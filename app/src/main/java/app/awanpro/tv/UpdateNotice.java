package app.awanpro.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A newer version out, told over Home (MainActivity.showUpdate) as What's new is: a layer of Home's own, never over
 * the screensaver or the call, which run with no one at the remote. Shown when Home opens or a key was just pressed
 * there, after What's new and never with it. Once for each new version, then again a week after it was closed, until
 * the TV has it or the viewer chooses Don't remind me of this version (that version only: the next one shows again).
 * The gold mark on Home's Settings button and on Settings > About stays until the TV has it, whatever was chosen.
 * Its keys start with "upd_", so they stay on one TV (SettingsTransfer.LOCAL).
 */
final class UpdateNotice {
    private UpdateNotice() {}

    private static final String SHOWN = "upd_shown", SHOWN_AT = "upd_shown_at", MUTED = "upd_muted";
    static final long AGAIN_MS = 7 * 24 * 3600_000L;
    /** The QR code's own light ground and dark ink, the same on every theme (tools/AwanTV-build-qr.py). */
    static final int QR_LIGHT = 0xFFEFE7D6, QR_DARK = 0xFF0C1B33;

    /** This build's version as installed ("1.5.0a" for a test build). */
    static String installed(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    /** The newer version out, or null: none known. Settings' mark and Home's. */
    static String newer(Context c) {
        String now = installed(c);
        return now.isEmpty() ? null : Updates.newer(c, now);
    }

    /** The version to tell of now, or null: none, told less than a week ago, or that version muted. */
    static String due(Context c) {
        String v = newer(c);
        if (v == null) return null;
        SharedPreferences p = Prefs.of(c);
        if (!v.equals(p.getString(SHOWN, ""))) return v;
        if (v.equals(p.getString(MUTED, ""))) return null;
        long at = p.getLong(SHOWN_AT, 0), now = System.currentTimeMillis();
        // A clock set back (a TV without the time yet) must not hold it off for good.
        return at > now || now - at >= AGAIN_MS ? v : null;
    }

    /** Closed (OK, Back, or a reminder took Home): again in a week. mute: never again for this version. */
    static void closed(Context c, String version, boolean mute) {
        SharedPreferences.Editor ed = Prefs.of(c).edit()
            .putString(SHOWN, version).putLong(SHOWN_AT, System.currentTimeMillis());
        if (mute) ed.putString(MUTED, version);
        ed.apply();
    }

    /** What the window shows: the version (Update now where allowed), a download, the install, or a failure. */
    static final int OFFER = 0, DOWNLOAD = 1, INSTALL = 2, FAILED = 3, ASK = 4;

    static int mode(String version) {
        if (version == null || !version.equals(SelfUpdate.version())) return OFFER;
        switch (SelfUpdate.state()) {
            case SelfUpdate.DOWNLOADING: return DOWNLOAD;
            // Android's question waiting: its own mode, so the window is rebuilt with Continue installing.
            case SelfUpdate.INSTALLING: return SelfUpdate.confirmWaiting() ? ASK : INSTALL;
            case SelfUpdate.FAILED: return FAILED;
            default: return OFFER;
        }
    }

    /** The window's buttons. later: OK / Later (again in a week); mute: not for this version. */
    interface Actions {
        void update();
        void later();
        void mute();
        void cancel();
        void confirm();
    }

    /** The layer's parts the screen needs: the whole layer, its buttons in order, and the download's line and bar. */
    static final class Card {
        FrameLayout layer;
        FocusTile ok;
        java.util.List<FocusTile> buttons = new java.util.ArrayList<>();
        String version;
        int mode;
        TextView progress;
        LinearLayout bar;
        View fill, rest;
    }

    static Card card(Screen s, String version, Actions a) {
        Text t = s.t;
        Card out = new Card();
        out.version = version;
        out.mode = mode(version);
        out.layer = new FrameLayout(s);
        out.layer.setBackgroundColor(Tokens.SCRIM_MENU);
        out.layer.setClickable(true);

        LinearLayout card = new LinearLayout(s);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.ipx(48), Ui.ipx(40), Ui.ipx(48), Ui.ipx(32));
        GradientDrawable bg = round(Tokens.LAPIS_800, 24);
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
        card.setBackground(bg);
        card.setClipChildren(false);
        card.setClipToPadding(false);

        int dir = t.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR;
        LinearLayout across = new LinearLayout(s);
        across.setGravity(Gravity.TOP);
        across.setClipChildren(false);
        LinearLayout words = new LinearLayout(s);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setClipChildren(false);
        words.setClipToPadding(false);

        TextView title = s.text(s.displayBold, Tokens.FONT_SIZE_DISPLAY_S, Tokens.GOLD_400);
        title.setText(t.updateTitle(version));
        words.addView(title);
        s.lineHeight(title, 1.2f);
        words.addView(line(s, t.updateHave(installed(s)), Tokens.PLASTER_100, 24, 6, dir));

        boolean allowed = SelfUpdate.allowed(s);
        float r = FocusTile.REACH;
        LinearLayout buttons = new LinearLayout(s);
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        switch (out.mode) {
            case DOWNLOAD:
            case INSTALL:
            case ASK: {
                boolean down = out.mode == DOWNLOAD;
                out.progress = line(s, down ? t.updateDownloading(version, SelfUpdate.percent()) : t.updateInstalling(),
                    Tokens.PLASTER_100, 26, 14, dir);
                words.addView(out.progress);
                out.bar = new LinearLayout(s);
                out.bar.setBackground(round(Tokens.LAPIS_700, 7));
                out.bar.setClipToOutline(true);
                out.fill = new View(s);
                out.fill.setBackground(round(Tokens.GOLD_400, 7));
                out.rest = new View(s);
                out.bar.addView(out.fill, new LinearLayout.LayoutParams(0, -1, 1));
                out.bar.addView(out.rest, new LinearLayout.LayoutParams(0, -1, 99));
                LinearLayout.LayoutParams blp0 = new LinearLayout.LayoutParams(-1, Ui.ipx(14));
                blp0.topMargin = Ui.ipx(20);
                words.addView(out.bar, blp0);
                progress(out, t);
                if (down) {
                    words.addView(line(s, t.updateLeave(), Tokens.MIST_400, 20, 16, dir));
                    words.addView(line(s, t.updateThen(), Tokens.MIST_400, 20, 6, dir));
                    out.buttons.add(button(s, t.cancel(), a::cancel));
                } else {
                    words.addView(line(s, t.updateConfirm(), Tokens.MIST_400, 20, 16, dir));
                    // Android's question not on screen (blocked while the viewer was in another app): here again.
                    if (SelfUpdate.confirmWaiting()) out.buttons.add(button(s, t.updateContinue(), a::confirm));
                    out.buttons.add(button(s, t.close(), a::later));
                }
                break;
            }
            case FAILED:
                words.addView(line(s, t.updateFailed(), Tokens.PLASTER_100, 24, 10, dir));
                out.buttons.add(button(s, t.updateRetry(), a::update));
                out.buttons.add(button(s, t.updateLater(), a::later));
                break;
            default:
                words.addView(line(s, t.updateWhy(), Tokens.PLASTER_100, 24, 10, dir));
                words.addView(line(s, allowed ? t.updateHowSelf() : t.updateHow(), Tokens.MIST_400, 24, 10, dir));
                if (!allowed) words.addView(line(s, t.updateManual(), Tokens.GOLD_400, 22, 10, dir));
                words.addView(line(s, t.updateAgain(), Tokens.MIST_400, 20, 12, dir));
                if (allowed) out.buttons.add(button(s, t.updateNow(), a::update));
                out.buttons.add(button(s, allowed ? t.updateLater() : t.newsOk(), a::later));
                out.buttons.add(button(s, t.updateMute(), a::mute));
        }
        for (int i = 0; i < out.buttons.size(); i++) {
            LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(-2, Ui.ipx(68 + 2 * r));
            if (i > 0) b.setMarginStart(Ui.ipx(24 - 2 * r));
            buttons.addView(out.buttons.get(i), b);
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.ipx(28 - r);
        blp.setMarginStart(-Ui.ipx(r));
        words.addView(buttons, blp);
        out.ok = out.buttons.get(0);

        across.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-2, -2);
        qlp.setMarginStart(Ui.ipx(40));
        across.addView(qrTile(s, R.drawable.qr_release, archLogo(s), Updates.shown(Updates.RELEASE)), qlp);
        card.addView(across, new LinearLayout.LayoutParams(-1, -2));

        out.layer.addView(card, new FrameLayout.LayoutParams(Ui.ipx(1240), -2, Gravity.CENTER));
        return out;
    }

