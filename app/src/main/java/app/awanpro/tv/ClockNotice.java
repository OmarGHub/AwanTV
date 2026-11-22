package app.awanpro.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

/**
 * The TV's clock an hour off by old time zone data (1.27.0a; Omar's design, 2026-11-22).
 *
 * A TV takes the time from the network as UTC and turns it into its local clock with the time zone rules kept in its
 * own system (tzdata); only a system update brings new rules. Morocco went to GMT for good on 20 September 2026 (IANA
 * tz 2026), after GMT+1 all year but Ramadan since 2018: a TV whose rules are older still counts Morocco (and Western
 * Sahara) as GMT+1, so its clock and every time written on screen read an hour ahead, while the adhan still comes at
 * the right moment (the times are moments, checked against the clock's UTC). The viewer's natural fix, setting the
 * clock by hand, puts the adhan an hour late; the right one is a GMT zone without summer time (Dakar, Abidjan).
 *
 * {@link #stale} looks only at the TV's own zone: one of {@link Change#zones}, after {@link Change#since}, whose offset
 * now is not the country's. Silent again once the rules are updated, the viewer picks another zone, or the old rules
 * happen to agree (Morocco's Ramadan). Told over Home once a day (MainActivity.showClock, after What's new and a newer
 * version, never with them) until fixed or Don't remind me; always in Settings > About > App status while it lasts.
 * Its keys start with "clk_", so they stay on one TV (SettingsTransfer.LOCAL).
 */
final class ClockNotice {
    private ClockNotice() {}

    /** One country's clock change that TVs with older time zone data don't know. */
    static final class Change {
        final String[] zones;
        final long since;
        final int offsetMs;

        Change(long since, int offsetMs, String... zones) {
            this.zones = zones;
            this.since = since;
            this.offsetMs = offsetMs;
        }
    }

    /** Morocco and Western Sahara: GMT from 20 September 2026, 02:00 local (01:00 UTC), IANA tz 2026. */
    static final Change MOROCCO_2026 = new Change(1_789_866_000_000L, 0, "Africa/Casablanca", "Africa/El_Aaiun");

    static final Change[] CHANGES = {MOROCCO_2026};

    private static final String SHOWN_AT = "clk_shown_at", MUTED = "clk_muted";
    static final long AGAIN_MS = 24 * 3600_000L;

    /** The change this TV's clock misses now, or null: its zone is none of theirs, or its rules are right. */
    static Change stale(long now) {
        return stale(TimeZone.getDefault(), now);
    }

    static Change stale(TimeZone tz, long now) {
        for (Change ch : CHANGES) {
            if (now >= ch.since && Arrays.asList(ch.zones).contains(tz.getID()) && tz.getOffset(now) != ch.offsetMs) {
                return ch;
            }
        }
        return null;
    }

    /** To tell over Home now: stale, not muted, and not told within a day (a clock set back never holds it off). */
    static boolean due(Context c) {
        if (stale(System.currentTimeMillis()) == null) return false;
        SharedPreferences p = Prefs.of(c);
        if (p.getBoolean(MUTED, false)) return false;
        long at = p.getLong(SHOWN_AT, 0), now = System.currentTimeMillis();
        return at > now || now - at >= AGAIN_MS;
    }

    /** Closed: again tomorrow; mute: not over Home again (App status still tells it). */
    static void closed(Context c, boolean mute) {
        SharedPreferences.Editor e = Prefs.of(c).edit().putLong(SHOWN_AT, System.currentTimeMillis());
        if (mute) e.putBoolean(MUTED, true);
        e.apply();
    }

    /** A moment written on the country's right clock, in the screen's clock format and digits. */
    private static CharSequence rightClock(Text t, Date d, Change ch, boolean h24) {
        // The screen writes moments on the TV's clock: moved by the difference, it writes the right one.
        long diff = ch.offsetMs - TimeZone.getDefault().getOffset(d.getTime());
        return Screen.clock(t, new Date(d.getTime() + diff), h24, 1f);
    }

    /**
     * The window's words: the body (with today's Maghrib as the screen writes it and as it should be, when the
     * location is in Morocco), the fix's header, its two steps, the warning, and the reminder's note.
     */
    static String[] lines(Context c, Text t, Change ch) {
        String body = t.tzOldBody2();
        if (Habous.table(c) != null) {
            Date maghrib = new PrayerDay(c, new Date()).times[PrayerDay.MAGHRIB];
            boolean h24 = Prefs.clock24(c);
            body = t.tzOldBody(Screen.clock(t, maghrib, h24, 1f), rightClock(t, maghrib, ch, h24));
        }
        return new String[] {body, t.tzOldFix(), t.d("1. ") + t.tzOldStep1(), t.d("2. ") + t.tzOldStep2(),
            t.tzOldWarn(), t.tzOldNote()};
    }

    /** The layer's parts the screen needs: the whole layer and its buttons in order. */
    static final class Card {
        FrameLayout layer;
        FocusTile ok;
        java.util.List<FocusTile> buttons = new java.util.ArrayList<>();
    }

    /** The window over Home: as UpdateNotice's, without a code. */
    static Card card(Screen s, Change ch, Runnable later, Runnable mute) {
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

        int dir = t.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR;
        TextView title = s.text(s.displayBold, Tokens.FONT_SIZE_DISPLAY_S, Tokens.GOLD_400);
        title.setText(t.tzOldTitle());
        title.setTextDirection(dir);
        card.addView(title);
        s.lineHeight(title, 1.2f);
        String[] l = lines(s, t, ch);
        card.addView(line(s, l[0], Tokens.PLASTER_100, 24, 8, dir));
        card.addView(line(s, l[1], Tokens.MIST_400, 24, 10, dir));
        card.addView(line(s, l[2], Tokens.MIST_400, 22, 4, dir));
        card.addView(line(s, l[3], Tokens.MIST_400, 22, 4, dir));
        card.addView(line(s, l[4], Tokens.GOLD_400, 22, 10, dir));
        card.addView(line(s, l[5], Tokens.MIST_400, 20, 10, dir));

        float r = FocusTile.REACH;
        LinearLayout buttons = new LinearLayout(s);
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        out.buttons.add(button(s, t.newsOk(), later));
        out.buttons.add(button(s, t.tzOldLater(), mute));
        for (int i = 0; i < out.buttons.size(); i++) {
            LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
            if (i > 0) b.setMarginStart(Ui.ipx(24 - 2 * r));
            buttons.addView(out.buttons.get(i), b);
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.ipx(24 - r);
        blp.setMarginStart(-Ui.ipx(r));
        blp.setMarginEnd(-Ui.ipx(r));
        card.addView(buttons, blp);
        out.ok = out.buttons.get(0);

        out.layer.addView(card, new FrameLayout.LayoutParams(Ui.ipx(1100), -2, Gravity.CENTER));
        return out;
    }

    /** The layer's keys: Back is OK (again tomorrow); Left and Right move along its two buttons. */
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
                boolean forward = (k == KeyEvent.KEYCODE_DPAD_LEFT) == rtl;
                int to = Math.max(0, Math.min(c.buttons.size() - 1, at + (forward ? 1 : -1)));
                c.buttons.get(to).requestFocus();
            }
            return true;
        }
        return k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN;
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
        b.addView(v, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> v.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        b.setFocusable(true);
        b.setOnClickListener(x -> run.run());
        return b;
    }
}
