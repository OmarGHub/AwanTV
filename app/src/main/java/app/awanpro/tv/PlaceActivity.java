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
import android.widget.LinearLayout;
import android.widget.TextView;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Any place in the world (Settings > Prayer times > Location > Any place in the world).
 *
 * <p>The start side searches {@link Places} (34,000 cities, Arabic or English) as the viewer types with the TV's
 * keyboard; OK on a result fills the chosen place on the end side: its name (which the viewer can still change),
 * its coordinates, and the method its country's mosques follow. Coordinates can also be typed there directly for a
 * smaller place: Arabic digits, the Arabic decimal comma and a pasted "lat, lng" pair are all understood. Save
 * keeps it as the place ({@link Prefs#setCustom}).
 */
public class PlaceActivity extends Screen {

    private static final int RESULTS = 5, NAME_MAX = 32;
    private static final float START_W = 840, CARD_W = 760, CARD_IN = 40;

    private EditText search, name, lat, lng;
    private TextView status, method, iqama, error;
    private final FocusTile[] result = new FocusTile[RESULTS];
    private final TextView[] resultName = new TextView[RESULTS], resultSub = new TextView[RESULTS];
    private final List<Places.Place> shown = new ArrayList<>();
    /** The city picked from the results (its country goes with it while the coordinates stay its own); or null. */
    private Places.Place picked;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private int asked;
    private final Runnable runSearch = this::runSearch;

    @Override
    void build() {
        newRoot();
        root.setBackgroundColor(Tokens.LAPIS_900);

        TextView title = text(displayBold, 52, Tokens.PLASTER_100);
        title.setText(t.placeTitle());
        place(title, 96, 48, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Amiri's tall line box would push the title down onto the line under it.
        lineHeight(title, 1.1f);
        TextView intro = text(ui400, 24, Tokens.MIST_400);
        intro.setText(t.placeIntro());
        place(intro, 96, 132, 1728, ViewGroup.LayoutParams.WRAP_CONTENT);

        // ---- the start side: the search and its results
        label(t.searchCity(), 96, 210, false);
        search = field(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, false);
        search.setPaddingRelative(Ui.ipx(70), 0, Ui.ipx(24), 0);
        place(search, 96, 248, START_W, 76);
        Icon glass = new Icon(this, Icon.SEARCH, 2f);
        glass.setColor(Tokens.MIST_400);
        place(glass, 96 + 24, 248 + 23, 30, 30);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                main.removeCallbacks(runSearch);
                main.postDelayed(runSearch, 250);
            }
        });
        for (int i = 0; i < RESULTS; i++) result[i] = resultTile(i);
        status = text(ui400, 22, Tokens.MIST_400);
        place(status, 96, 360, START_W, ViewGroup.LayoutParams.WRAP_CONTENT);

        // ---- the end side: the chosen place
        View card = new View(this);
        GradientDrawable bg = box(Tokens.LAPIS_800, 24);
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
        card.setBackground(bg);
        placeEnd(card, 96, 210, CARD_W, 560);
        float in = 96 + CARD_IN, inW = CARD_W - 2 * CARD_IN;
        label(t.chosenPlace(), in, 246, true);
        label(t.placeName(), in, 292, true);
        name = field(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, false);
        // Short enough for the Hijri line on Home.
        name.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(NAME_MAX)});
        placeEnd(name, in, 324, inW, 72);
        float half = (inW - 20) / 2;
        label(t.latitude(), in + half + 20, 420, true);
        label(t.longitude(), in, 420, true);
        int number = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
        lat = field(number, true);
        placeEnd(lat, in + half + 20, 452, half, 72);
        lng = field(number, true);
        placeEnd(lng, in, 452, half, 72);
        method = text(ui400, 22, Tokens.GOLD_400);
        method.setSingleLine(true);
        methodWidth = inW;
        placeEnd(method, in, 552, inW, ViewGroup.LayoutParams.WRAP_CONTENT);
        iqama = text(ui400, 20, Tokens.MIST_400);
        placeEnd(iqama, in, 590, inW, ViewGroup.LayoutParams.WRAP_CONTENT);
        FocusTile save = button(t.placeSave(), true);
        save.setOnClickListener(v -> save());
        placeEnd(save, in + inW - 220, 650, 220, 72);
        FocusTile cancel = button(t.placeCancel(), false);
        cancel.setOnClickListener(v -> finish());
        placeEnd(cancel, in + inW - 220 - 20 - 220, 650, 220, 72);
        error = text(ui400, 22, Tokens.GOLD_400);
        placeEnd(error, 96, 790, CARD_W, ViewGroup.LayoutParams.WRAP_CONTENT);

        LinearLayout hints = new LinearLayout(this);
        place(hints, 96, 976, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        List<String> h = new ArrayList<>();
        h.add(t.hintPick());
        h.add(t.hintToChosen());
        h.add(t.hintCancel());
        setHints(hints, h);
        TextView credit = text(ui400, 18, Tokens.MIST_400);
        credit.setAlpha(0.7f);
        credit.setText(t.citiesCredit());
        placeEnd(credit, 96, 980, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        // Today's place as the starting point.
        Prefs.City now = Prefs.city(this);
        name.setText(t.ar ? now.ar : now.en);
        lat.setText(String.format(Locale.US, "%.4f", now.lat));
        lng.setText(String.format(Locale.US, "%.4f", now.lng));
        showMethod(null);
        lat.addTextChangedListener(onCoordinates);
        lng.addTextChangedListener(onCoordinates);

        setContentView(root);
        search.requestFocus();
        // Loads the list while the viewer starts typing.
        worker.execute(() -> {
            try {
                Places.load(this);
            } catch (java.io.IOException ignored) {
                // Shown as no results when searching.
            }
        });
    }

    @Override
    void onTick() {
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- search

    private void runSearch() {
        String q = search.getText().toString();
        int ticket = ++asked;
        if (Places.normalise(q).length() < 2) {
            show(new ArrayList<>(), "");
            return;
        }
        // While the list loads the first time (a second or two on a TV), with nothing shown yet.
        if (shown.isEmpty()) {
            status.setText(t.loadingCities());
            status.setVisibility(View.VISIBLE);
        }
        worker.execute(() -> {
            List<Places.Place> found;
            try {
                found = Places.search(this, q, RESULTS);
            } catch (java.io.IOException e) {
                found = new ArrayList<>();
            }
            List<Places.Place> got = found;
            main.post(() -> {
                if (ticket == asked) show(got, got.isEmpty() ? t.noCity() : "");
            });
        });
    }

    private void show(List<Places.Place> found, String note) {
        shown.clear();
        shown.addAll(found);
        for (int i = 0; i < RESULTS; i++) {
            boolean has = i < found.size();
            result[i].setVisibility(has ? View.VISIBLE : View.GONE);
            if (!has) continue;
            Places.Place p = found.get(i);
            resultName[i].setText(p.name(t));
            String sub = t.population(p.population);
            String country = Prefs.countryName(p.cc, t.ar);
            if (!country.isEmpty()) sub = country + t.sep() + sub;
            // The Latin name too, when the line above is Arabic.
            if (t.ar && !p.ar.isEmpty()) sub = p.name + t.sep() + sub;
            resultSub[i].setText(sub);
        }
        status.setText(note);
        status.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private FocusTile resultTile(int i) {
        FocusTile b = new FocusTile(this, 16);
        b.setRest(Tokens.LAPIS_800, 0, 0);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Ui.ipx(28), 0, Ui.ipx(28), 0);
        TextView n = text(ui400, 28, Tokens.PLASTER_100);
        n.setSingleLine(true);
        TextView s = text(ui400, 20, Tokens.MIST_400);
        s.setSingleLine(true);
        col.addView(n);
        col.addView(s);
        b.addView(col, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> {
            n.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100);
            s.setTextColor(f ? Tokens.LAPIS_900 : Tokens.MIST_400);
            s.setAlpha(f ? 0.8f : 1f);
        });
        b.setOnClickListener(v -> pick(i));
        b.setVisibility(View.GONE);
        resultName[i] = n;
        resultSub[i] = s;
        placeTile(b, 96, 346 + i * 98, START_W, 88);
        return b;
    }

    /** OK on a result: it becomes the chosen place, and focus goes to its name for a last look. */
    private void pick(int i) {
        if (i >= shown.size()) return;
        Places.Place p = shown.get(i);
        lat.removeTextChangedListener(onCoordinates);
        lng.removeTextChangedListener(onCoordinates);
        name.setText(shorten(p.name(t)));
        lat.setText(String.format(Locale.US, "%.4f", p.lat));
        lng.setText(String.format(Locale.US, "%.4f", p.lng));
        lat.addTextChangedListener(onCoordinates);
        lng.addTextChangedListener(onCoordinates);
        picked = p;
        showMethod(p.cc);
        error.setText("");
        name.requestFocus();
    }

    /** Coordinates typed by hand: the place is no longer the picked city, so its country no longer applies. */
    private final TextWatcher onCoordinates = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) {
            if (picked != null) {
                picked = null;
                showMethod(null);
            }
        }
    };

    /** The method line's width (design px): its text shrinks to fit it (setMethod). */
    private float methodWidth;

    /**
     * A long method name with its hint ("· check it after saving": Morocco's ministry) at a smaller size, down to 70%,
     * so it is never cut. Measured here: a single line's TextView never shrinks by itself (setSingleLine scrolls).
     */
    private void setMethod(String line) {
        float full = Ui.px(22), size = full, room = Ui.px(methodWidth);
        android.graphics.Paint p = new android.graphics.Paint(method.getPaint());
        p.setTextSize(size);
        while (size > full * 0.7f && p.measureText(line) > room) {
            size -= 1;
            p.setTextSize(size);
        }
        method.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        method.setText(line);
    }

    /** The method line: the country's, or today's when the country is not known. And the iqamah, where it has one. */
    private void showMethod(String cc) {
        Method m = Places.method(cc);
        String country = Prefs.countryName(cc, t.ar);
        if (m != null) {
            setMethod(t.placeMethod(t.methodName(m), country));
        } else {
            setMethod(t.placeMethod(t.methodName(Prefs.method(this)), null));
        }
        boolean preset = cc != null && Iqama.presetFor(Prefs.countryName(cc, false)) != null;
        iqama.setText(preset ? t.placeIqama(country) : "");
    }

    /** A long city name cut at a word, to fit the name field. */
    private static String shorten(String s) {
        if (s.length() <= NAME_MAX) return s;
        int cut = s.lastIndexOf(' ', NAME_MAX);
        return (cut > NAME_MAX / 2 ? s.substring(0, cut) : s.substring(0, NAME_MAX)).trim();
    }

    // ---------------------------------------------------------------- save

    private void save() {
        String n = name.getText().toString().trim();
        double[] a = numbers(lat.getText().toString());
        double[] b = numbers(lng.getText().toString());
        // A "lat, lng" pair pasted or typed into the latitude field fills both.
        if (a != null && a.length == 2) b = new double[] {a[1]};
        if (n.isEmpty()) {
            error.setText(t.placeNoName());
            name.requestFocus();
            return;
        }
        if (a == null || a.length < 1 || b == null || b.length < 1 || Math.abs(a[0]) > 90 || Math.abs(b[0]) > 180) {
            error.setText(t.placeBad());
            lat.requestFocus();
            return;
        }
        Prefs.setCustom(this, n, a[0], b[0], picked == null ? "" : picked.cc);
        finish();
    }

    /**
     * The numbers in a field: Arabic-Indic digits and the Arabic decimal separator read as Western, "," between two
     * numbers splits them. Null when something is not a number.
     */
    static double[] numbers(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.trim().toCharArray()) {
            if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + ch - '٠'));
            else if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + ch - '۰'));
            else if (ch == '٫') b.append('.');
            else if (ch == '−' || ch == '–') b.append('-');
            else if (ch == '،' || ch == ';') b.append(',');
            else b.append(ch);
        }
        String clean = b.toString().replace("°", "").trim();
        if (clean.isEmpty()) return new double[0];
        // One number with a decimal comma (21,4225), not a pair.
        if (clean.matches("-?\\d+,\\d+")) clean = clean.replace(',', '.');
        String[] parts = clean.split("\\s*,\\s*|\\s+");
        double[] out = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return out.length > 2 ? null : out;
    }

    // ---------------------------------------------------------------- small views

    private void label(String s, float inset, float y, boolean fromEnd) {
        TextView l = text(ui400, fromEnd ? 20 : 22, Tokens.MIST_400);
        l.setText(s);
        if (fromEnd) placeEnd(l, inset, y, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        else place(l, inset, y, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** A text field: lapis, a soft line at rest and a gold one when focused; OK opens the TV's keyboard. */
    private EditText field(int inputType, boolean digits) {
        EditText e = new EditText(this);
        e.setTypeface(ui400);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(digits ? 28 : 30));
        e.setTextColor(Tokens.PLASTER_100);
        e.setSingleLine(true);
        // The coordinates' input type only picks the keyboard (setRaw): a digits-only filter would drop the Arabic
        // digits, the decimal comma and the "lat, lng" pair that numbers() reads.
        if (digits) e.setRawInputType(inputType);
        else e.setInputType(inputType);
        e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        e.setPadding(Ui.ipx(22), 0, Ui.ipx(22), 0);
        if (digits) e.setTextDirection(View.TEXT_DIRECTION_LTR);
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = box(Tokens.LAPIS_900, 14);
        focused.setStroke(Ui.ipx(3), Tokens.GOLD_400);
        bg.addState(new int[] {android.R.attr.state_focused}, focused);
        GradientDrawable rest = box(Tokens.LAPIS_900, 14);
        rest.setStroke(Ui.ipx(2), Tokens.LINE_SOFT);
        bg.addState(new int[0], rest);
        e.setBackground(bg);
        return e;
    }

    private static GradientDrawable box(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(Ui.px(radius));
        return d;
    }

    private FocusTile button(String label, boolean filled) {
        FocusTile b = new FocusTile(this, 36);
        b.setRest(filled ? Tokens.GOLD_400 : Tokens.LAPIS_800, Tokens.LINE_SOFT, filled ? 0 : 2);
        TextView v = text(ui500, 26, filled ? Tokens.LAPIS_900 : Tokens.PLASTER_100);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        b.addView(v, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> v.setTextColor(f || filled ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        return b;
    }
}
