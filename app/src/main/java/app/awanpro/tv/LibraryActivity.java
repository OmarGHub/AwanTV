package app.awanpro.tv;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings > Radio > Saved recitations: recitations saved on the TV to listen without
 * internet ({@link Library}).
 *
 * <p>The start side lists mp3quran.net's reciters (each recording on its own row, those already saved first), with
 * a search. The end side shows the focused one: download what is missing, delete what is saved, the download in
 * progress, and its surahs, where OK downloads a surah, cancels it while queued, or deletes it (OK twice). Above,
 * the space saved and the space left; 2 GB are always left for the TV. Saved recordings play from Radio > Station >
 * Saved on the TV.
 */
public class LibraryActivity extends Screen {

    private static final float LIST_W = 640, PANEL_W = 1000, CHIP_H = 58;
    private static final int COLUMNS = 5;
    private static final long DELETE_WINDOW_MS = 3000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<Library.Reciter> all = new ArrayList<>();
    private String[] surahAr = new String[115], surahEn = new String[115];
    /** In the screen's language when it is neither Arabic nor English (mp3quran.net's list); English where missing. */
    private String[] surahOwn = new String[115];

    private EditText search;
    private LinearLayout list;
    private ScrollView listScroll;
    private TextView listNote, storageSaved, storageFree, panelName, panelSub, panelProgress;
    private View storageFill;
    private FocusTile getAll, deleteAll;
    private TextView getAllLabel;
    private GridLayout grid;
    private final TextView[] chipName = new TextView[115], chipState = new TextView[115];
    private final FocusTile[] chip = new FocusTile[115];
    /** The recording on the end side. */
    private Library.Reciter shown;
    /** A saved surah OK was pressed on once: a second OK within 3 s deletes it. */
    private int deleteAsk = -1;
    private long deleteAskAt;
    private long lastRefresh;
    private float chipCell;
    private TextView legend, panelEmpty;

