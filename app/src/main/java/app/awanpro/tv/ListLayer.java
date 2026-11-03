package app.awanpro.tv;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.IntConsumer;

/**
 * A list over a screen that has no chooser of its own (Home's radio card: Station), drawn as Settings' chooser is:
 * a dimmed layer, a lapis card with its gold title, items 68 high that scroll by whole items, the chosen one marked.
 * The screen adds {@link #layer} to its root, focuses {@link #start}, and handles Back itself.
 */
final class ListLayer {
    final FrameLayout layer;
    /** The item focus starts on: the chosen one, else the first. */
    FocusTile start;

    ListLayer(Screen s, String title, String[] items, int selected, IntConsumer pick) {
        layer = new FrameLayout(s);
        layer.setBackgroundColor(Tokens.SCRIM_MENU);
        layer.setClickable(true);

        LinearLayout card = new LinearLayout(s);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.ipx(48), Ui.ipx(40), Ui.ipx(48), Ui.ipx(32));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tokens.LAPIS_800);
        bg.setCornerRadius(Ui.px(24));
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
        card.setBackground(bg);
        card.setClipChildren(true);

        TextView head = s.text(s.displayBold, Tokens.FONT_SIZE_DISPLAY_S, Tokens.GOLD_400);
        head.setText(title);
        card.addView(head);
        s.lineHeight(head, 1.2f);

        ScrollView scroll = new ScrollView(s);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(false);
        int reach = Ui.ipx(FocusTile.REACH);
        // Padding at the sides only, each item holding its ring inside (as Settings' chooser, Afnon 0.8).
        scroll.setPadding(reach, 0, reach, 0);
        scroll.setClipToPadding(true);
        scroll.setClipChildren(true);
        int itemH = Ui.ipx(68 + 2 * FocusTile.REACH), gap = Ui.ipx(6 - 2 * FocusTile.REACH);
        LinearLayout list = new LinearLayout(s);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            boolean chosen = i == selected;
            FocusTile item = new FocusTile(s, 14);
            item.setFocusScale(1f);
            item.setRest(chosen ? Tokens.LAPIS_700 : 0, 0, 0);
            TextView v = s.text(chosen ? s.ui500 : s.ui400, 26, Tokens.PLASTER_100);
            v.setText(items[i]);
            v.setSingleLine(true);
            v.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            v.setPadding(Ui.ipx(24), 0, Ui.ipx(24), 0);
            item.addView(v, new FrameLayout.LayoutParams(-1, -1));
            item.setLook(f -> v.setTextColor(f ? Tokens.LAPIS_900 : (chosen ? Tokens.GOLD_400 : Tokens.PLASTER_100)));
            item.setOnClickListener(x -> pick.accept(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, itemH);
            if (i > 0) lp.topMargin = gap;
            list.addView(item, lp);
            if (chosen || start == null) start = item;
        }
        scroll.addView(list);
        int shown = Math.max(1, Math.min(items.length, 8));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, shown * itemH + (shown - 1) * gap);
        slp.topMargin = Ui.ipx(20);
        slp.setMarginStart(-reach);
        slp.setMarginEnd(-reach);
        card.addView(scroll, slp);

        layer.addView(card, new FrameLayout.LayoutParams(Ui.ipx(900), -2, Gravity.CENTER));
    }
}
