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
 */
final class CropCheck {
    private CropCheck() {}

    private static Boolean on;
    private static String version = "";
    private static final Set<String> SEEN = new HashSet<>();
    private static final Rect R = new Rect();

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
        StringBuilder found = new StringBuilder();
        walk(root, false, where + "|" + lang, found);
        if (found.length() == 0) return;
        File dir = c.getExternalFilesDir(null);
        if (dir == null) return;
        try (FileOutputStream out = new FileOutputStream(new File(dir, "crop.txt"), true)) {
            out.write(found.toString().getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException ignored) {
            // A check only.
        }
    }

    private static void walk(View v, boolean inList, String where, StringBuilder found) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView) check((TextView) v, inList, where, found);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            boolean list = inList || v instanceof ScrollView || v instanceof android.widget.HorizontalScrollView;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), list, where, found);
        }
    }

    private static void check(TextView v, boolean inList, String where, StringBuilder found) {
        CharSequence text = v.getText();
        Layout l = v.getLayout();
        if (text == null || text.length() == 0 || l == null || v.getWidth() <= 0 || v.getHeight() <= 0) return;
        String why = null;
        int innerW = v.getWidth() - v.getTotalPaddingLeft() - v.getTotalPaddingRight();
        int innerH = v.getHeight() - v.getTotalPaddingTop() - v.getTotalPaddingBottom();
        for (int i = 0; i < l.getLineCount() && why == null; i++) if (l.getEllipsisCount(i) > 0) why = "ends in ...";
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
        synchronized (SEEN) {
            if (!SEEN.add(line)) return;
        }
        found.append(version).append('|').append(line).append('\n');
    }
}
