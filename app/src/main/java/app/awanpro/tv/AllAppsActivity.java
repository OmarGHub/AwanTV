package app.awanpro.tv;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * All apps (design/afnon-mihrab/design/AllApps.dc.html): five columns, three rows in view, paging a row at a time. The info bar
 * names the focused app and its place in favourites. Hold OK arranges favourites: a favourite moves earlier or
 * later; any other app can take a favourite's place (Home always has six). The chip shows the next prayer and
 * opens Prayer day; Back returns Home.
 */
public class AllAppsActivity extends Screen {

    private static final int COLS = 5, VISIBLE_ROWS = 3;
    private static final float GRID_TOP = 200, TILE_H = 168, GAP = 32;
    private static final int BACK_BTN = -2, CHIP = -3, NONE = -1;

    private final List<FocusTile> tiles = new ArrayList<>();
    private List<String> apps = new ArrayList<>();
    private List<String> favs = new ArrayList<>();
    /** Home's More row, as it shows. */
    private List<String> more = new ArrayList<>();
    private FocusTile backButton, chip;
    private TextView chipLabel, chipTime, infoName, infoNote, count;
    private int topRow;
    private PrayerDay day;
    private long lastMinute = -1;
    /** The app to focus after a rebuild. */
    private String focusPkg;

    // Hold-OK handling and the arrange menu.
    private boolean okDown, okLong;
    private FrameLayout menu;
    private final List<FocusTile> menuItems = new ArrayList<>();
    private final List<Runnable> menuActions = new ArrayList<>();
    /** The OK release that ends the long press which opened the menu must not pick its first item. */
    private boolean swallowOkUp;
    /** The tile the menu was opened from, focused again when it closes without a change. */
    private int menuFrom;

    @Override
    void build() {
        newRoot();
        apps = Apps.sorted(this, t);
        favs = Apps.slots(this, t);
        more = Apps.more(this, t, Apps.MORE_SLOTS);
        tiles.clear();

        backButton = new FocusTile(this, 36);
        backButton.setRest(Tokens.LAPIS_800, Tokens.LINE_SOFT, 2);
        Icon back = new Icon(this, Icon.BACK, 2f);
        backButton.addView(back, new FrameLayout.LayoutParams(Ui.ipx(32), Ui.ipx(32), Gravity.CENTER));
        backButton.setLook(f -> back.setColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        backButton.setContentDescription(t.back());
        backButton.setOnClickListener(v -> finish());
        backButton.setOnFocusChangeListener((v, has) -> { if (has) info(t.back(), t.hintBackHome()); });
        placeTile(backButton, 96, 56, 72, 72);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(displayBold, 64, Tokens.PLASTER_100);
        title.setText(t.allTitle());
        count = text(ui400, 26, Tokens.MIST_400);
        count.setText(t.d(String.valueOf(apps.size())));
        head.addView(title);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.setMarginStart(Ui.ipx(28));
        head.addView(count, clp);
        // Amiri's tall letters reach above a 64px line box; let them draw past the 72px row instead of clipping.
        lineHeight(title, 1f);
        head.setClipChildren(false);
        head.setClipToPadding(false);
        place(head, 96 + 72 + 28, 56, ViewGroup.LayoutParams.WRAP_CONTENT, 72);

        chip = new FocusTile(this, 36);
        chip.setRest(0, Tokens.GOLD_400, 2);
        LinearLayout cr = new LinearLayout(this);
        cr.setGravity(Gravity.CENTER_VERTICAL);
        cr.setPadding(Ui.ipx(28), 0, Ui.ipx(28), 0);
        Icon mihrab = new Icon(this, Icon.MIHRAB, 2f);
        chipLabel = text(ui400, 26, Tokens.GOLD_400);
        chipTime = text(display, 32, Tokens.PLASTER_100);
        chipTime.setTextDirection(View.TEXT_DIRECTION_LTR);
        chipTime.setIncludeFontPadding(false);
        cr.addView(mihrab, new LinearLayout.LayoutParams(Ui.ipx(26), Ui.ipx(26)));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(-2, -2);
        lp1.setMarginStart(Ui.ipx(16));
        cr.addView(chipLabel, lp1);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-2, -2);
        lp2.setMarginStart(Ui.ipx(16));
        cr.addView(chipTime, lp2);
        chip.addView(cr, new FrameLayout.LayoutParams(-2, -1));
        chip.setLook(f -> {
            chipLabel.setTextColor(f ? Tokens.LAPIS_900 : Tokens.GOLD_400);
            chipTime.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100);
            mihrab.setColor(f ? Tokens.LAPIS_900 : Tokens.GOLD_400);
        });
        chip.setOnClickListener(v -> startActivity(new Intent(this, PrayerDayActivity.class)));
        chip.setOnFocusChangeListener((v, has) -> { if (has) info(t.today(), t.hintOk(t.today())); });
        placeEnd(chip, 96, 56, ViewGroup.LayoutParams.WRAP_CONTENT, 72);

