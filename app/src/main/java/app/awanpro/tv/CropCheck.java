package app.awanpro.tv;

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Test builds only (a version ending in a letter, as TEXT_DUMP): every text on screen that does not fit its place,
 * written to files/crop.txt for tools/AwanTV-textdump.ps1 -Crops (languages plan, phase 4: a language a quarter
 * longer than English must not be cut anywhere). Each screen asks every two seconds while in front (Screen's tick,
 * the screensaver's), so pages, lists and the adhkar are seen as the viewer goes through them.
 *
 * <p>Found: a line ending in "…", more lines than the view shows, a single line wider than its view, text taller
 * than its view, or a view cut by its parent (not inside a list, which scrolls on purpose). One line each, once per
 * process: version|screen|language|why|text.
 *
 * <p>Since 1.21.0: a finding is written only when two checks in a row see it (within {@link #AGAIN_MS}), so a row
 * caught as it is drawn, before {@link Screen#fitWidth} makes it smaller, is not taken for a cut (1.21.0c: names
 * written at their full size, whole on the screen). mp3quran.net's own names (the reciter channels, {@link Radios})
 * are not checked: their data as it comes, not Awan TV's words (Omar).
 */
final class CropCheck {
    private CropCheck() {}

    private static Boolean on;
    private static String version = "";
    private static final Set<String> SEEN = new HashSet<>();
    private static final Rect R = new Rect();
    /** A finding is written when the check before saw it too, at most this long ago (checks run every 2 s). */
    private static final long AGAIN_MS = 5000;
    private static final java.util.Map<String, Long> LAST = new java.util.HashMap<>();
    private static Set<String> data = new HashSet<>();
    private static Object dataFrom;

    static boolean on(Context c) {
        if (on == null) {
            String v = "";
            try {
                v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                // No version: off.
            }
            version = v == null ? "" : v;
            on = version.matches(".*\\d[a-z]+$");
        }
        return on;
    }

    /** Looks through one screen's views; a new finding is added to the file. Main thread. */
    static void scan(Context c, View root, String where, String lang) {
        if (root == null || !on(c) || !root.isShown()) return;
        File dir0 = c.getExternalFilesDir(null);
        // The list cleared from the computer (AwanTV-textdump -Crops -Clear deletes the file): forget what was seen,
        // or a cut found before the clearing is never written again (1.20.0c).
        if (dir0 != null && !new File(dir0, "crop.txt").exists()) {
            synchronized (SEEN) {
                SEEN.clear();
            }
        }
        StringBuilder found = new StringBuilder();
        walk(root, false, where + "|" + lang, dataNames(c), found);
        if (found.length() == 0) return;
        File dir = c.getExternalFilesDir(null);
        if (dir == null) return;
        try (FileOutputStream out = new FileOutputStream(new File(dir, "crop.txt"), true)) {
            out.write(found.toString().getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException ignored) {
            // A check only.
        }
    }

    /** mp3quran.net's names of the reciter channels in every language, read again when Radios reads its list. */
    private static Set<String> dataNames(Context c) {
        java.util.List<Radio.Station> extra = Radios.extra(c);
        if (extra != dataFrom) {
            Set<String> names = new HashSet<>();
            for (Radio.Station st : extra) {
                names.add(plain(st.ar));
                names.add(plain(st.en));
                for (String n : st.more.values()) names.add(plain(n));
            }
            data = names;
            dataFrom = extra;
        }
        return data;
    }

    /** Without the direction marks Text.d adds. */
    private static String plain(CharSequence t) {
        return t == null ? "" : t.toString().replaceAll("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "").trim();
    }

    private static void walk(View v, boolean inList, String where, Set<String> data, StringBuilder found) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView && !data.contains(plain(((TextView) v).getText()))) {
            check((TextView) v, inList, where, found);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            boolean list = inList || v instanceof ScrollView || v instanceof android.widget.HorizontalScrollView;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), list, where, data, found);
        }
    }

    private static void check(TextView v, boolean inList, String where, StringBuilder found) {
        CharSequence text = v.getText();
        Layout l = v.getLayout();
        if (text == null || text.length() == 0 || l == null || v.getWidth() <= 0 || v.getHeight() <= 0) return;
        String why = null;
        int innerW = v.getWidth() - v.getTotalPaddingLeft() - v.getTotalPaddingRight();
        int innerH = v.getHeight() - v.getTotalPaddingTop() - v.getTotalPaddingBottom();
        for (int i = 0; i < l.getLineCount() && why == null; i++) {
            if (l.getEllipsisCount(i) > 0) {
                // How wide the whole text would be at the view's present size, against its room.
                float whole = android.text.Layout.getDesiredWidth(text, v.getPaint());
                why = "ends in ... (needs " + Math.round(whole) + " of " + innerW + " px)";
            }
        }
        if (why == null && l.getLineCount() > v.getMaxLines()) why = "more lines than shown";
        if (why == null && l.getLineCount() == 1 && l.getLineWidth(0) > innerW + 1) {
            why = "wider than its place by " + Math.round(l.getLineWidth(0) - innerW) + " of " + innerW + " px";
        }
        // Font padding off with the negative margins of Screen.lineHeight lets glyphs sit past the box by design:
        // only a whole line more counts.
        if (why == null && l.getLineCount() > 1 && l.getHeight() > innerH + l.getHeight() / l.getLineCount() / 2) {
            why = "taller than its place";
        }
        if (why == null && !inList && v.getGlobalVisibleRect(R) && R.width() < v.getWidth() - 2
                && l.getLineWidth(0) > R.width()) {
            why = "cut by its box";
        }
        if (why == null) return;
        String line = where + "|" + why + "|" + text.toString().replace("\n", "\\n").replace("|", "/");
        // The text alone (its width may differ between the two checks): seen by the check before, it stays cut.
        String key = where + "|" + text;
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (SEEN) {
            Long before = LAST.put(key, now);
            if (before == null || now - before > AGAIN_MS) return;
            if (!SEEN.add(line)) return;
        }
        found.append(version).append('|').append(line).append('\n');
    }
}
