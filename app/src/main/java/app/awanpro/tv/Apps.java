package app.awanpro.tv;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The apps Afnon shows: which exist, their names and icons, launching, and the saved favourites order. */
final class Apps {
    private Apps() {}

    // Six square favourites on Home.
    static final int FAVOURITE_SLOTS = 6;
    /** Home's More row, six too, arranged in All apps like the favourites. */
    static final int MORE_SLOTS = 6;

    private static final String[] DEFAULT_FAVOURITES = {
        "com.google.android.youtube.tv", "com.netflix.ninja", "net.mbc.shahidTV", "com.tcl.browser"};
    /** Home's "More" row picks these first (minus favourites), then fills from the rest. */
    static final String[] MORE_FIRST = {
        "com.google.android.youtube.tvmusic", "com.android.vending", "com.tcl.ui_mediaCenter",
        "com.alphainventor.filemanager"};
    /**
     * Never listed: this launcher, the previous one, the TV's own input/Live TV plumbing, and TCL's remote pairing
     * service, whose launcher entry is only a test screen (Afnon 0.8).
     */
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
        "tv.afnon.home", "app.awanpro.tv", "com.gathin.tvhome", "com.tcl.suspension", "com.tcl.tv",
        "com.tcl.tcl_bt_rcu_service"));

    private static final Map<String, String> AR_NAMES = new HashMap<>();
    private static final Map<String, String> EN_NAMES = new HashMap<>();
    static {
        AR_NAMES.put("com.google.android.youtube.tv", "يوتيوب");
        AR_NAMES.put("com.netflix.ninja", "نتفليكس");
        AR_NAMES.put("net.mbc.shahidTV", "شاهد");
        AR_NAMES.put("com.google.android.youtube.tvmusic", "يوتيوب ميوزك");
        AR_NAMES.put("com.android.vending", "متجر Play");
        AR_NAMES.put("com.tcl.ui_mediaCenter", "مشغل الوسائط");
        AR_NAMES.put("com.android.tv.settings", "الإعدادات");
        AR_NAMES.put("com.google.android.play.games", "ألعاب Google Play");
        EN_NAMES.put("com.google.android.youtube.tv", "YouTube");
        EN_NAMES.put("com.netflix.ninja", "Netflix");
        EN_NAMES.put("net.mbc.shahidTV", "Shahid");
        EN_NAMES.put("com.android.vending", "Play Store");
    }

    private static List<String> cached;
    private static long cachedAt;

    /**
     * Every app that has a TV or a phone-style launcher entry, one per package, not hidden. Cached for two
     * seconds: one screen build asks several times, and each query is slow on this TV.
     */
    static synchronized List<String> installed(Context c) {
        long now = android.os.SystemClock.uptimeMillis();
        if (cached != null && now - cachedAt < 2000) return new ArrayList<>(cached);
        PackageManager pm = c.getPackageManager();
        Set<String> found = new LinkedHashSet<>();
        for (ResolveInfo r : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            String p = r.activityInfo.packageName;
            if (!HIDDEN.contains(p)) found.add(p);
        }
        // A phone-style entry alone does not count for a preinstalled, never-updated system app, unless Awan TV
        // knows it by name or the viewer put it on Home (favourites, the More row): on a Xiaomi MiTV those were
        // Clock, MediaTek's Netflix watcher and a vendor disclaimer screen (Afnon 0.7). Sideloaded phone apps count.
        Set<String> known = null;
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            String p = r.activityInfo.packageName;
            if (HIDDEN.contains(p) || found.contains(p)) continue;
            int flags = r.activityInfo.applicationInfo.flags;
            boolean stock = (flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                && (flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (stock) {
                if (known == null) known = known(c);
                if (!known.contains(p)) continue;
            }
            found.add(p);
        }
        cached = new ArrayList<>(found);
        cachedAt = now;
        return new ArrayList<>(cached);
    }

    /** Packages Awan TV names or suggests, and those the viewer put on Home: always listed when installed. */
    private static Set<String> known(Context c) {
        Set<String> out = new HashSet<>(Arrays.asList(DEFAULT_FAVOURITES));
        out.addAll(Arrays.asList(MORE_FIRST));
        out.addAll(AR_NAMES.keySet());
        out.addAll(EN_NAMES.keySet());
        out.addAll(favourites(c));
        out.addAll(moreSaved(c));
        return out;
    }

    /**
     * Favourites first, in their saved order; then the saved More, in its order; then the rest by
     * name in the screen's language.
     */
    static List<String> sorted(Context c, Text t) {
        List<String> all = installed(c);
        List<String> favs = new ArrayList<>(favourites(c));
        for (String p : moreSaved(c)) if (!favs.contains(p)) favs.add(p);
        List<String> rest = new ArrayList<>();
        for (String p : all) if (!favs.contains(p)) rest.add(p);
        Collator col = collator(t);
        Map<String, String> names = new HashMap<>();
        for (String p : rest) names.put(p, name(c, t, p));
        rest.sort((a, b) -> col.compare(names.get(a), names.get(b)));
        List<String> out = new ArrayList<>();
        for (String p : favs) if (all.contains(p)) out.add(p);
        out.addAll(rest);
        return out;
    }

    /** Home's "More" row: the preferred picks that are installed and not favourites, filled up from the rest. */
    static List<String> more(Context c, Text t, int count) {
        // Not any app Home shows as a favourite, the ones that topped the saved favourites up included.
        List<String> favs = slots(c, t);
        List<String> all = installed(c);
        List<String> out = new ArrayList<>();
        // The viewer's own More first (All apps > hold OK), in its order.
        for (String p : moreSaved(c)) if (out.size() < count && all.contains(p) && !favs.contains(p) && !out.contains(p)) out.add(p);
        for (String p : MORE_FIRST) if (out.size() < count && all.contains(p) && !favs.contains(p) && !out.contains(p)) out.add(p);
        for (String p : sorted(c, t)) if (out.size() < count && !favs.contains(p) && !out.contains(p)) out.add(p);
        return out;
    }

    // ---------------------------------------------------------------- favourites

    private static SharedPreferences prefs(Context c) {
        return Prefs.of(c);
    }

    /** At most {@link #FAVOURITE_SLOTS}, in Home order. Uninstalled ones are kept, so a reinstall brings them back. */
    static List<String> favourites(Context c) {
        String s = prefs(c).getString("favourites", null);
        if (s == null) return new ArrayList<>(Arrays.asList(DEFAULT_FAVOURITES));
        List<String> out = new ArrayList<>();
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    /** Always six for Home: the saved favourites that are installed, topped up from the rest if some went away. */
    static List<String> slots(Context c, Text t) {
        List<String> installed = installed(c);
        List<String> out = new ArrayList<>();
        for (String p : favourites(c)) if (installed.contains(p) && out.size() < FAVOURITE_SLOTS) out.add(p);
        for (String p : sorted(c, t)) if (out.size() < FAVOURITE_SLOTS && !out.contains(p)) out.add(p);
        return out;
    }

    static void saveFavourites(Context c, List<String> favs) {
        prefs(c).edit().putString("favourites", TextUtils.join(",", favs)).apply();
    }

    /** The More row as the viewer arranged it (empty until then). */
    static List<String> moreSaved(Context c) {
        String s = prefs(c).getString("more", null);
        List<String> out = new ArrayList<>();
        if (s != null) for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void saveMore(Context c, List<String> more) {
        prefs(c).edit().putString("more", TextUtils.join(",", more)).apply();
    }

    // ---------------------------------------------------------------- one app

    /** The order of names in the screen's language (apps here, countries in Settings). */
    static Collator collator(Text t) {
        return Collator.getInstance(t.ar ? new Locale("ar") : Locale.ENGLISH);
    }

    static String name(Context c, Text t, String pkg) {
        if (t.ar && AR_NAMES.containsKey(pkg)) return AR_NAMES.get(pkg);
        if (EN_NAMES.containsKey(pkg)) return EN_NAMES.get(pkg);
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    static Drawable icon(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * The app's icon at the highest density it has, for Home's large squares (the TV's own density can
     * pick a small one that blurs when drawn at 157 px).
     */
    static Drawable bigIcon(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (ai.icon != 0) {
                Drawable d = pm.getResourcesForApplication(ai)
                    .getDrawableForDensity(ai.icon, android.util.DisplayMetrics.DENSITY_XXXHIGH, null);
                if (d != null) return d;
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Fall back to the usual icon below.
        }
        return icon(c, pkg);
    }

    /** Opens the app's TV entry, or its phone-style one; back at its main screen, not wherever it was left. */
    /** The app's TV launch intent, or its phone one, or null if it isn't installed. */
    static Intent launchIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    static void launch(Context c, String pkg) {
        Intent i = launchIntent(c, pkg);
        if (i == null) return;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // The app went away or refuses outside launches; stay where we are.
        }
    }

    static boolean isLatin(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.UnicodeBlock.of(s.charAt(i)) == Character.UnicodeBlock.ARABIC) return false;
        return true;
    }
}