    @Override
    void build() {
        newRoot();
        root.setBackgroundColor(Tokens.LAPIS_900);

        TextView title = text(displayBold, 52, Tokens.PLASTER_100);
        title.setText(t.libraryRow());
        place(title, 96, 48, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Amiri's tall line box would push the title down onto the line under it.
        lineHeight(title, 1.1f);
        TextView intro = text(ui400, 24, Tokens.MIST_400);
        intro.setText(t.libraryIntro());
        place(intro, 96, 132, 1300, ViewGroup.LayoutParams.WRAP_CONTENT);

        // ---- the storage, at the end side of the title
        storageSaved = text(ui400, 20, Tokens.MIST_400);
        placeEnd(storageSaved, 96 + 210, 64, 210, ViewGroup.LayoutParams.WRAP_CONTENT);
        storageFree = text(ui400, 20, Tokens.MIST_400);
        storageFree.setGravity(Gravity.END);
        placeEnd(storageFree, 96, 64, 210, ViewGroup.LayoutParams.WRAP_CONTENT);
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(box(Tokens.LAPIS_800, 4));
        storageFill = new View(this);
        storageFill.setBackground(box(Tokens.GOLD_400, 4));
        bar.addView(storageFill, new FrameLayout.LayoutParams(0, -1, Gravity.START));
        placeEnd(bar, 96, 100, 420, 8);
        TextView reserve = text(ui400, 18, Tokens.MIST_400);
        reserve.setAlpha(0.75f);
        reserve.setText(t.reserveNote());
        placeEnd(reserve, 96, 118, 420, ViewGroup.LayoutParams.WRAP_CONTENT);

        // ---- the start side: search and reciters
        search = new EditText(this);
        search.setTypeface(ui400);
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(24));
        search.setTextColor(Tokens.PLASTER_100);
        search.setHintTextColor(Tokens.MIST_400);
        search.setHint(t.searchReciter());
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        search.setPaddingRelative(Ui.ipx(64), 0, Ui.ipx(22), 0);
        StateListDrawable sb = new StateListDrawable();
        GradientDrawable sf = box(Tokens.LAPIS_800, 16);
        sf.setStroke(Ui.ipx(3), Tokens.GOLD_400);
        sb.addState(new int[] {android.R.attr.state_focused}, sf);
        GradientDrawable sr = box(Tokens.LAPIS_800, 16);
        sr.setStroke(Ui.ipx(2), Tokens.LINE_SOFT);
        sb.addState(new int[0], sr);
        search.setBackground(sb);
        place(search, 96, 210, LIST_W, 72);
        Icon glass = new Icon(this, Icon.SEARCH, 2f);
        glass.setColor(Tokens.MIST_400);
        place(glass, 96 + 22, 210 + 23, 26, 26);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { fillList(); }
        });

        float reach = FocusTile.REACH;
        listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        listScroll.setClipToPadding(true);
        int r = Ui.ipx(reach);
        listScroll.setPadding(r, r, r, r);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        listScroll.addView(list);
        place(listScroll, 96 - reach, 300 - reach, LIST_W + 2 * reach, 640 + 2 * reach);
        listNote = text(ui400, 22, Tokens.MIST_400);
        place(listNote, 96, 310, LIST_W, ViewGroup.LayoutParams.WRAP_CONTENT);

        // ---- the end side: the recording in focus
        View panel = new View(this);
        panel.setBackground(box(Tokens.LAPIS_800, 24));
        placeEnd(panel, 96, 210, PANEL_W, 720);
        float in = 96 + 36, inW = PANEL_W - 72;
        panelName = text(displayBold, 40, Tokens.PLASTER_100);
        panelName.setSingleLine(true);
        panelName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        placeEnd(panelName, in + 580, 236, inW - 580, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelSub = text(ui400, 20, Tokens.MIST_400);
        panelSub.setSingleLine(true);
        panelSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        placeEnd(panelSub, in + 580, 300, inW - 580, ViewGroup.LayoutParams.WRAP_CONTENT);
        getAllLabel = text(ui500, 22, Tokens.LAPIS_900);
        getAll = pill(getAllLabel, true);
        getAll.setOnClickListener(v -> downloadRest());
        placeEnd(getAll, in + 200, 242, 360, 64);
        TextView delLabel = text(ui400, 22, Tokens.PLASTER_100);
        delLabel.setText(t.deleteSaved());
        deleteAll = pill(delLabel, false);
        deleteAll.setOnClickListener(v -> deleteSaved());
        placeEnd(deleteAll, in, 242, 180, 64);
        panelProgress = text(ui400, 21, Tokens.GOLD_400);
        panelProgress.setSingleLine(true);
        placeEnd(panelProgress, in, 340, inW, ViewGroup.LayoutParams.WRAP_CONTENT);

        ScrollView gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(false);
        gridScroll.setPadding(r, r, r, r);
        gridScroll.setClipToPadding(true);
        grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        chipCell = inW / COLUMNS;
        for (int s = 1; s <= 114; s++) chip(s);
        gridScroll.addView(grid);
        placeEnd(gridScroll, in - reach, 382 - reach, inW + 2 * reach, 470 + 2 * reach);
        legend = text(ui400, 19, Tokens.MIST_400);
        legend.setText(t.libraryLegend());
        placeEnd(legend, in, 870, inW, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Until a reciter is focused, the panel says what it is for.
        panelEmpty = text(ui400, 26, Tokens.MIST_400);
        panelEmpty.setText(t.pickReciter());
        panelEmpty.setGravity(Gravity.CENTER);
        placeEnd(panelEmpty, 96, 540, PANEL_W, ViewGroup.LayoutParams.WRAP_CONTENT);

        LinearLayout hints = new LinearLayout(this);
        place(hints, 96, 976, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        List<String> h = new ArrayList<>();
        h.add(t.hintOk(t.hintChooseWord()));
        h.add(t.hintToSurahs());
        h.add(t.hintBackSettings());
        setHints(hints, h);
        TextView credit = text(ui400, 18, Tokens.MIST_400);
        credit.setAlpha(0.7f);
        credit.setText(t.recitationsCredit());
        placeEnd(credit, 96, 980, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        setContentView(root);
        showPanel(null);
        refreshStorage();
        listNote.setText(t.loadingReciters());
        load(false);
    }

    @Override
    void onTick() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < 2000) return;
        lastRefresh = now;
        refreshStorage();
        if (shown != null) refreshPanel();
        if (deleteAsk > 0 && now - deleteAskAt > DELETE_WINDOW_MS) {
            deleteAsk = -1;
            if (shown != null) refreshPanel();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Library.pump(this);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- loading

    private void load(boolean fresh) {
        worker.execute(() -> {
            List<Library.Reciter> got;
            String[] ar = Library.surahNames(this, true), en = Library.surahNames(this, false);
            String[] own = t.ar || "en".equals(t.lang) ? new String[115] : Library.surahNames(this, t.lang);
            try {
                got = Library.reciters(this, fresh);
            } catch (Exception e) {
                got = null;
            }
            List<Library.Reciter> result = got;
            main.post(() -> {
                surahAr = ar;
                surahEn = en;
                surahOwn = own;
                for (int s = 1; s <= 114; s++) chipName[s].setText(surahLabel(s));
                if (result == null) {
                    listNote.setText(t.recitersOffline());
                    return;
                }
                all = result;
                fillList();
            });
        });
    }

    /** The reciters for what is typed in the search: saved ones first, then the list's own order. */
    private void fillList() {
        list.removeAllViews();
        String q = Places.normalise(search.getText().toString());
        Set<String> saved = new HashSet<>(Library.savedKeys(this));
        List<Library.Reciter> first = new ArrayList<>(), rest = new ArrayList<>();
        for (Library.Reciter r : all) {
            if (!q.isEmpty() && !Places.normalise(r.ar + " " + r.en).contains(q)) continue;
            (saved.contains(r.key()) ? first : rest).add(r);
        }
        first.addAll(rest);
        // A long list is built 80 at a time as focus reaches its end, so the screen opens at once.
        addRows(first, 0);
        listNote.setText(all.isEmpty() ? t.loadingReciters() : first.isEmpty() ? t.noReciter() : "");
        listNote.setVisibility(listNote.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void addRows(List<Library.Reciter> rows, int from) {
        int to = Math.min(rows.size(), from + 80);
        for (int i = from; i < to; i++) {
            FocusTile tile = reciterRow(rows.get(i));
            if (i == to - 1 && to < rows.size()) {
                Library.Reciter last = rows.get(i);
                tile.setOnFocusChangeListener((v, has) -> {
                    if (!has) return;
                    // The next rows once, then back to the row's own job: showing its recording.
                    v.setOnFocusChangeListener((v2, h2) -> { if (h2) showPanel(last); });
                    addRows(rows, to);
                    showPanel(last);
                });
            }
        }
    }

    private FocusTile reciterRow(Library.Reciter r) {
        FocusTile b = new FocusTile(this, 16);
        b.setFocusScale(1f);
        b.setRest(Tokens.LAPIS_800, 0, 0);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.ipx(24), 0, Ui.ipx(24), 0);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView n = text(ui400, 26, Tokens.PLASTER_100);
        n.setSingleLine(true);
        n.setText(r.name(t));
        TextView s = text(ui400, 19, Tokens.MIST_400);
        s.setSingleLine(true);
        s.setText(r.moshaf(t));
        words.addView(n);
        words.addView(s);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = text(ui400, 20, Tokens.GOLD_400);
        int have = Library.savedSurahs(this, r.key()).length;
        state.setText(have == 0 ? "" : have >= r.surahs.length ? t.complete() : t.d(t.someOf(have, r.surahs.length)));
        row.addView(state);
        b.addView(row, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> {
            n.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100);
            s.setTextColor(f ? Tokens.LAPIS_900 : Tokens.MIST_400);
            state.setTextColor(f ? Tokens.LAPIS_900 : Tokens.GOLD_400);
        });
        b.setOnFocusChangeListener((v, has) -> { if (has) showPanel(r); });
        // OK goes to the recording's first button.
        b.setOnClickListener(v -> (getAll.getVisibility() == View.VISIBLE ? getAll : chip[r.surahs[0]]).requestFocus());
        float reach = FocusTile.REACH;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.ipx(86 + 2 * reach));
        if (list.getChildCount() > 0) lp.topMargin = Ui.ipx(10 - 2 * reach);
        list.addView(b, lp);
        return b;
    }

    // ---------------------------------------------------------------- the recording in focus

    private void showPanel(Library.Reciter r) {
        if (r != shown) deleteAsk = -1;
        shown = r;
        int visible = r == null ? View.INVISIBLE : View.VISIBLE;
        for (View v : new View[] {panelName, panelSub, getAll, deleteAll, panelProgress, grid, legend}) v.setVisibility(visible);
        panelEmpty.setVisibility(r == null ? View.VISIBLE : View.GONE);
        if (r == null) return;
        panelName.setText(r.name(t));
        panelSub.setText(t.d(r.moshaf(t) + t.sep() + t.surahCount(r.surahs.length)));
        // Only the recording's own surahs, in order, so a partial one has no gaps.
        grid.removeAllViews();
        for (int s : r.surahs) {
            chip[s].setVisibility(View.VISIBLE);
            grid.addView(chip[s], chipParams(chipCell));
        }
        refreshPanel();
    }

    /** The buttons, the line about the download, and each surah's state. */
    private void refreshPanel() {
        Library.Reciter r = shown;
        int[] saved = Library.savedSurahs(this, r.key());
        Set<Integer> have = new HashSet<>();
        for (int s : saved) have.add(s);
        List<String> queue = Library.queue(this);
        String current = Library.current(this);
        int missing = 0;
        for (int s : r.surahs) if (!have.contains(s)) missing++;
        int waiting = 0;
        for (String q : queue) if (q.startsWith(r.key() + "/")) waiting++;
        int pending = waiting + (current != null && current.startsWith(r.key() + "/") ? 1 : 0);

        boolean more = missing > pending;
        // The button about to go has focus (Download all, just pressed): focus moves on to the surahs first.
        if (!more && getAll.isFocused()) {
            View next = deleteAll.getVisibility() == View.VISIBLE ? deleteAll : chip[r.surahs[0]];
            next.requestFocus();
        }
        getAll.setVisibility(more ? View.VISIBLE : View.GONE);
        getAllLabel.setText(t.d(saved.length == 0 ? t.downloadAll(missing - pending) : t.downloadRest(missing - pending)));
        deleteAll.setVisibility(saved.length > 0 || pending > 0 ? View.VISIBLE : View.GONE);

        String line = "";
        if (Library.cannotDownload(this) && pending > 0) {
            line = t.noDownloads();
        } else if (Library.waitingForSpace(this) && pending > 0) {
            line = t.waitingSpace();
        } else if (current != null && current.startsWith(r.key() + "/")) {
            int s = Integer.parseInt(current.substring(current.indexOf('/') + 1));
            int pct = Library.progress(this);
            line = t.d(t.downloadingNow(surahName(s), pct, waiting));
        } else if (waiting > 0) {
            line = t.d(t.queuedOnly(waiting));
        } else if (missing == 0) {
            line = t.allSaved();
        }
        panelProgress.setText(line);

        int pct = Library.progress(this);
        for (int s : r.surahs) {
            String item = r.key() + "/" + s;
            String mark;
            int color = Tokens.GOLD_400;
            if (s == deleteAsk) {
                mark = t.deleteAsk();
            } else if (have.contains(s)) {
                mark = "✓";
            } else if (item.equals(current)) {
                mark = pct >= 0 ? t.percentDone(pct) : "↓";
            } else if (queue.contains(item)) {
                mark = "↓";
                color = Tokens.MIST_400;
            } else {
                mark = "";
            }
            chipState[s].setText(mark);
            chipState[s].setTag(color);
            if (!chip[s].isFocused()) chipState[s].setTextColor(color);
        }
    }

    private FocusTile chip(int s) {
        FocusTile b = new FocusTile(this, 12);
        b.setFocusScale(1f);
        b.setRest(Tokens.LAPIS_900, 0, 0);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.ipx(16), 0, Ui.ipx(16), 0);
        TextView n = text(ui400, 21, Tokens.PLASTER_100);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setText(surahLabel(s));
        TextView state = text(ui400, 20, Tokens.GOLD_400);
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(state);
        b.addView(row, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> {
            n.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100);
            Object c = state.getTag();
            state.setTextColor(f ? Tokens.LAPIS_900 : c instanceof Integer ? (Integer) c : Tokens.GOLD_400);
        });
        b.setOnClickListener(v -> onSurah(s));
        b.setVisibility(View.GONE);
        chip[s] = b;
        chipName[s] = n;
        chipState[s] = state;
        return b;
    }

    /** A chip's cell: a fifth of the grid; the tile draws its body inside the ring's reach, which makes the gaps. */
    private GridLayout.LayoutParams chipParams(float cell) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = Ui.ipx(cell);
        lp.height = Ui.ipx(CHIP_H + 2 * FocusTile.REACH);
        return lp;
    }

    /** Android's own question about files saved before a reinstall (Library.askToDelete): its answer redraws. */
    private static final int DELETE_ASKED = 51;

    @Override
    protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != DELETE_ASKED) return;
        Library.savedChanged();
        fillList();
        refreshPanel();
        refreshStorage();
    }

    /** OK on a surah: download it; cancel it while waiting or downloading; on a saved one, OK twice deletes it. */
    private void onSurah(int s) {
        Library.Reciter r = shown;
        if (r == null) return;
        String item = r.key() + "/" + s;
        if (Library.saved(this, r.key(), s)) {
            if (deleteAsk == s && System.currentTimeMillis() - deleteAskAt < DELETE_WINDOW_MS) {
                deleteAsk = -1;
                Library.askToDelete(this, Library.delete(this, r.key(), s), DELETE_ASKED);
            } else {
                deleteAsk = s;
                deleteAskAt = System.currentTimeMillis();
            }
        } else if (item.equals(Library.current(this)) || Library.queue(this).contains(item)) {
            Library.delete(this, r.key(), s);
        } else if (!enoughSpace()) {
            return;
        } else {
            Library.enqueue(this, r, new int[] {s});
        }
        refreshPanel();
        refreshStorage();
    }

    private void downloadRest() {
        if (shown == null || !enoughSpace()) return;
        Library.enqueue(this, shown, shown.surahs);
        refreshPanel();
    }

    /** Deletes what is saved of the recording (and stops its downloads); a saved station playing it stops. */
    private void deleteSaved() {
        if (shown == null) return;
        String key = shown.key();
        if (key.equals(Radio.station(this).savedKey()) && Radio.on()) Radio.stop();
        Library.askToDelete(this, Library.delete(this, key, 0), DELETE_ASKED);
        fillList();
        refreshPanel();
        refreshStorage();
        getAll.requestFocus();
    }

    private boolean enoughSpace() {
        if (Library.dir(this, true) != null && Library.freeBytes(this) > Library.RESERVE + 256L * 1024 * 1024) return true;
        panelProgress.setText(Library.dir(this, true) == null ? t.noStorage() : t.waitingSpace());
        return false;
    }

    private void refreshStorage() {
        long saved = Library.savedBytes(this, null), free = Library.freeBytes(this);
        storageSaved.setText(t.d(t.savedSize(t.bytes(saved))));
        storageFree.setText(t.d(t.freeSize(t.bytes(free))));
        long total = saved + free;
        int w = total <= 0 ? 0 : Math.round(Ui.px(420) * saved / (float) total);
        ViewGroup.LayoutParams lp = storageFill.getLayoutParams();
        if (lp.width != Math.max(w, saved > 0 ? Ui.ipx(4) : 0)) {
            lp.width = Math.max(w, saved > 0 ? Ui.ipx(4) : 0);
            storageFill.setLayoutParams(lp);
        }
    }

    // ---------------------------------------------------------------- small helpers

    /** A surah's name in the screen's language: Arabic, English, or another's from its list, else English. */
    private String surahIn(int s) {
        if (t.ar) return surahAr[s];
        return surahOwn[s] != null ? surahOwn[s] : surahEn[s];
    }

    private String surahName(int s) {
        String n = surahIn(s);
        return n == null ? t.d(String.valueOf(s)) : n;
    }

    private String surahLabel(int s) {
        String n = surahIn(s);
        return t.d(String.valueOf(s)) + (n == null ? "" : " " + n);
    }

    private FocusTile pill(TextView label, boolean filled) {
        FocusTile b = new FocusTile(this, 32);
        b.setRest(filled ? Tokens.GOLD_400 : Tokens.LAPIS_800, Tokens.LINE_SOFT, filled ? 0 : 2);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        b.addView(label, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> label.setTextColor(f || filled ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        return b;
    }

    private static GradientDrawable box(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(Ui.px(radius));
        return d;
    }
}
