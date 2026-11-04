package app.awanpro.tv.installer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Awan TV's look (Afnon's Mihrab tokens, design/afnon-mihrab/tokens.json) for a phone: lapis ground, gold accent,
 * plaster text, Alexandria for the interface and Amiri for titles in both languages, as the TV app. Small factories, so the screens in
 * MainActivity read as layout.
 */
final class Ui {
    private Ui() {}

    static final int LAPIS_900 = 0xff0c1b33, LAPIS_800 = 0xff12264a, LAPIS_700 = 0xff1a3360,
            GOLD_400 = 0xffd8b46a, GOLD_200 = 0xfff0d79c, PLASTER = 0xffefe7d6, MIST = 0xff9fb0cc,
            LINE_SOFT = 0x40efe7d6, GOLD_SOFT = 0x26d8b46a, CORAL = 0xffe39a86, CORAL_SOFT = 0x26e39a86;

    private static Typeface light, regular, medium, semibold, display;

    static void init(Context c) {
        // The title face follows the language, which the language button can change while the app runs.
        display = c.getResources().getFont(arabic(c) ? R.font.amiri_bold : R.font.amiri_regular);
        if (light != null) return;
        light = c.getResources().getFont(R.font.alexandria_light);
        regular = c.getResources().getFont(R.font.alexandria_regular);
        medium = c.getResources().getFont(R.font.alexandria_medium);
        semibold = c.getResources().getFont(R.font.alexandria_semibold);
    }

    /** Whether the Arabic strings are in use (values-ar), which is also what decides the title face. */
    static boolean arabic(Context c) {
        return "ar".equals(c.getString(R.string.lang));
    }

    static Typeface medium() { return medium; }
    static Typeface semibold() { return semibold; }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, CharSequence s, int color, float sp, Typeface face) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setLineSpacing(0, 1.3f);
        t.setIncludeFontPadding(false);
        // A line that starts with Latin ("Awan TV ...") still reads from the language's own side.
        t.setTextDirection(arabic(c) ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        links(t);
        return t;
    }

    /** Our addresses (github.com/..., awanpro.app) open in the phone's browser; again after a setText. */
    static void links(TextView t) {
        CharSequence s = t.getText();
        String plain = s == null ? "" : s.toString();
        if (plain.contains("github.com/") || plain.contains("awanpro.app")) {
            Linkify.addLinks(t, Patterns.WEB_URL, "https://");
            t.setLinkTextColor(GOLD_200);
        }
    }

    static TextView title(Context c, CharSequence s) {
        TextView t = text(c, s, GOLD_400, arabic(c) ? 30 : 28, display);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    static TextView lead(Context c, CharSequence s) {
        TextView t = text(c, s, MIST, 15, light);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    static TextView label(Context c, CharSequence s) { return text(c, s, PLASTER, 16, medium); }

    static TextView small(Context c, CharSequence s) { return text(c, s, MIST, 13, light); }

    /** Numbers (ports, codes, addresses) stay in Latin digits and left-to-right, as the TV shows them. */
    static TextView latin(TextView t) {
        t.setTextDirection(View.TEXT_DIRECTION_LTR);
        t.setTextLocale(java.util.Locale.US);
        return t;
    }

    static GradientDrawable round(int fill, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** A lapis-800 card with a soft line, radius 18. */
    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        GradientDrawable bg = round(LAPIS_800, 18, c);
        bg.setStroke(dp(c, 1), LINE_SOFT);
        l.setBackground(bg);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** Pressable: a ripple over the given background. */
    static void pressable(View v, GradientDrawable bg, int ripple) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), bg, bg));
        v.setClickable(true);
        v.setFocusable(true);
    }

    static TextView primary(Context c, CharSequence s, View.OnClickListener click) {
        TextView b = text(c, s, LAPIS_900, 16, semibold);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 54));
        b.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        pressable(b, round(GOLD_400, 27, c), 0x400c1b33);
        b.setOnClickListener(click);
        return b;
    }

    static TextView secondary(Context c, CharSequence s, View.OnClickListener click) {
        TextView b = text(c, s, PLASTER, 15, medium);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 50));
        b.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        GradientDrawable bg = round(0x00000000, 25, c);
        bg.setStroke(dp(c, 1), LINE_SOFT);
        pressable(b, bg, 0x33efe7d6);
        b.setOnClickListener(click);
        return b;
    }

    /** A small rounded button inside a card: gold (the action) or outlined (the way out). */
    static TextView pill(Context c, CharSequence s, boolean gold, View.OnClickListener click) {
        TextView b = text(c, s, gold ? LAPIS_900 : PLASTER, 14, semibold);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 18), dp(c, 9), dp(c, 18), dp(c, 9));
        GradientDrawable bg = round(gold ? GOLD_400 : 0x00000000, 99, c);
        if (!gold) bg.setStroke(dp(c, 1), LINE_SOFT);
        pressable(b, bg, gold ? 0x400c1b33 : 0x33efe7d6);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(c, 8));
        b.setLayoutParams(lp);
        return b;
    }

    static void enabled(TextView primary, boolean on) {
        primary.setEnabled(on);
        primary.setAlpha(on ? 1f : 0.4f);
    }

    /** A pill: gold on a gold tint (accent), or mist on lapis-700 (neutral). */
    static TextView chip(Context c, CharSequence s, boolean accent) {
        TextView t = text(c, s, accent ? GOLD_400 : PLASTER, 12, medium);
        t.setBackground(round(accent ? GOLD_SOFT : LAPIS_700, 99, c));
        t.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** A TV menu path, as a chip the user can match against the screen. */
    static TextView path(Context c, CharSequence s) {
        TextView t = text(c, s, GOLD_200, 13, regular);
        t.setBackground(round(LAPIS_700, 8, c));
        t.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        return t;
    }

    /**
     * Something written on the TV, in both languages, since the TV's language isn't known yet: the phone's
     * language first, the other under it. Each line keeps its own direction.
     */
    static LinearLayout tvText(Context c, int en, int ar) {
        boolean phoneAr = arabic(c);
        LinearLayout box = column(c);
        box.setBackground(round(LAPIS_700, 10, c));
        box.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        int[] order = phoneAr ? new int[] {ar, en} : new int[] {en, ar};
        for (int i = 0; i < 2; i++) {
            boolean lineAr = order[i] == ar;
            TextView t = text(c, c.getString(order[i]), i == 0 ? GOLD_200 : MIST, i == 0 ? 14 : 12.5f,
                    i == 0 ? medium : regular);
            t.setTextDirection(lineAr ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
            t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            box.addView(t, wide(c, i == 0 ? 0 : 4));
        }
        return box;
    }

    /** A gold disc with a number, for numbered steps. */
    static TextView badge(Context c, String number) {
        TextView t = text(c, number, LAPIS_900, 14, semibold);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(GOLD_400);
        t.setBackground(g);
        int s = dp(c, 28);
        t.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return t;
    }

    static LinearLayout.LayoutParams wide(Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    static LinearLayout.LayoutParams fill(Context c) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }
}
