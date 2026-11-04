package app.awanpro.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The safety net's screen (Crash, 1.13.0), in its own process (":crash"), so the error that ended Awan TV cannot end
 * it too: Restart (Home again), the TV's own home (Home held off for half an hour), Details to report (the lines and a
 * code to the project's issues). After a second stop within two minutes it says Home is held, the TV's own home
 * first. With no key for ten minutes it closes, so nothing stays still on the screen. Built from the plainest parts
 * (no Screen, no timer of the app's), each of which may fail without taking the screen down.
 */
public class CrashActivity extends Activity {
    static final String REPEAT = "repeat";
    private static final long CLOSE_MS = 10 * 60_000L;
    private static final String ISSUES = "https://github.com/OmarGHub/AwanTV/issues/new";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable close = this::finish;
    private Text t;
    private Typeface ui400, ui500, title;
    private FrameLayout root;
    private boolean repeat;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        try {
            Tokens.apply(this);
        } catch (RuntimeException ignored) {
            // The default colours.
        }
        try {
            t = Screen.loadText(this);
        } catch (RuntimeException e) {
            boolean ar = "ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
            t = new Text(ar, ar);
        }
        try {
            ui400 = getResources().getFont(R.font.alexandria_regular);
            ui500 = getResources().getFont(R.font.alexandria_medium);
            title = getResources().getFont(t.ar ? R.font.amiri_bold : R.font.amiri_regular);
        } catch (RuntimeException e) {
            ui400 = ui500 = title = Typeface.DEFAULT;
        }
        repeat = getIntent().getBooleanExtra(REPEAT, false);
        root = new FrameLayout(this);
        root.setBackgroundColor(Tokens.LAPIS_900);
        root.setLayoutDirection(t.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        setContentView(root);
        showMain();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(close);
        handler.postDelayed(close, CLOSE_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(close);
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // A key: ten more minutes.
        handler.removeCallbacks(close);
        handler.postDelayed(close, CLOSE_MS);
        return super.dispatchKeyEvent(e);
    }

    /** The first screen: what happened and the three ways on; after a repeat, Home held and the TV's own home first. */
    private void showMain() {
        boolean stock = HomeGuard.stockHome(this, new java.util.HashSet<>()) != null;
        List<FocusTile> buttons = new ArrayList<>();
        FocusTile restart = button(t.crashRestart(), this::restart);
        FocusTile own = stock ? button(t.crashStock(), this::ownHome) : null;
        if (repeat && own != null) buttons.add(own);
        buttons.add(restart);
        if (!repeat && own != null) buttons.add(own);
        buttons.add(button(t.crashDetails(), this::showDetails));
        LinearLayout words = column();
        words.addView(titleView(repeat ? t.crashAgainTitle() : t.crashTitle()));
        words.addView(line(repeat ? t.crashAgainLead() : t.crashLead(), Tokens.PLASTER_100, 26, 10));
        words.addView(line(repeat ? t.crashAgainHow() : t.crashHow(), Tokens.MIST_400, 24, 12));
        words.addView(line(t.crashCloses(), Tokens.MIST_400, 20, 12));
        words.addView(row(buttons), buttonsParams());
        show(words, null);
        buttons.get(0).requestFocus();
    }

    /** The lines to report and a code to the project's new issue page. */
    private void showDetails() {
        String[] last = Crash.last(this);
        LinearLayout words = column();
        words.addView(titleView(t.crashDetailsTitle()));
        words.addView(line(t.crashDetailsLead(), Tokens.PLASTER_100, 24, 10));
        TextView lines = new TextView(this);
        lines.setText(last == null ? "-" : last[1]);
        lines.setTypeface(Typeface.MONOSPACE);
        lines.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(18));
        lines.setTextColor(Tokens.PLASTER_100);
        lines.setTextDirection(View.TEXT_DIRECTION_LTR);
        lines.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        lines.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        lines.setLineSpacing(0, 1.3f);
        lines.setMaxLines(12);
        lines.setBackground(round(Tokens.LAPIS_900, 10));
        int pad = Ui.ipx(18);
        lines.setPadding(pad, Ui.ipx(14), pad, Ui.ipx(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.ipx(16);
        words.addView(lines, lp);
        List<FocusTile> buttons = new ArrayList<>();
        buttons.add(button(t.back(), this::showMain));
        words.addView(row(buttons), buttonsParams());
        View code = null;
        try {
            code = UpdateNotice.qrTile(this, R.drawable.qr_issue, UpdateNotice.archLogo(this), ISSUES);
        } catch (RuntimeException ignored) {
            // The lines alone.
        }
        show(words, code);
        buttons.get(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    /** Restart: Home held no more, opened anew in Awan TV's own process. */
    private void restart() {
        Crash.release(this);
        try {
            startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } catch (RuntimeException ignored) {
            // Nothing to open: the TV's own home comes up as this closes.
        }
        finish();
    }

    /** The TV's own home, left up for half an hour (HomeGuard would otherwise cover it with Home at once). */
    private void ownHome() {
        Crash.hold(this);
        HomeGuard.openStock(this);
        finish();
    }

    // ---------------------------------------------------------------- the card, as Update now's window

    private void show(LinearLayout words, View side) {
        root.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setPadding(Ui.ipx(48), Ui.ipx(40), Ui.ipx(48), Ui.ipx(32));
        GradientDrawable bg = round(Tokens.LAPIS_800, 24);
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
        card.setBackground(bg);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        if (side != null) {
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.setMarginStart(Ui.ipx(40));
            card.addView(side, sp);
        }
        root.addView(card, new FrameLayout.LayoutParams(Ui.ipx(1300), -2, Gravity.CENTER));
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setClipChildren(false);
        v.setClipToPadding(false);
        return v;
    }

    private TextView titleView(String text) {
        TextView v = new TextView(this);
        v.setTypeface(title);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(Tokens.FONT_SIZE_DISPLAY_S));
        v.setTextColor(Tokens.GOLD_400);
        v.setText(text);
        v.setTextDirection(t.ar ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        return v;
    }

    private TextView line(String text, int color, float size, float top) {
        TextView v = new TextView(this);
        v.setTypeface(ui400);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(size));
        v.setTextColor(color);
        v.setText(text);
        v.setTextDirection(t.ar ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        v.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.ipx(top);
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout row(List<FocusTile> buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        float r = FocusTile.REACH;
        for (int i = 0; i < buttons.size(); i++) {
            LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(-2, Ui.ipx(68 + 2 * r));
            if (i > 0) b.setMarginStart(Ui.ipx(24 - 2 * r));
            row.addView(buttons.get(i), b);
        }
        return row;
    }

    private LinearLayout.LayoutParams buttonsParams() {
        float r = FocusTile.REACH;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.ipx(28 - r);
        lp.setMarginStart(-Ui.ipx(r));
        return lp;
    }

    private FocusTile button(String label, Runnable run) {
        FocusTile b = new FocusTile(this, 14);
        b.setRest(Tokens.LAPIS_700, 0, 0);
        TextView v = new TextView(this);
        v.setTypeface(ui500);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(26));
        v.setTextColor(Tokens.PLASTER_100);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        int pad = Ui.ipx(36 + FocusTile.REACH);
        v.setPadding(pad, 0, pad, 0);
        b.addView(v, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> v.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        b.setOnClickListener(x -> run.run());
        return b;
    }

    private static GradientDrawable round(int color, float radiusDesignPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(Ui.px(radiusDesignPx));
        return d;
    }
}
