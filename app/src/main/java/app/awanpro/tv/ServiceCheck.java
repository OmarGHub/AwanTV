package app.awanpro.tv;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/**
 * The remote service (RemoteKeys: Home over the TV's own home screen, the adhan over any app) off when the viewer
 * did not turn it off. Where Awan TV holds WRITE_SECURE_SETTINGS (granted over adb at install), Home, the screensaver
 * and an update of the app put it right, at most once in two minutes:
 * <ul>
 * <li>Listed in enabled_accessibility_services but never bound by the system: seen on a TCL BeyondTV on Android
 * TV 11 after the install, and after a force-stop while bound (Android marks it crashed). Home went to the stock
 * launcher and stayed there until the list itself changed. It leaves the list and comes back a second later
 * (Android reads the list when it hears of a change; written back at once, it may only see the last write).</li>
 * <li>Missing from the list: Android drops a force-stopped app's service from it, and TV cleaners force-stop apps
 * (Afnon found the adhan off so on a Xiaomi MiTV and a TCL). It is listed again, and looked at once more 3 s later.</li>
 * </ul>
 * Never when the viewer turned it off in the TV's settings: {@link RemoteKeys#onUnbind} notes that in {@link #ON}
 * (a force-stop unbinds nothing), and connecting again clears it. As Afnon 0.7 and 0.8 do.
 */
final class ServiceCheck {
    private ServiceCheck() {}

    /** Pref, this TV's own: false after the viewer turned the service off in the TV's settings. */
    static final String ON = "service_on";

    private static final String KEY = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
    private static final long WAIT_MS = 8_000, EVERY_MS = 120_000, BACK_MS = 1_000, AGAIN_MS = 3_000;
    private static long lastFix;

    /** Checks after a few seconds (the system may still be binding it, after a boot or an update). */
    static void soon(Context c, String why) {
        Context app = c.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> now(app, why), WAIT_MS);
    }

    /** True when the service had to be put right (off, but not by the viewer). */
    static boolean now(Context c, String why) {
        return fix(c.getApplicationContext(), why, true);
    }

    /** again: after listing a missing service, look once more a little later (it may be marked crashed meanwhile). */
    private static boolean fix(Context c, String why, boolean again) {
        if (RemoteKeys.guard != null || !Saver.canChange(c)) return false;
        if (!Prefs.of(c).getBoolean(ON, true)) return false;
        long t = System.currentTimeMillis();
        if (again && t - lastFix < EVERY_MS) return false;
        ComponentName me = new ComponentName(c, RemoteKeys.class);
        String list;
        try {
            list = Settings.Secure.getString(c.getContentResolver(), KEY);
        } catch (RuntimeException e) {
            return false;
        }
        lastFix = t;
        Handler main = new Handler(Looper.getMainLooper());
        try {
            if (!listed(list, me)) {
                Log.i(HomeGuard.TAG, "service missing from the list (" + why + "), not turned off by the viewer: listing it again");
                put(c, with(list, me));
                if (again) main.postDelayed(() -> fix(c, why + ", again", false), AGAIN_MS);
                return true;
            }
            Log.i(HomeGuard.TAG, "service on but not running (" + why + "): writing the list again");
            String rest = without(list, me);
            Settings.Secure.putString(c.getContentResolver(), KEY, rest.isEmpty() ? null : rest);
            main.postDelayed(() -> {
                try {
                    // Read again: another service may have been turned on or off in that second.
                    put(c, with(Settings.Secure.getString(c.getContentResolver(), KEY), me));
                    // Out of the list for that second, it may have reached onUnbind, which takes it as turned off.
                    Prefs.of(c).edit().putBoolean(ON, true).apply();
                } catch (RuntimeException e) {
                    Log.w(HomeGuard.TAG, "could not write the service list", e);
                }
            }, BACK_MS);
            return true;
        } catch (RuntimeException e) {
            Log.w(HomeGuard.TAG, "could not write the service list", e);
            return false;
        }
    }

    /** Within a minute of this app's last install or update (for RemoteKeys.onUnbind). */
    static boolean justUpdated(Context c) {
        try {
            long at = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime;
            return System.currentTimeMillis() - at < 60_000;
        } catch (Exception e) {
            return false;
        }
    }

    /** Whether this TV's enabled accessibility services include Awan TV's (for RemoteKeys.onUnbind). */
    static boolean listed(Context c) {
        try {
            return listed(Settings.Secure.getString(c.getContentResolver(), KEY), new ComponentName(c, RemoteKeys.class));
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void put(Context c, String list) {
        Settings.Secure.putString(c.getContentResolver(), KEY, list);
        Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
    }

    /** An entry is this service in its full or short form ("pkg/.RemoteKeys"). */
    private static boolean listed(String list, ComponentName me) {
        if (list == null) return false;
        for (String part : list.split(":")) if (me.equals(ComponentName.unflattenFromString(part.trim()))) return true;
        return false;
    }

    private static String without(String list, ComponentName me) {
        StringBuilder out = new StringBuilder();
        if (list == null) return "";
        for (String part : list.split(":")) {
            String p = part.trim();
            if (p.isEmpty() || me.equals(ComponentName.unflattenFromString(p))) continue;
            if (out.length() > 0) out.append(':');
            out.append(p);
        }
        return out.toString();
    }

    private static String with(String list, ComponentName me) {
        String rest = without(list, me);
        return rest.isEmpty() ? me.flattenToString() : rest + ":" + me.flattenToString();
    }

    /** After an update of the app (adb install -r, or the Play Store one day): the system should bind it again itself. */
    public static final class Updated extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) return;
            PendingResult done = goAsync();
            Context app = c.getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    now(app, "update");
                } finally {
                    done.finish();
                }
            }, WAIT_MS);
        }
    }
}
