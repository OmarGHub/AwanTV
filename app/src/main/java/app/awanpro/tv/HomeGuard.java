package app.awanpro.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Home over the TV's own home screen, without disabling anything (the approach of Masjid TV,
 * github.com/obaid/masjid-tv-tcl, MIT). Many TVs, Google TV above all, keep their own launcher as Home whatever is
 * installed, and disabling it costs features (on Google TV, casting from phones). So the stock launcher stays, and
 * whenever its home activity comes to the front this opens Home over it.
 *
 * <p>Hosted by {@link RemoteKeys}, from its window events: no timer, and no key filtering except Home's own button
 * on an HDMI source box (below). It is generic: the stock
 * launcher is whichever app answers HOME on this TV (Google TV's launcherx, Android TV's tvlauncher, a maker's own),
 * and only its HOME activities trigger it, so its other screens (a settings panel, a full-screen player) are left
 * alone. Nothing to do when Awan TV itself is the TV's Home.
 *
 * <p>On an HDMI source box (a Google TV Streamer, a Chromecast, a set-top box) Android itself sends
 * HDMI-CEC One Touch Play on every short Home press (Text View On, Active Source, and a System Audio Mode Request to
 * the soundbar), and no setting stops it (system_audio_control and volume_control_enabled were both tried). A soundbar
 * on the TV's ARC went silent on it every time, until its volume was pressed on the
 * TV's remote. So there the service can take the Home button itself, open Home directly, and Android never sees the
 * press: no One Touch Play, and no flash of the stock home either. Held, it opens the TV's own home screen. Only
 * when the viewer turns it on (Settings > Device settings, shown on source boxes only), since it also ends Android's
 * switching the TV to the box's input on Home. On a TV (TCL, BeyondTV) nothing changes: no key filtering, as before.
 *
 * <p>Diagnostics: {@code adb logcat -s AwanGuard}.
 */
final class HomeGuard {
    static final String TAG = "AwanGuard";
    /** Packages that answer HOME but are not a home screen to cover: setup wizards, settings fallbacks, factory tools. */
    private static final String[] NOT_A_LAUNCHER = {"setupwraith", "setupwizard", "tvsetup", "provision", "factory",
        "com.android.settings", "com.android.tv.settings"};
    /** Shorter (was 1.5 s), now that a stock home skipped inside it is checked again when it ends. */
    private static final long DEBOUNCE_MS = 700;

    /**
     * Set when the viewer asked for the TV's own home screen (Settings): it stays until they open some other app,
     * and the next Home press after that comes back to Awan TV.
     */
    static volatile boolean bypass;

    private final AccessibilityService service;
    private String stockPackage;
    private final Set<String> homeClasses = new HashSet<>();
    private long lastRedirect;
    /**
     * Quick Home presses. Logs from a TCL and a Google TV Streamer showed that window events can't
     * tell what is in front then: Awan TV's own comes late (after a start Android then cancelled), and a Home press
     * on the stock home already in front sends none. So after the stock home comes up, and after each time Awan TV is
     * opened over it or goes to the back, it looks again a moment later and trusts only Home's own state
     * (MainActivity.shown): if Home is not in front, the stock home was the last app to come up, and that was in the
     * last five seconds, Home opens again. Another app opened in between (YouTube from Home) ends it.
     */
    private long stockAt, otherAt;
    private static final long SETTLE_MS = 1200, BURST_MS = 5000;
    private final Handler later = new Handler(Looper.getMainLooper());
    private final Runnable settle = () -> {
        if (bypass || !active() || MainActivity.shown) return;
        long now = SystemClock.uptimeMillis();
        if (stockAt == 0 || otherAt > stockAt || MainActivity.launchedAt > stockAt || now - stockAt > BURST_MS) return;
        lastRedirect = now;
        Log.i(TAG, "stock home still in front after quick Home presses: opening Awan TV");
        open();
    };

    private void settleIn(long ms) {
        later.removeCallbacks(settle);
        later.postDelayed(settle, ms);
    }

    /** Called by MainActivity.onStop: Home went to the back. */
    void homeStopped() {
        settleIn(900);
    }

    HomeGuard(AccessibilityService service) {
        this.service = service;
        refresh();
    }

    /** True when the TV has its own home screen beside Awan TV (Awan TV is not its Home). */
    boolean hasStock() {
        return stockPackage != null;
    }

    /** True when there is a stock home screen to cover and the setting is on. */
    boolean active() {
        return stockPackage != null && Prefs.guard(service);
    }

    /** Re-reads which app is the TV's Home (an app installed or removed, the viewer picking a default). */
    void refresh() {
        stockPackage = stockHome(service, homeClasses);
        Log.i(TAG, "stock home: " + stockPackage + " " + homeClasses + " active=" + active());
        // Home's button follows (the run mode changed, or a new default Home).
        if (service instanceof RemoteKeys && RemoteKeys.guard == this) ((RemoteKeys) service).keysChanged();
    }

    void onEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = e.getPackageName(), c = e.getClassName();
        if (p == null) return;
        String pkg = p.toString();
        // Another app's screensaver (Google TV's own) is up: Home goes to Android then, which ends it.
        if (!pkg.equals(service.getPackageName())) {
            String cls = c == null ? "" : c.toString();
            if (cls.startsWith("android.service.dreams") || pkg.contains(".dream")) {
                otherDream = true;
                return;
            }
            otherDream = false;
        }
        if (pkg.equals(service.getPackageName())) {
            // Back on Awan TV's own screens: the next Home press is ours again, and the debounce starts over so a
            // quick second Home press is not lost. The screensaver and the call to prayer don't count: they come
            // over whatever was on, the TV's own home screen included.
            String cls = c == null ? "" : c.toString();
            if (!cls.equals(NowActivity.class.getName()) && !cls.equals(ScreenOffActivity.class.getName())
                    && !cls.startsWith("android.service.dreams")) {
                bypass = false;
                lastRedirect = 0;
            }
            return;
        }
        if (!pkg.equals(stockPackage)) {
            // Another app came to the front: the bypass ends with it. Panels, overlays, the keyboard and
            // screensavers are not a choice of app, so only a real activity of a real app ends it.
            boolean app = c != null && launcher(pkg) && !pkg.equals("com.android.systemui") && isActivity(pkg, c.toString());
            if (bypass && app) bypass = false;
            if (app) {
                otherAt = SystemClock.uptimeMillis();
                later.removeCallbacks(settle);
            }
            return;
        }
        // Any window of the stock launcher keeps it in front: moving with the arrows on its home screen
        // in the moment before Awan TV came back raised one of its other windows, which cancelled the check, and the
        // stock home stayed for good. Only Awan TV or another app coming up ends it.
        if (c == null || !homeClasses.contains(c.toString())) return;
        stockAt = SystemClock.uptimeMillis();
        if (bypass || !active()) return;
        long now = stockAt;
        if (now - lastRedirect < DEBOUNCE_MS) {
            // Skipped for now, not forgotten: looked at again when the debounce ends.
            settleIn(DEBOUNCE_MS - (now - lastRedirect) + 100);
            return;
        }
        lastRedirect = now;
        Log.i(TAG, "stock home came up (" + c + "): opening Awan TV");
        open();
    }

    // ---------------------------------------------------------------- The Home button itself

    /** Is this an HDMI source box (playback device)? Read once from the system properties. */
    private static Boolean hdmiSource;
    /** The press now held is ours (decided on its press, so its release goes the same way); held long enough. */
    private boolean takingPress, longDone;
    /** Another app's screensaver is in front (Google TV's own): Android handles Home then. */
    private boolean otherDream;
    private final Runnable longPress = this::homeHeld;

    private void homeHeld() {
        longDone = true;
        Log.i(TAG, "Home held: the TV's own home screen");
        openStock(service);
    }

    /**
     * True when the service takes the Home button itself: an HDMI source box, Home over its stock home on, and the
     * viewer chose it (Settings > Device settings, off by default: Android's own Home).
     */
    boolean takesHome() {
        return active() && hdmiSource() && Prefs.homeButton(service);
    }

    /** From RemoteKeys.onKeyEvent beside a stock launcher: true for a Home press taken here, false lets it through. */
    boolean onHomeKey(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        int action = e.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() > 0) return takingPress;
            takingPress = takesHome() && !otherDream;
            if (!takingPress) return false;
            longDone = false;
            later.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
            return true;
        }
        if (action == KeyEvent.ACTION_UP) {
            boolean took = takingPress;
            takingPress = false;
            later.removeCallbacks(longPress);
            if (took && !longDone && !e.isCanceled()) homePressed();
            return took;
        }
        return takingPress;
    }

    /** A short Home press taken here: what Android's own Home would do, minus the HDMI-CEC One Touch Play. */
    private void homePressed() {
        AwanDream dream = AwanDream.current;
        if (dream != null) {
            // As Android does: Home ends the screensaver and goes back to what was on.
            Log.i(TAG, "Home on the screensaver: waking");
            dream.wakeUp();
            return;
        }
        if (bypass) {
            Log.i(TAG, "Home, the TV's own home chosen: opening it");
            openStock(service);
            return;
        }
        Log.i(TAG, "Home pressed: opening Awan TV directly (no HDMI-CEC One Touch Play)");
        lastRedirect = SystemClock.uptimeMillis();
        open();
    }

    /** True on an HDMI source box: ro.hdmi.cec_device_types lists playback, or ro.hdmi.device_type has 4. */
    static boolean hdmiSource() {
        if (hdmiSource == null) {
            String types = prop("ro.hdmi.cec_device_types"), type = prop("ro.hdmi.device_type");
            boolean source = types.contains("playback");
            for (String t : type.split(",")) if (t.trim().equals("4")) source = true;
            hdmiSource = source;
            Log.i(TAG, "HDMI-CEC device types: '" + types + "' '" + type + "', source box: " + source);
        }
        return hdmiSource;
    }

    /** A system property, "" when unreadable: android.os.SystemProperties, else the getprop command. */
    private static String prop(String key) {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
            if (v instanceof String && !((String) v).isEmpty()) return (String) v;
        } catch (Throwable ignored) {
            // Hidden on this build: ask getprop.
        }
        try {
            Process proc = new ProcessBuilder("getprop", key).redirectErrorStream(true).start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line = r.readLine();
                return line == null ? "" : line.trim();
            } finally {
                proc.destroy();
            }
        } catch (Throwable e) {
            return "";
        }
    }

    private void open() {
        // A start Android cancels (the next Home press came first) is caught by looking again.
        settleIn(SETTLE_MS);
        try {
            service.startActivity(new Intent(service, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (RuntimeException e) {
            Log.w(TAG, "could not open Home", e);
        }
    }

    /** Opens the TV's own home screen and leaves it up until the viewer opens another app. False if there is none. */
    static boolean openStock(Context c) {
        String pkg = stockHome(c, new HashSet<>());
        if (pkg == null) return false;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        bypass = true;
        try {
            c.startActivity(home);
            return true;
        } catch (RuntimeException e) {
            bypass = false;
            return false;
        }
    }

    /**
     * The package that is the TV's own home screen, filling {@code classes} with its HOME activities (aliases
     * and their targets: window events name the target). The TV's default Home when it is another app; with no
     * default (Android's chooser) the highest-priority HOME app. Null when Awan TV is the default Home or none fits.
     */
    static String stockHome(Context c, Set<String> classes) {
        classes.clear();
        PackageManager pm = c.getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        String own = c.getPackageName();
        String pkg = null;
        ResolveInfo def = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null) {
            String d = def.activityInfo.packageName;
            if (d.equals(own)) return null;
            if (!d.equals("android") && launcher(d)) pkg = d;
        }
        List<ResolveInfo> all = pm.queryIntentActivities(home, 0);
        if (pkg == null) {
            int best = Integer.MIN_VALUE;
            for (ResolveInfo r : all) {
                if (r.activityInfo == null) continue;
                String p = r.activityInfo.packageName;
                if (p.equals(own) || !launcher(p)) continue;
                if (r.priority > best) {
                    best = r.priority;
                    pkg = p;
                }
            }
        }
        if (pkg == null) return null;
        for (ResolveInfo r : all) {
            ActivityInfo a = r.activityInfo;
            if (a == null || !a.packageName.equals(pkg)) continue;
            classes.add(a.name);
            if (a.targetActivity != null) classes.add(a.targetActivity);
        }
        return pkg;
    }

    /** True for an app's activities only, so its dialogs, panels and toasts don't count (as in Redirects). */
    private boolean isActivity(String pkg, String cls) {
        try {
            service.getPackageManager().getActivityInfo(new android.content.ComponentName(pkg, cls), 0);
            return true;
        } catch (PackageManager.NameNotFoundException notAnActivity) {
            return false;
        }
    }

    private static boolean launcher(String pkg) {
        for (String n : NOT_A_LAUNCHER) if (pkg.contains(n)) return false;
        return true;
    }
}
