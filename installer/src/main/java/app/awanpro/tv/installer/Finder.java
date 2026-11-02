package app.awanpro.tv.installer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Finds TVs with ADB on the same Wi-Fi, three ways at once:
 * <ul>
 * <li>mDNS: {@code _adb._tcp} (classic network debugging), {@code _adb-tls-connect._tcp} (Wireless debugging, on a
 * random port) and {@code _adb-tls-pairing._tcp} (shown while the TV's "Pair with code" dialog is open).</li>
 * <li>A sweep of the phone's /24 for port 5555, since many TVs run classic ADB without announcing it.</li>
 * <li>mDNS {@code _googlecast._tcp} for the TV's own name and model (TXT {@code fn} and {@code md}, the name shown
 * when casting), so a TV is told apart by more than its address. It opens no door: a speaker that casts is not
 * listed.</li>
 * <li>{@link #scanPorts} for a TV typed in by hand: every port where Wireless debugging may be listening.</li>
 * </ul>
 * Library note: libadb's own AdbMdns only accepts services on this phone, so discovery is done here.
 */
final class Finder {
    static final int CLASSIC_PORT = 5555;

    /** One TV and the ADB doors it has open; 0 means not seen. */
    static final class Tv {
        final String host;
        /** The TV's own name and model from Google Cast, or null when it announces none. */
        String name, model;
        int classic, tls, pairing;
        Tv(String host) { this.host = host; }
    }

    interface Listener { void onChanged(List<Tv> tvs); }

    private static final String[] TYPES = {"_adb._tcp", "_adb-tls-connect._tcp", "_adb-tls-pairing._tcp",
            "_googlecast._tcp"};

    private final Context context;
    private final Listener listener;
    private final NsdManager nsd;
    private final Map<String, Tv> tvs = new LinkedHashMap<>();
    private final List<NsdManager.DiscoveryListener> discoveries = new ArrayList<>();
    /** Before Android 14 NsdManager resolves one service at a time, so resolves wait their turn. */
    private final ArrayDeque<NsdServiceInfo> toResolve = new ArrayDeque<>();
    private boolean resolving;
    private ExecutorService sweep;
    private WifiManager.MulticastLock multicast;

    Finder(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.nsd = (NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
    }

    synchronized void start() {
        stop();
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            multicast = wifi.createMulticastLock("awantv-installer");
            multicast.setReferenceCounted(false);
            multicast.acquire();
        }
        for (String type : TYPES) {
            NsdManager.DiscoveryListener d = new Discovery();
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, d);
                discoveries.add(d);
            } catch (RuntimeException ignored) {
                // Discovery can refuse to start (no Wi-Fi); the sweep still runs.
            }
        }
        sweepClassic();
    }

    synchronized void stop() {
        for (NsdManager.DiscoveryListener d : discoveries) {
            try {
                nsd.stopServiceDiscovery(d);
            } catch (RuntimeException ignored) {
                // Already stopped.
            }
        }
        discoveries.clear();
        toResolve.clear();
        resolving = false;
        if (sweep != null) sweep.shutdownNow();
        sweep = null;
        if (multicast != null && multicast.isHeld()) multicast.release();
        multicast = null;
    }

    synchronized Tv get(String host) {
        return tvs.get(host);
    }

    private synchronized Tv tv(String host) {
        Tv t = tvs.get(host);
        if (t == null) tvs.put(host, t = new Tv(host));
        return t;
    }

    private void changed() {
        List<Tv> copy;
        synchronized (this) { copy = new ArrayList<>(tvs.values()); }
        listener.onChanged(copy);
    }

    // mDNS

    private final class Discovery implements NsdManager.DiscoveryListener {
        @Override public void onServiceFound(NsdServiceInfo info) { resolve(info); }
        @Override public void onServiceLost(NsdServiceInfo info) {}
        @Override public void onDiscoveryStarted(String type) {}
        @Override public void onDiscoveryStopped(String type) {}
        @Override public void onStartDiscoveryFailed(String type, int error) {}
        @Override public void onStopDiscoveryFailed(String type, int error) {}
    }

    private synchronized void resolve(NsdServiceInfo info) {
        toResolve.add(info);
        if (!resolving) next();
    }

    private synchronized void next() {
        NsdServiceInfo info = toResolve.poll();
        resolving = info != null;
        if (info == null) return;
        try {
            nsd.resolveService(info, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo i, int error) { next(); }
                @Override public void onServiceResolved(NsdServiceInfo i) {
                    found(i);
                    next();
                }
            });
        } catch (RuntimeException e) {
            resolving = false;
        }
    }

    @SuppressWarnings("deprecation") // getHost(): the replacement needs Android 14.
    private void found(NsdServiceInfo i) {
        InetAddress a = i.getHost();
        if (!(a instanceof Inet4Address) || ownAddresses().contains(a.getHostAddress())) return;
        String type = i.getServiceType();
        synchronized (this) {
            Tv t = tv(a.getHostAddress());
            if (type.contains("googlecast")) {
                t.name = txt(i, "fn");
                t.model = txt(i, "md");
            } else if (type.contains("adb-tls-connect")) t.tls = i.getPort();
            else if (type.contains("adb-tls-pairing")) t.pairing = i.getPort();
            else t.classic = i.getPort();
        }
        changed();
    }

    /** One TXT value as text, or null. */
    private static String txt(NsdServiceInfo i, String key) {
        byte[] v = i.getAttributes().get(key);
        if (v == null) return null;
        String s = new String(v, java.nio.charset.StandardCharsets.UTF_8).trim();
        return s.isEmpty() ? null : s;
    }

    // Port 5555 across the /24

    private synchronized void sweepClassic() {
        String self = phoneAddress(context);
        if (self == null) return;
        String prefix = self.substring(0, self.lastIndexOf('.') + 1);
        java.util.Set<String> mine = ownAddresses();
        sweep = Executors.newFixedThreadPool(48);
        for (int i = 1; i < 255; i++) {
            String host = prefix + i;
            // The phone's own ADB (Wireless debugging on the phone) is not a TV.
            if (mine.contains(host)) continue;
            sweep.execute(() -> {
                if (open(host, CLASSIC_PORT, 500)) {
                    synchronized (Finder.this) { tv(host).classic = CLASSIC_PORT; }
                    changed();
                }
            });
        }
        sweep.shutdown();
    }

    // A TV typed in by hand

    /**
     * Open ports on one host where ADB may listen: 5555, then the range Wireless debugging picks its random port
     * from. Blocking; takes about 15 s.
     */
    static List<Integer> scanPorts(String host) throws InterruptedException {
        List<Integer> open = new ArrayList<>();
        if (open(host, CLASSIC_PORT, 800)) open.add(CLASSIC_PORT);
        ExecutorService pool = Executors.newFixedThreadPool(256);
        for (int p = 30000; p <= 49999; p++) {
            final int port = p;
            pool.execute(() -> {
                if (open(host, port, 300)) synchronized (open) { open.add(port); }
            });
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.MINUTES);
        return open;
    }

    static boolean open(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static java.util.Set<String> ownAddresses() {
        java.util.Set<String> out = new java.util.HashSet<>();
        try {
            for (java.net.NetworkInterface n : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : java.util.Collections.list(n.getInetAddresses())) out.add(a.getHostAddress());
            }
        } catch (Exception ignored) {
            // Nothing to exclude.
        }
        return out;
    }

    /** The phone's IPv4 address on Wi-Fi, or null when it isn't on one. */
    static String phoneAddress(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        if (lp == null) return null;
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (la.getAddress() instanceof Inet4Address && !la.getAddress().isLoopbackAddress()) {
                return la.getAddress().getHostAddress();
            }
        }
        return null;
    }
}
