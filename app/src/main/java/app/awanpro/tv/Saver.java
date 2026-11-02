package app.awanpro.tv;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.Log;

/**
 * Which screensaver the TV runs, and putting back the one Awan TV replaced.
 *
 * <p>The TV's screensaver is the secure setting {@code screensaver_components}. Changing it needs
 * WRITE_SECURE_SETTINGS, which only adb can grant (tools/AwanTV-install.ps1 does); Google TV has no screen to pick
 * a screensaver at all. So:
 * <ul>
 * <li>With the grant, the Run setting moves the TV's screensaver to Awan TV's and back, and "Remove Awan TV" puts
 * the old one back before the uninstall. The one replaced is kept here ({@code saver_before}), handed over by the
 * install tool or read the first time the app sees it.</li>
 * <li>Without it, Awan TV opens its own screensaver after some minutes on Home ({@link MainActivity}), and leaves
 * the TV's alone.</li>
 * </ul>
 * Uninstalled from the TV's own settings instead, the TV goes back to its default screensaver by itself: Android
 * drops a screensaver whose app is gone.
 */
final class Saver {
    private Saver() {}

    private static final String KEY = "screensaver_components";

    /** Awan TV's screensaver, as the setting holds it. */
    static String ours(Context c) {
        return new ComponentName(c, AwanDream.class).flattenToString();
    }

    /** The TV's screensaver setting, or null when it cannot be read. */
    static String current(Context c) {
        try {
            return Settings.Secure.getString(c.getContentResolver(), KEY);
        } catch (RuntimeException notReadable) {
            return null;
        }
    }

    /** True when the TV's own screensaver is Awan TV's. False when it is another, or cannot be read. */
    static boolean isOurs(Context c) {
        String now = current(c);
        if (now == null) return false;
        ComponentName me = new ComponentName(c, AwanDream.class);
        for (String part : now.split(",")) {
            if (me.equals(ComponentName.unflattenFromString(part.trim()))) return true;
        }
        return false;
    }

    /** True when Awan TV may change the TV's screensaver (granted over adb at install). */
    static boolean canChange(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Keeps the TV's screensaver to put back later, when it is not Awan TV's. Called whenever the app looks; the
     * install tool also hands it over ({@link #remember(Context, String)}) since it switches before the app runs.
     */
    static void remember(Context c) {
        remember(c, current(c));
    }

    static void remember(Context c, String component) {
        if (component == null) return;
        component = component.trim();
        if (component.isEmpty() || "null".equals(component) || component.contains(c.getPackageName() + "/")) return;
        // Only a screensaver installed on this TV (Home's saver_before extra could come from any app).
        if (!isDream(c, component)) return;
        Prefs.of(c).edit().putString("saver_before", component).apply();
    }

    /** True when every component in the setting's value is an installed screensaver. */
    private static boolean isDream(Context c, String value) {
        java.util.List<android.content.pm.ResolveInfo> dreams = c.getPackageManager().queryIntentServices(
            new android.content.Intent(android.service.dreams.DreamService.SERVICE_INTERFACE), PackageManager.GET_META_DATA);
        for (String part : value.split(",")) {
            ComponentName want = ComponentName.unflattenFromString(part.trim());
            if (want == null) return false;
            boolean found = false;
            for (android.content.pm.ResolveInfo r : dreams) {
                if (r.serviceInfo != null && want.equals(new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name))) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /** The screensaver to go back to: the one Awan TV replaced, else the TV's default; null if neither is known. */
    static String before(Context c) {
        String saved = Prefs.of(c).getString("saver_before", null);
        if (saved != null && !saved.isEmpty()) return saved;
        try {
            int id = Resources.getSystem().getIdentifier("config_dreamsDefaultComponent", "string", "android");
            String def = id == 0 ? null : Resources.getSystem().getString(id);
            return def == null || def.isEmpty() ? null : def;
        } catch (RuntimeException none) {
            return null;
        }
    }

    /**
     * Puts the TV's screensaver in step with the Run setting: Awan TV's while its screensaver is on, the one it
     * replaced while it is off. Does nothing without the grant.
     */
    static void apply(Context c) {
        if (Prefs.saver(c)) take(c);
        else giveBack(c);
    }

    /** Awan TV's screensaver as the TV's. */
    private static void take(Context c) {
        if (!canChange(c) || isOurs(c)) return;
        remember(c);
        put(c, ours(c));
    }

    /** The screensaver Awan TV replaced, back as the TV's (only if Awan TV's is the TV's now). */
    static void giveBack(Context c) {
        if (!canChange(c) || !isOurs(c)) return;
        put(c, before(c));
    }

    private static void put(Context c, String component) {
        try {
            if (component == null) Settings.Secure.putString(c.getContentResolver(), KEY, null);
            else Settings.Secure.putString(c.getContentResolver(), KEY, component);
            Log.i(HomeGuard.TAG, "screensaver: " + component);
        } catch (RuntimeException refused) {
            Log.w(HomeGuard.TAG, "screensaver not changed", refused);
        }
    }

    /**
     * Before the uninstall: the screensaver back, the radio off, nothing left behind. Android removes the
     * accessibility service itself with the app.
     */
    static void leave(Context c) {
        giveBack(c);
        Library.stopAll(c);
        Radio.stop();
        Radio.restoreSleep(c);
    }
}
