package app.awanpro.tv.installer;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Update the installer (the guide's newer-installer card, About): the newest release's AwanTVInstaller.apk, downloaded
 * from GitHub only when pressed and streamed into an Android install session, as Awan TV's own Update now
 * (app/.../SelfUpdate.java). Android itself refuses an APK signed with another key, cut short, or older than this one,
 * so no checksum of our own is needed. A phone asks once for "Install unknown apps" for this app ({@link #allowed}),
 * then Android's own "Update this app?" each time.
 */
final class SelfUpdate {
    private SelfUpdate() {}

    static final int IDLE = 0, DOWNLOADING = 1, INSTALLING = 2, FAILED = 3;
    private static final String TAG = "AwanInstallerUpdate";
    private static final String DOWNLOAD = "https://github.com/OmarGHub/AwanTV/releases/download/v%s/AwanTVInstaller.apk";

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile int state = IDLE, percent;
    private static volatile long done, total;
    private static volatile boolean cancelled;
    private static String version;
    private static Runnable listener;
    /** Android's "Update this app?" for the session waiting on it, offered again if the phone did not show it. */
    private static volatile Intent confirm;

    static int state() { return state; }
    static int percent() { return percent; }
    static long done() { return done; }
    static long total() { return total; }
    static String version() { return version; }
    static boolean busy() { return state == DOWNLOADING || state == INSTALLING; }
    static boolean confirmWaiting() { return confirm != null && state == INSTALLING; }

    /** The screen's redraw while it shows the update; null when it doesn't. Called on the main thread. */
    static void listen(Runnable r) { listener = r; }

    private static void set(int s) {
        main.post(() -> {
            state = s;
            if (listener != null) listener.run();
        });
    }

    /** Whether this phone lets the installer install its update ("Install unknown apps" for this app). */
    static boolean allowed(Context c) {
        try {
            return c.getPackageManager().canRequestPackageInstalls();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void start(Context c, String v) {
        if (busy() || v == null) return;
        Context app = c.getApplicationContext();
        if (!allowed(app)) return;
        version = v;
        confirm = null;
        cancelled = false;
        percent = 0;
        done = 0;
        total = 0;
        state = DOWNLOADING;
        if (listener != null) listener.run();
        new Thread(() -> run(app, v), "installer-self-update").start();
    }

    /** A failure seen: Update is offered again. */
    static void reset() {
        if (state == FAILED) state = IDLE;
    }

    /** Cancel while downloading: the session is abandoned, nothing installed. */
    static void cancel() {
        if (state != DOWNLOADING) return;
        cancelled = true;
        state = IDLE;
        if (listener != null) listener.run();
    }

    /** Android's question again (Continue installing). */
    static void confirm(Context c) {
        Intent i = confirm;
        if (i == null) return;
        try {
            c.startActivity(new Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Log.i(TAG, "confirm: " + e);
        }
    }

    private static void run(Context c, String v) {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        int id = -1;
        try {
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(c.getPackageName());
            p.setInstallReason(PackageManager.INSTALL_REASON_USER);
            if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            id = pi.createSession(p);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                download(s, String.format(DOWNLOAD, v));
                if (cancelled) throw new IOException("cancelled");
                set(INSTALLING);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent result = PendingIntent.getBroadcast(c, id, new Intent(c, Result.class), flags);
                s.commit(result.getIntentSender());
            }
        } catch (IOException | RuntimeException e) {
            Log.i(TAG, "update " + v + ": " + e);
            if (id >= 0) {
                try {
                    pi.abandonSession(id);
                } catch (RuntimeException ignored) {
                    // Already gone.
                }
            }
            if (!cancelled) set(FAILED);
        }
    }

    private static void download(PackageInstaller.Session s, String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", "AwanTV-installer (https://awanpro.app)");
        try {
            if (c.getResponseCode() != 200) throw new IOException("GitHub answered " + c.getResponseCode());
            long all = c.getContentLengthLong(), got = 0;
            total = Math.max(0, all);
            int shown = -1;
            try (InputStream in = c.getInputStream(); OutputStream out = s.openWrite("AwanTVInstaller.apk", 0, all)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled) throw new IOException("cancelled");
                    out.write(buf, 0, n);
                    got += n;
                    done = got;
                    int pct = all > 0 ? (int) (got * 100 / all) : 0;
                    if (pct != shown) {
                        shown = pct;
                        main.post(() -> {
                            if (state != DOWNLOADING) return;
                            percent = pct;
                            if (listener != null) listener.run();
                        });
                    }
                }
                s.fsync(out);
            }
            if (all > 0 && got != all) throw new IOException("download cut short");
        } finally {
            c.disconnect();
        }
    }

    /** Android's answer to the install. On success this process is being replaced by the new installer. */
    public static final class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                    Intent ask = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (ask == null) break;
                    confirm = ask;
                    set(INSTALLING);
                    try {
                        c.startActivity(new Intent(ask).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException ignored) {
                        // Offered again on the card (Continue installing).
                    }
                    return;
                }
                case PackageInstaller.STATUS_SUCCESS:
                    confirm = null;
                    return;
                case PackageInstaller.STATUS_FAILURE_ABORTED:
                    // Cancelled in Android's window: Update is offered again.
                    confirm = null;
                    set(IDLE);
                    return;
                default:
                    break;
            }
            Log.i(TAG, "install refused: " + status + " " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            confirm = null;
            set(FAILED);
        }
    }
}