    /** The download's line and bar, as it moves (no rebuild, so focus stays). */
    static void progress(Card c, Text t) {
        if (c.progress == null) return;
        int p = Math.max(0, Math.min(100, SelfUpdate.percent()));
        if (c.mode == DOWNLOAD) c.progress.setText(t.updateDownloading(c.version, p));
        int done = c.mode == DOWNLOAD ? Math.max(1, p) : 100;
        ((LinearLayout.LayoutParams) c.fill.getLayoutParams()).weight = done;
        ((LinearLayout.LayoutParams) c.rest.getLayoutParams()).weight = Math.max(0, 100 - done);
        c.bar.requestLayout();
    }

    /** The layer's keys: Back is Later (the download goes on); Left and Right move along its buttons. */
    static boolean key(Card c, KeyEvent e, Runnable later, boolean rtl) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) later.run();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                int at = 0;
                for (int i = 0; i < c.buttons.size(); i++) if (c.buttons.get(i).isFocused()) at = i;
                // The buttons run from the start edge: the right one first in Arabic.
                boolean forward = (k == KeyEvent.KEYCODE_DPAD_LEFT) == rtl;
                int to = Math.max(0, Math.min(c.buttons.size() - 1, at + (forward ? 1 : -1)));
                c.buttons.get(to).requestFocus();
            }
            return true;
        }
        return k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    /** The small gold mark of a newer version, on Home's Settings button and Settings > About. */
    static View mark(Context c, int color) {
        View dot = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Tokens.GOLD_400);
        d.setStroke(Ui.ipx(3), color);
        dot.setBackground(d);
        return dot;
    }

    private static TextView line(Screen s, String text, int color, float size, float top, int dir) {
        TextView v = s.text(s.ui400, size, color);
        v.setText(text);
        v.setTextDirection(dir);
        v.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.ipx(top);
        v.setLayoutParams(lp);
        return v;
    }

    private static FocusTile button(Screen s, String label, Runnable run) {
        FocusTile b = new FocusTile(s, 14);
        b.setRest(Tokens.LAPIS_700, 0, 0);
        TextView v = s.text(s.ui500, 26, Tokens.PLASTER_100);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        int pad = Ui.ipx(36 + FocusTile.REACH);
        v.setPadding(pad, 0, pad, 0);
        b.addView(v, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> v.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        b.setFocusable(true);
        b.setOnClickListener(x -> run.run());
        return b;
    }

    private static GradientDrawable round(int color, float radiusDesignPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(Ui.px(radiusDesignPx));
        return d;
    }

    /** Awan TV's arch, gold on the code's dark ink (as Settings > About's). */
    static View archLogo(Context c) {
        FrameLayout v = new FrameLayout(c);
        v.setBackground(round(QR_DARK, 10));
        Icon arch = new Icon(c, Icon.MIHRAB, 1.3f);
        arch.setColor(Tokens.GOLD_400);
        v.addView(arch, new FrameLayout.LayoutParams(Ui.ipx(18), Ui.ipx(23), Gravity.CENTER));
        return v;
    }

    /** A code on its light tile (the padding is its quiet zone), drawn without smoothing, its logo in the middle. */
    static FrameLayout qrTile(Context c, int image, View logo, String description) {
        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(round(QR_LIGHT, 14));
        int quiet = Ui.ipx(18);
        tile.setPadding(quiet, quiet, quiet, quiet);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap bits = BitmapFactory.decodeResource(c.getResources(), image, o);
        BitmapDrawable d = new BitmapDrawable(c.getResources(), bits);
        d.setFilterBitmap(false);
        d.setAntiAlias(false);
        ImageView code = new ImageView(c);
        code.setImageDrawable(d);
        code.setScaleType(ImageView.ScaleType.FIT_XY);
        code.setContentDescription(description);
        tile.addView(code, new FrameLayout.LayoutParams(Ui.ipx(194), Ui.ipx(194)));
        FrameLayout ring = new FrameLayout(c);
        ring.setBackground(round(QR_LIGHT, 13));
        int r = Ui.ipx(4);
        ring.setPadding(r, r, r, r);
        ring.addView(logo, new FrameLayout.LayoutParams(Ui.ipx(40), Ui.ipx(40)));
        tile.addView(ring, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return tile;
    }
}