        float w = (1728 - (COLS - 1) * GAP) / COLS;
        for (int i = 0; i < apps.size(); i++) {
            FocusTile tile = appTile(apps.get(i), i);
            tiles.add(tile);
            // A mouse pointer over an app focuses it, as the remote would.
            final int index = i;
            tile.setOnHoverListener((v, e) -> {
                if (e.getAction() == android.view.MotionEvent.ACTION_HOVER_ENTER && menu == null && !v.isFocused()) focusTile(index);
                return false;
            });
            placeTile(tile, 96 + (i % COLS) * (w + GAP), GRID_TOP + (i / COLS) * (TILE_H + GAP), w, TILE_H);
        }

        // Info bar: the focused app on the start side, key hints on the end side.
        FrameLayout bar = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tokens.LAPIS_800);
        bg.setCornerRadius(Ui.px(18));
        bar.setBackground(bg);
        bar.setPadding(Ui.ipx(32), Ui.ipx(24), Ui.ipx(32), Ui.ipx(24));
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        infoName = text(ui500, 30, Tokens.PLASTER_100);
        infoNote = text(ui400, 22, Tokens.MIST_400);
        who.addView(infoName);
        who.addView(infoNote);
        ((LinearLayout.LayoutParams) infoNote.getLayoutParams()).topMargin = Ui.ipx(6);
        bar.addView(who, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout keys = new LinearLayout(this);
        List<String> h = new ArrayList<>();
        h.add(t.hintOpen());
        h.add(t.hintHold());
        h.add(t.hintBackHome());
        for (int i = 0; i < h.size(); i++) {
            TextView k = text(ui400, 22, Tokens.MIST_400);
            k.setText(h.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.setMarginStart(Ui.ipx(36));
            keys.addView(k, lp);
        }
        bar.addView(keys, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        place(bar, 96, 830, 1728, ViewGroup.LayoutParams.WRAP_CONTENT);

        setContentView(root);
        lastMinute = -1;
        onTick();
        int start = focusPkg != null && apps.contains(focusPkg) ? apps.indexOf(focusPkg) : 0;
        focusPkg = null;
        topRow = 0;
        focusTile(start);
    }

    private FocusTile appTile(String pkg, int index) {
        FocusTile b = new FocusTile(this, Tokens.RADIUS_TILE);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.ipx(24), Ui.ipx(22), Ui.ipx(24), Ui.ipx(22));

        FrameLayout top = new FrameLayout(this);
        FrameLayout mono = new FrameLayout(this);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable d = Apps.icon(this, pkg);
        if (d != null) icon.setImageDrawable(d);
        mono.addView(icon, new FrameLayout.LayoutParams(Ui.ipx(40), Ui.ipx(40), Gravity.CENTER));
        top.addView(mono, new FrameLayout.LayoutParams(Ui.ipx(56), Ui.ipx(56), Gravity.START | Gravity.TOP));
        int favAt = favs.indexOf(pkg);
        Icon star = null;
        if (favAt >= 0) {
            star = new Icon(this, Icon.STAR, 2f);
            star.setContentDescription(t.favourite());
            top.addView(star, new FrameLayout.LayoutParams(Ui.ipx(26), Ui.ipx(26), Gravity.END | Gravity.TOP));
        }
        String name = Apps.name(this, t, pkg);
        TextView label = text(ui500, 26, Tokens.PLASTER_100);
        label.setText(name);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(top, new LinearLayout.LayoutParams(-1, Ui.ipx(56)));
        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        col.addView(label, new LinearLayout.LayoutParams(-1, -2));
        b.addView(col, new FrameLayout.LayoutParams(-1, -1));

        final Icon starIcon = star;
        b.setLook(f -> {
            int fg = f ? Tokens.LAPIS_900 : Tokens.PLASTER_100;
            label.setTextColor(fg);
            if (starIcon != null) starIcon.setColor(f ? Tokens.LAPIS_900 : Tokens.GOLD_400);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Ui.px(14));
            g.setColor(f ? Tokens.LAPIS_900 : Tokens.LAPIS_700);
            mono.setBackground(g);
        });
        b.setOnClickListener(v -> Apps.launch(this, pkg));
        int moreAt = more.indexOf(pkg);
        b.setOnFocusChangeListener((v, has) -> {
            if (has) info(name, favAt >= 0 ? t.favNote(favAt + 1) : moreAt >= 0 ? t.moreNote(moreAt + 1) : t.notFav());
        });
        return b;
    }

    private void info(String name, String note) {
        infoName.setText(name);
        infoName.setTextDirection(Apps.isLatin(name) ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        infoNote.setText(note);
    }

    // ---------------------------------------------------------------- live chip

    @Override
    void onTick() {
        Date now = new Date();
        long minute = now.getTime() / 60000;
        if (day == null || minute != lastMinute || secondsUntil(day.nextTime, now) == 0) {
            lastMinute = minute;
            boolean arrived = day != null && secondsUntil(day.nextTime, now) == 0;
            day = new PrayerDay(this, arrived ? new Date(now.getTime() + 1000) : now);
            String[] names = t.prayerNames();
            if (day.friday) names[PrayerDay.DHUHR] = t.jumuah();
            chipLabel.setText(t.chip(names[day.nextIndex]));
        }
        chipTime.setText(t.d(duration(secondsUntil(day.nextTime, now), false)));
    }

    // ---------------------------------------------------------------- focus and paging

    private void focusTile(int i) {
        if (i < 0 || i >= tiles.size()) return;
        int row = i / COLS;
        if (row < topRow) topRow = row;
        else if (row >= topRow + VISIBLE_ROWS) topRow = row - VISIBLE_ROWS + 1;
        float shift = -Ui.px(topRow * (TILE_H + GAP));
        for (int j = 0; j < tiles.size(); j++) {
            int r = j / COLS;
            FocusTile tile = tiles.get(j);
            tile.setTranslationY(shift);
            tile.setVisibility(r >= topRow && r < topRow + VISIBLE_ROWS ? View.VISIBLE : View.INVISIBLE);
        }
        tiles.get(i).requestFocus();
    }

    private int current() {
        View f = getCurrentFocus();
        if (f == backButton) return BACK_BTN;
        if (f == chip) return CHIP;
        return tiles.indexOf(f);
    }

    /** The mouse wheel moves a row up or down, as the remote's Up and Down do. */
    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        if (menu == null && e.getAction() == android.view.MotionEvent.ACTION_SCROLL) {
            float v = e.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL);
            int from = current();
            if (v != 0 && from >= 0) {
                int to = v < 0 ? Math.min(tiles.size() - 1, from + COLS) : from - COLS;
                if (to >= 0) focusTile(to);
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(e);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (menu != null) return menuKey(e);
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) finish();
            return true;
        }
        if (isOk(k)) return okKey(e);
        boolean nav = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
            || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!nav) return super.dispatchKeyEvent(e);
        if (!down) return true;
        // Written for LTR; in Arabic Left/Right swap.
        if (rtl && k == KeyEvent.KEYCODE_DPAD_LEFT) k = KeyEvent.KEYCODE_DPAD_RIGHT;
        else if (rtl && k == KeyEvent.KEYCODE_DPAD_RIGHT) k = KeyEvent.KEYCODE_DPAD_LEFT;
        int from = current();
        if (from == BACK_BTN) {
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) chip.requestFocus();
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) focusTile(topRow * COLS);
        } else if (from == CHIP) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) backButton.requestFocus();
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) focusTile(Math.min(tiles.size() - 1, topRow * COLS + COLS - 1));
        } else if (from == NONE) {
            focusTile(0);
        } else {
            int col = from % COLS;
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) focusTile(from - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && col < COLS - 1 && from + 1 < tiles.size()) focusTile(from + 1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN && from + COLS < tiles.size()) focusTile(from + COLS);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN && from / COLS < (tiles.size() - 1) / COLS) focusTile(tiles.size() - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_UP && from >= COLS) focusTile(from - COLS);
            else if (k == KeyEvent.KEYCODE_DPAD_UP) (col < 3 ? backButton : chip).requestFocus();
        }
        return true;
    }

    private static boolean isOk(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    /** A short press clicks; held past the long-press timeout on an app, it opens the arrange menu instead. */
    private boolean okKey(KeyEvent e) {
        int at = current();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                okDown = true;
                okLong = false;
            } else if (okDown && !okLong && e.isLongPress() && at >= 0) {
                okLong = true;
                openMenu(apps.get(at), true);
            }
            return true;
        }
        if (okDown && !okLong) {
            View f = getCurrentFocus();
            if (f != null) f.performClick();
        }
        okDown = false;
        return true;
    }

    // ---------------------------------------------------------------- arrange menu

    /**
     * The app's place on Home. In favourites or in More: earlier, later, or
     * to the other row (then a slot to pick); elsewhere: into either row (then a slot). Moving between the rows
     * swaps the two apps, so each keeps six.
     */
    private void openMenu(String pkg, boolean fromLongPress) {
        menuFrom = apps.indexOf(pkg);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        int fa = favs.indexOf(pkg), ma = more.indexOf(pkg);
        String sub;
        if (fa >= 0) {
            sub = t.favNote(fa + 1);
            if (fa > 0) { labels.add(t.moveEarlier()); actions.add(() -> swapIn(favs, true, fa, fa - 1, pkg)); }
            if (fa < favs.size() - 1) { labels.add(t.moveLater()); actions.add(() -> swapIn(favs, true, fa, fa + 1, pkg)); }
            if (!more.isEmpty()) { labels.add(t.moveToMore()); actions.add(() -> openSlots(pkg, false)); }
        } else if (ma >= 0) {
            sub = t.moreNote(ma + 1);
            if (ma > 0) { labels.add(t.moveEarlierMore()); actions.add(() -> swapIn(more, false, ma, ma - 1, pkg)); }
            if (ma < more.size() - 1) { labels.add(t.moveLaterMore()); actions.add(() -> swapIn(more, false, ma, ma + 1, pkg)); }
            labels.add(t.moveToFav()); actions.add(() -> openSlots(pkg, true));
        } else {
            sub = t.notOnHome();
            labels.add(t.putInFav()); actions.add(() -> openSlots(pkg, true));
            if (!more.isEmpty()) { labels.add(t.putInMore()); actions.add(() -> openSlots(pkg, false)); }
        }
        labels.add(t.cancel());
        actions.add(null);
        showMenu(pkg, sub, labels, actions, fromLongPress);
    }

    /** The six places of a row; the chosen one takes the app. The last item goes back to the first menu. */
    private void openSlots(String pkg, boolean toFav) {
        List<String> row = toFav ? favs : more;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (int j = 0; j < row.size(); j++) {
            final int slot = j;
            labels.add(t.inPlaceOf(Apps.name(this, t, row.get(j))));
            actions.add(() -> place(pkg, toFav, slot));
        }
        labels.add(t.back());
        actions.add(() -> openMenu(pkg, false));
        showMenu(pkg, toFav ? t.chooseFavSlot() : t.chooseMoreSlot(), labels, actions, false);
    }

    private void showMenu(String pkg, String sub, List<String> labels, List<Runnable> actions, boolean fromLongPress) {
        menuItems.clear();
        menuActions.clear();
        menuActions.addAll(actions);
        menu = new FrameLayout(this);
        menu.setBackgroundColor(Tokens.SCRIM_MENU);
        menu.setClickable(true);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(false);
        // The focused item's ring reaches past the card's padding.
        card.setClipToPadding(false);
        card.setPadding(Ui.ipx(48), Ui.ipx(40), Ui.ipx(48), Ui.ipx(40));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tokens.LAPIS_800);
        bg.setCornerRadius(Ui.px(24));
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
        card.setBackground(bg);
        TextView title = text(displayBold, 48, Tokens.GOLD_400);
        String name = Apps.name(this, t, pkg);
        title.setText(name);
        title.setTextDirection(Apps.isLatin(name) ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        card.addView(title);
        lineHeight(title, 1.2f);
        TextView where = text(ui400, 20, Tokens.MIST_400);
        where.setText(sub);
        card.addView(where);
        for (int i = 0; i < labels.size(); i++) {
            FocusTile item = new FocusTile(this, 36);
            item.setFocusScale(1.04f);
            item.setRest(Tokens.LAPIS_700, 0, 0);
            TextView l = text(ui400, 26, Tokens.PLASTER_100);
            l.setText(labels.get(i));
            l.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            l.setPadding(Ui.ipx(32), 0, Ui.ipx(32), 0);
            item.addView(l, new FrameLayout.LayoutParams(-1, -1));
            item.setLook(f -> l.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
            float r = FocusTile.REACH;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.ipx(72 + 2 * r));
            lp.topMargin = Ui.ipx(i == 0 ? 24 - r : 12 - 2 * r);
            card.addView(item, lp);
            menuItems.add(item);
        }
        menu.addView(card, new FrameLayout.LayoutParams(Ui.ipx(820), -2, Gravity.CENTER));
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        // Opened by a long press, the release of that press must not pick the first item; a second menu opened by
        // a release has none still to come.
        swallowOkUp = fromLongPress;
        menuItems.get(0).requestFocus();
    }

    private boolean menuKey(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) closeMenu(-1);
            return true;
        }
        if (isOk(k)) {
            if (down) return true;
            if (swallowOkUp) { swallowOkUp = false; okDown = false; return true; }
            closeMenu(menuItems.indexOf(getCurrentFocus()));
            return true;
        }
        if (down && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
            int i = menuItems.indexOf(getCurrentFocus());
            int to = Math.max(0, Math.min(menuItems.size() - 1, i + (k == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)));
            menuItems.get(to).requestFocus();
        }
        return true;
    }

    /** Runs the chosen action (the last item is Cancel), or just returns focus to the tile. */
    private void closeMenu(int choice) {
        root.removeView(menu);
        menu = null;
        Runnable a = choice >= 0 && choice < menuActions.size() ? menuActions.get(choice) : null;
        if (a != null) a.run();
        else focusTile(menuFrom);
    }

    /** Two places of one row swap. */
    private void swapIn(List<String> row, boolean isFav, int a, int b, String pkg) {
        List<String> r = new ArrayList<>(row);
        String x = r.get(a);
        r.set(a, r.get(b));
        r.set(b, x);
        if (isFav) Apps.saveFavourites(this, r);
        else Apps.saveMore(this, r);
        focusPkg = pkg;
        build();
    }

    /**
     * The app takes a place in a row. Coming from the other row, the two swap; from neither, the one there leaves
     * Home. Both rows are saved as they show, so More keeps what it had.
     */
    private void place(String pkg, boolean toFav, int slot) {
        List<String> f = new ArrayList<>(favs), m = new ArrayList<>(more);
        List<String> dst = toFav ? f : m, src = toFav ? m : f;
        String out = dst.get(slot);
        int from = src.indexOf(pkg);
        dst.set(slot, pkg);
        if (from >= 0) src.set(from, out);
        Apps.saveFavourites(this, f);
        Apps.saveMore(this, m);
        focusPkg = pkg;
        build();
    }
}
