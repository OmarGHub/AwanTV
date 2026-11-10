package app.awanpro.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * What's new: once after an update, the versions the TV has not seen yet, newest first (an update that skipped some
 * shows them all), as a layer over Home itself (MainActivity.showNews: an activity of its own over Home was closed
 * again by HomeGuard on a TCL TV, Home being no longer in front); this activity shows every version written, from
 * Settings > About ({@link #ALL}). Never on a new install.
 * Each version's lines are in {@link Text#news}; {@link #VERSIONS} lists the versions that have them, newest first, and a
 * release adds its own at the top. The last one seen is the prefs key {@link #SEEN} (SettingsTransfer.LOCAL, so a Reset
 * does not show it all again), only once it has been closed. Translucent (Theme.Awan.Call): Settings shows dimmed
 * under the card.
 */
public class NewsActivity extends Screen {

    /** The versions with a What's new, newest first. */
    static final String[] VERSIONS = {"1.24.0", "1.22.0", "1.21.0", "1.20.0", "1.19.0", "1.18.0", "1.17.0", "1.14.2", "1.14.1", "1.14.0", "1.13.0", "1.10.0", "1.9.0", "1.8.0", "1.7.0", "1.5.0", "1.4.0", "1.3.0", "1.2.0", "1.1.0"};
    static final String SEEN = "news_seen";
    /** Extra: every version, from Settings > About (no "seen" kept). */
    static final String ALL = "all";

    /** This build's release number, a test build's letter dropped ("1.2.0" for 1.2.0a). */
    static String current(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null ? "" : v.replaceFirst("[a-z]+$", "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * The versions to show after an update, newest first; empty when there is nothing new. A new install has nothing
     * to show and starts as seen. A TV updated from a version older than the first with a What's new (no SEEN yet)
     * sees every version written.
     */
    static List<String> pending(Context c) {
        List<String> out = new ArrayList<>();
        String now = current(c);
        if (now.isEmpty()) return out;
        SharedPreferences p = Prefs.of(c);
        String seen = p.getString(SEEN, null);
        if (seen == null && freshInstall(c)) {
            p.edit().putString(SEEN, now).apply();
            return out;
        }
        for (String v : VERSIONS) {
            if (Updates.compare(v, now) > 0) continue;
            if (seen == null || Updates.compare(v, seen) > 0) out.add(v);
        }
        if (out.isEmpty() && !now.equals(seen)) p.edit().putString(SEEN, now).apply();
        return out;
    }

    private static boolean freshInstall(Context c) {
        try {
            android.content.pm.PackageInfo i = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return i.firstInstallTime == i.lastUpdateTime;
        } catch (Exception e) {
            return false;
        }
    }

    /** The card's parts that the screen holding it needs: the whole layer, its list (Up/Down) and OK. */
    static final class Card {
        FrameLayout layer;
        ScrollView scroll;
        FocusTile ok;
    }

    /**
     * The dimmed layer with the card: after an update over Home itself (MainActivity.showNews, so Home stays the screen
     * in front and HomeGuard leaves it be), and in this activity from Settings > About.
     */
    static Card card(Screen s, List<String> versions, boolean all, Runnable close) {
        Text t = s.t;
        Card out = new Card();
        out.layer = new FrameLayout(s);
        out.layer.setBackgroundColor(Tokens.SCRIM_MENU);
        out.layer.setClickable(true);

        LinearLayout card = new LinearLayout(s);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.ipx(48), Ui.ipx(40), Ui.ipx(48), Ui.ipx(32));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tokens.LAPIS_800);
        bg.setCornerRadius(Ui.px(24));
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
        card.setBackground(bg);
        card.setClipChildren(false);
        card.setClipToPadding(false);

        TextView title = s.text(s.displayBold, Tokens.FONT_SIZE_DISPLAY_S, Tokens.GOLD_400);
        title.setText(all ? t.newsAll() : t.newsTitle(versions.get(0)));
        card.addView(title);
        s.lineHeight(title, 1.2f);

        int dir = t.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR;
        LinearLayout list = new LinearLayout(s);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < versions.size(); i++) {
            String v = versions.get(i);
            // After an update the title names the newest; the others (and every one in the full list) get a heading.
            if (all || i > 0) {
                TextView head = s.text(s.ui500, 22, Tokens.GOLD_400);
                head.setText(all ? "Awan TV " + v : t.newsAlso(v));
                head.setTextDirection(dir);
                LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
                hl.topMargin = Ui.ipx(i == 0 ? 14 : 28);
                list.addView(head, hl);
            }
            for (String[] item : t.news(v)) list.addView(feature(s, item[0], item[1]));
        }
        ScrollView scroll = new ScrollView(s);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Ui.ipx(6);
        card.addView(scroll, slp);
        // Long lists scroll (Up/Down) within at most this height.
        scroll.post(() -> {
            int most = Ui.ipx(560);
            if (scroll.getHeight() > most) {
                scroll.getLayoutParams().height = most;
                scroll.requestLayout();
            }
        });
        out.scroll = scroll;

        if (!all) {
            TextView later = s.text(s.ui400, 20, Tokens.MIST_400);
            later.setText(t.newsLater());
            later.setTextDirection(dir);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            ll.topMargin = Ui.ipx(22);
            card.addView(later, ll);
        }

        float reach = FocusTile.REACH;
        FocusTile ok = new FocusTile(s, 14);
        ok.setRest(Tokens.LAPIS_700, 0, 0);
        TextView okText = s.text(s.ui500, 26, Tokens.PLASTER_100);
        okText.setText(t.newsOk());
        okText.setGravity(Gravity.CENTER);
        ok.addView(okText, new FrameLayout.LayoutParams(-1, -1));
        ok.setLook(f -> okText.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        ok.setFocusable(true);
        ok.setOnClickListener(v -> close.run());
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(Ui.ipx(240 + 2 * reach), Ui.ipx(68 + 2 * reach));
        olp.topMargin = Ui.ipx(26 - reach);
        olp.setMarginStart(-Ui.ipx(reach));
        card.addView(ok, olp);
        out.ok = ok;

        out.layer.addView(card, new FrameLayout.LayoutParams(Ui.ipx(1000), -2, Gravity.CENTER));
        return out;
    }

    /** The card's keys: Back closes, Up/Down scroll its list, OK on its one button; nothing else moves. */
    static boolean key(Card c, KeyEvent e, Runnable close) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) close.run();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down) c.scroll.smoothScrollBy(0, Ui.ipx(k == KeyEvent.KEYCODE_DPAD_UP ? -180 : 180));
            return true;
        }
        return k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private Card shown;
    /** The versions shown, worked out once (a rebuild for the theme at Maghrib keeps them). */
    private List<String> versions;

    /** Settings > About > What's new: every version written, newest first. */
    @Override
    void build() {
        if (versions == null) {
            versions = new ArrayList<>();
            for (String v : VERSIONS) if (Updates.compare(v, current(this)) <= 0) versions.add(v);
        }
        if (versions.isEmpty()) {
            finish();
            return;
        }
        newRoot();
        root.setBackground(null);
        shown = card(this, versions, true, this::finish);
        root.addView(shown.layer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        root.post(shown.ok::requestFocus);
    }

    /** One line of what's new: a gold dot, its name, and where it is or what it does. */
    private static View feature(Screen s, String name, String help) {
        LinearLayout row = new LinearLayout(s);
        row.setGravity(Gravity.TOP);
        TextView dot = s.text(s.ui500, 26, Tokens.GOLD_400);
        dot.setText("•");
        LinearLayout words = new LinearLayout(s);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView n = s.text(s.ui500, 26, Tokens.PLASTER_100);
        n.setText(name);
        TextView h = s.text(s.ui400, 21, Tokens.MIST_400);
        h.setText(help);
        h.setLineSpacing(0, 1.25f);
        int dir = s.t.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR;
        n.setTextDirection(dir);
        h.setTextDirection(dir);
        words.addView(n);
        words.addView(h);
        row.addView(dot);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1);
        wl.setMarginStart(Ui.ipx(16));
        row.addView(words, wl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.ipx(18);
        row.setLayoutParams(rl);
        return row;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (shown != null && key(shown, e, this::finish)) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override
    void onTick() {
        // Nothing moves here.
    }
}
