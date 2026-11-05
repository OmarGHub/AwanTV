package app.awanpro.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The screensaver's screen, shared by the TV's screensaver ({@link AwanDream}) and "Screensaver now"
 * ({@link SaverActivity}).
 *
 * <p>Two layouts. Most of the day: the clock, both dates and the next prayer at the start side, and along the bottom
 * all six times with their iqamah ({@link Iqama}), the next one framed in gold with its countdown, the ones gone by
 * dimmed. In the adhkar times ({@link Adhkar}): a compact top line, the dhikr in the middle in Amiri with its count
 * and source and a thin bar for its time on screen, and the six times smaller below.
 *
 * <p>The layout stays put so it can be read; against burn-in the whole screen eases a few pixels every three
 * minutes (the method of Masjid TV, github.com/obaid/masjid-tv-tcl, MIT), and the screen is kept dark and dim.
 */
final class SaverScreen {

    /** What hosts the screen: the screensaver or an activity. */
    interface Host {
        /** The call to prayer is due: the screensaver wakes the TV so the call can come up over what was on. */
        void wake();

        Window window();

        boolean isDream();
    }

    private static final int GROUND = Tokens.SCREENSAVER_GROUND;
    private static final int CLOCK = 0xFFCFC8B8, TEXT = Tokens.SCREENSAVER_TEXT, DIM = 0xFF8C8576, PAST = 0xFF77715F;
    private static final int NEXT = 0xFFC2AE7C, NEXT_TIME = 0xFFE2DBCB, NEXT_GROUND = 0xFF0D0C0A, DHIKR = 0xFFE2DBCB;
    private static final int LINE_PAST = 0xFF1F1D19, LINE_LATER = 0xFF2A2722;
    /** Burn-in care: every three minutes, eased over four seconds, by up to this many design pixels each way. */
    private static final long SHIFT_EVERY_MS = 3 * 60_000, SHIFT_EASE_MS = 4000;
    private static final int SHIFT_X = 12, SHIFT_Y = 8;
    private static final float BAR_W = 280;
    private static final long DHIKR_FADE_MS = 500;
    /** The screen's strength at night (dim): the shade covers the rest. */
    private static final float NIGHT_ALPHA = 0.5f;

    private final Context c;
    private final Host host;
    private final Handler tick = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Text t;
    private final boolean h24;
    private final Typeface light, regular, medium, amiri;

    private final FrameLayout root;
    private final LinearLayout group, topFull, topBar, middle, stripFull, stripCompact;
    private final TextView clockBig, gregorian, hijri, cityDay, nextLabel, nextFull, clockSmall, dateLine, nextBar;
    /** The gold line under the countdown: Friday's (from Thursday's Maghrib) and Ramadan's. */
    private final TextView note;
    private final TextView dhikrLabel, dhikrText, dhikrMeta;
    /** The hadith in the full layout's middle (Hadith), and the Hijri line's coming occasion. */
    private final LinearLayout hadithBox;
    private final TextView hadithText, hadithSource;
    private Hadith.Item hadithShown;
    /** When this window of adhkar after the prayer began, or -1 (worked out once a minute). */
    private long afterPrayerStart = -1;
    /** Dimmed from two hours after Isha until Fajr (Settings > Awan TV). */
    private boolean dimmed, dimKnown;
    /** Black over the whole screen, half see-through at night; cheaper than fading the screen itself. */
    private final View shade;
    /**
     * Going dark with the radio on, the same note as Home's Screen off (the station, "the screen is
     * off and the radio plays · any key"), fading after a few seconds; a black screen alone looked broken.
     */
    private final LinearLayout darkNote;
    private final TextView darkText;
    private final RadioBars darkBars;
    private final View barFill;
    private final Radio2 radioFull, radioBar;
    /** The weather beside each layout's clock (Weather). */
    private final WeatherBadge weatherFull, weatherBar;
    private final Runnable onWeather;
    private final Cell[] cellsFull = new Cell[6], cellsCompact = new Cell[6];

    private PrayerDay day;
    private long lastShift, lastMinute = -1, started;
    private boolean dark;
    /** The adhkar period on screen (Adhkar.NONE: the full layout) and its list, round position and timing. */
    private int period = -1;
    private List<Adhkar.Dhikr> list;
    private int index;
    private long shownAt;

    /** One prayer along the bottom. */
    private static final class Cell {
        LinearLayout box;
        TextView name, time, iqama, left;
    }

    /** The radio line: bars and the station. */
    private static final class Radio2 {
        LinearLayout row;
        RadioBars bars;
        TextView text;
    }

    private final Runnable everySecond = new Runnable() {
        @Override
        public void run() {
            render();
            long now = System.currentTimeMillis();
            if (now - lastShift >= SHIFT_EVERY_MS) shift();
            // Test builds: any text cut on the screensaver, every two seconds (CropCheck).
            if ((now / 1000) % 2 == 0) CropCheck.scan(c, root, "Screensaver", t.lang);
            tick.postDelayed(this, 1000 - (now % 1000) + 5);
        }
    };

    SaverScreen(Context c, Host host) {
        this.c = c;
        this.host = host;
        Ui.init(c);
        t = Screen.loadText(c);
        h24 = Prefs.clock24(c);
        light = c.getResources().getFont(R.font.alexandria_light);
        regular = c.getResources().getFont(R.font.alexandria_regular);
        medium = c.getResources().getFont(R.font.alexandria_medium);
        amiri = c.getResources().getFont(R.font.amiri_regular);
        int dir = t.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR;

        root = new FrameLayout(c);
        root.setBackgroundColor(GROUND);
        root.setLayoutDirection(t.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        group = new LinearLayout(c);
        group.setOrientation(LinearLayout.VERTICAL);
        // 20px less above and 24px less below than Afnon's, room for the hadith in the middle (the
        // three-minute shift moves 8px at most).
        group.setPadding(Ui.ipx(128), Ui.ipx(76), Ui.ipx(128), Ui.ipx(64));

        // Full layout. the clock at the start side, the next prayer in the middle
        // of the line with the radio under it, the weather at the end side; under them one date line as on Home
        // (the Hijri date with the gold notice, the city and the day, the Gregorian date).
        topFull = new LinearLayout(c);
        topFull.setOrientation(LinearLayout.VERTICAL);
        clockBig = text(light, 200, CLOCK);
        clockBig.setIncludeFontPadding(false);
        // Alexandria's tabular digits, so the clock keeps its width from one minute to the next.
        clockBig.setFontFeatureSettings("tnum");
        clockBig.setTextDirection(dir);
        hijri = fit(text(light, 36, TEXT), 36);
        cityDay = text(light, 36, DIM);
        gregorian = fit(text(light, 36, TEXT), 36);
        // "Fajr in" on its own line and the countdown under it, so the name never moves as the digits change.
        nextLabel = text(regular, 52, NEXT);
        nextFull = text(regular, 72, NEXT);
        for (TextView v : new TextView[] {cityDay, nextFull, nextLabel}) v.setIncludeFontPadding(false);
        nextFull.setTextDirection(dir);
        // The centre on the screen's own middle, over the city and the day (CentreRow), moving aside
        // only when a wide clock ("12:08 PM") needs the room.
        LinearLayout centre = new LinearLayout(c);
        centre.setOrientation(LinearLayout.VERTICAL);
        centre.setGravity(Gravity.CENTER_HORIZONTAL);
        nextLabel.setGravity(Gravity.CENTER);
        nextLabel.setTextDirection(dir);
        centre.addView(nextLabel, new LinearLayout.LayoutParams(-1, -2));
        // A set width, so the countdown changing every second redraws without a layout pass.
        nextFull.setGravity(Gravity.CENTER);
        nextFull.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams nfp = new LinearLayout.LayoutParams(-1, -2);
        nfp.topMargin = Ui.ipx(14);
        centre.addView(nextFull, nfp);
        // Under the countdown, gone on other days, so they
        // look as before; it shrinks to fit the centre's width.
        note = fit(text(regular, 30, NEXT), 30);
        note.setGravity(Gravity.CENTER);
        note.setTextDirection(dir);
        note.setMaxLines(1);
        note.setVisibility(View.GONE);
        LinearLayout.LayoutParams ntp = new LinearLayout.LayoutParams(-1, Ui.ipx(30 * 1.4f));
        ntp.topMargin = Ui.ipx(16);
        centre.addView(note, ntp);
        // No divider and no gap: the weather stands on its own at the end side, smaller than the clock.
        weatherFull = new WeatherBadge(c, t, light, 136, 154, 0, NEXT, CLOCK, 0);
        CentreRow clockRow = new CentreRow(c, clockBig, centre, weatherFull, Ui.ipx(560), Ui.ipx(24));
        topFull.addView(clockRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout dates = new LinearLayout(c);
        dates.setGravity(Gravity.CENTER_VERTICAL);
        hijri.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        gregorian.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        int lineH = Ui.ipx(36 * 1.4f);
        dates.addView(hijri, new LinearLayout.LayoutParams(0, lineH, 1));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.setMarginStart(Ui.ipx(24));
        clp.setMarginEnd(Ui.ipx(24));
        dates.addView(cityDay, clp);
        dates.addView(gregorian, new LinearLayout.LayoutParams(0, lineH, 1));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = Ui.ipx(28);
        topFull.addView(dates, dlp);
        // The radio under the city and the day, not under the countdown, so "Fajr in" starts level
        // with the clock; its place is kept while it is off, so nothing below moves when it starts or stops.
        radioFull = radio();
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = Ui.ipx(20);
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        topFull.addView(radioFull.row, rlp);
        // A long station name ends in "…".
        radioFull.text.setMaxWidth(Ui.ipx(560));
        radioFull.text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        group.addView(topFull, new LinearLayout.LayoutParams(-1, -2));

        // Adhkar layout's top line: the clock and date at the start side, the next prayer at the end side.
        topBar = new LinearLayout(c);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout when = new LinearLayout(c);
        when.setOrientation(LinearLayout.VERTICAL);
        clockSmall = text(light, 100, CLOCK);
        clockSmall.setIncludeFontPadding(false);
        clockSmall.setFontFeatureSettings("tnum");
        clockSmall.setTextDirection(dir);
        dateLine = text(light, 30, DIM);
        LinearLayout smallRow = new LinearLayout(c);
        smallRow.setGravity(Gravity.CENTER_VERTICAL);
        smallRow.addView(clockSmall, wrap(0));
        weatherBar = new WeatherBadge(c, t, light, 77, 83, 34, NEXT, CLOCK, LINE_LATER);
        smallRow.addView(weatherBar, wrap(0));
        when.addView(smallRow, wrap(0));
        when.addView(dateLine, wrap(10));
        topBar.addView(when, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout endSide = new LinearLayout(c);
        endSide.setOrientation(LinearLayout.VERTICAL);
        endSide.setGravity(Gravity.END);
        nextBar = text(regular, 36, NEXT);
        nextBar.setTextDirection(dir);
        // A set width, so the countdown changing every second redraws without a layout pass.
        nextBar.setGravity(Gravity.END);
        nextBar.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = Ui.ipx(16);
        endSide.addView(nextBar, nlp);
        radioBar = radio();
        endSide.addView(radioBar.row, wrap(14));
        topBar.addView(endSide, new LinearLayout.LayoutParams(Ui.ipx(760), -2));
        group.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        // The middle: empty in the full layout, the dhikr in the adhkar times.
        middle = new LinearLayout(c);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER_HORIZONTAL);
        dhikrLabel = text(medium, 30, NEXT);
        dhikrLabel.setGravity(Gravity.CENTER);
        dhikrText = new TextView(c);
        dhikrText.setTypeface(amiri);
        dhikrText.setTextColor(DHIKR);
        dhikrText.setGravity(Gravity.CENTER);
        dhikrText.setTextDirection(View.TEXT_DIRECTION_RTL);
        dhikrText.setLineSpacing(0, 1.25f);
        // The longest (the closing verses of Al-Baqarah) shrink to fit; short ones stay at 72.
        dhikrText.setAutoSizeTextTypeUniformWithConfiguration(
            Math.round(Ui.px(30)), Math.round(Ui.px(72)), 1, TypedValue.COMPLEX_UNIT_PX);
        dhikrMeta = text(light, 28, DIM);
        dhikrMeta.setGravity(Gravity.CENTER);
        dhikrMeta.setTextDirection(dir);
        FrameLayout bar = new FrameLayout(c);
        bar.setBackground(round(LINE_PAST, 3));
        barFill = new View(c);
        barFill.setBackground(round(NEXT, 3));
        bar.addView(barFill, new FrameLayout.LayoutParams(-1, -1));
        // The fill grows from the start side by scale, not by width: no layout pass every second.
        barFill.setPivotX(t.rtl() ? Ui.px(BAR_W) : 0);
        barFill.setScaleX(0);
        middle.addView(dhikrLabel, centered(-1, -2, 28));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, 0, 1);
        tlp.topMargin = Ui.ipx(18);
        tlp.setMarginStart(Ui.ipx(60));
        tlp.setMarginEnd(Ui.ipx(60));
        middle.addView(dhikrText, tlp);
        middle.addView(dhikrMeta, centered(-1, -2, 18));
        middle.addView(bar, centered(Ui.ipx(BAR_W), Math.max(2, Ui.ipx(3)), 22));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, 0, 1);
        mlp.bottomMargin = Ui.ipx(28);
        group.addView(middle, mlp);

        // The full layout's middle, a hadith with its source, centred between the top and the times.
        hadithBox = new LinearLayout(c);
        hadithBox.setOrientation(LinearLayout.VERTICAL);
        hadithBox.setGravity(Gravity.CENTER);
        hadithText = new TextView(c);
        hadithText.setTypeface(amiri);
        hadithText.setTextColor(DHIKR);
        hadithText.setGravity(Gravity.CENTER);
        hadithText.setTextDirection(View.TEXT_DIRECTION_RTL);
        hadithText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        hadithText.setIncludeFontPadding(false);
        hadithText.setLineSpacing(0, 1.1f);
        // As tall as its lines, so the source sits right under it and the two are centred together
        // in the middle; the size is picked to fit what the middle has (fitHadith), 56 down to 26, never cut.
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.setMarginStart(Ui.ipx(60));
        hlp.setMarginEnd(Ui.ipx(60));
        hadithBox.addView(hadithText, hlp);
        hadithSource = text(light, 26, DIM);
        hadithSource.setGravity(Gravity.CENTER);
        hadithSource.setTextDirection(dir);
        hadithBox.addView(hadithSource, centered(-1, -2, 10));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1);
        // Clear of the countdown line above it.
        blp.topMargin = Ui.ipx(24);
        blp.bottomMargin = Ui.ipx(28);
        group.addView(hadithBox, blp);

        stripFull = strip(cellsFull, false);
        group.addView(stripFull, new LinearLayout.LayoutParams(-1, -2));
        stripCompact = strip(cellsCompact, true);
        group.addView(stripCompact, new LinearLayout.LayoutParams(-1, -2));

        root.addView(group, new FrameLayout.LayoutParams(-1, -1));
        shade = new View(c) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        shade.setBackgroundColor(0xFF000000);
        darkNote = new LinearLayout(c);
        darkNote.setOrientation(LinearLayout.VERTICAL);
        darkNote.setGravity(Gravity.CENTER_HORIZONTAL);
        darkBars = new RadioBars(c, 5);
        darkBars.setColor(Tokens.SCREENSAVER_TEXT_DIM);
        darkNote.addView(darkBars, new LinearLayout.LayoutParams(Ui.ipx(56), Ui.ipx(48)));
        darkText = new TextView(c);
        darkText.setTypeface(regular);
        darkText.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(24));
        darkText.setTextColor(TEXT);
        darkText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dnl = new LinearLayout.LayoutParams(-2, -2);
        dnl.topMargin = Ui.ipx(20);
        darkNote.addView(darkText, dnl);
        darkNote.setVisibility(View.GONE);
        root.addView(darkNote, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        shade.setAlpha(0f);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        showLayout(Adhkar.NONE);
        onWeather = () -> {
            weatherFull.update();
            weatherBar.update();
        };
    }

    View view() {
        return root;
    }

    void start() {
        if (host.isDream()) AwanDream.running = true;
        day = null;
        lastMinute = -1;
        period = -1;
        hadithShown = null;
        dimKnown = false;
        started = System.currentTimeMillis();
        lastShift = started;
        tick.removeCallbacks(everySecond);
        Weather.listener = onWeather;
        everySecond.run();
        // The adhan comes from the remote service; if it is on but not running, bind it again.
        ServiceCheck.soon(c, "screensaver");
    }

    void stop() {
        if (host.isDream()) AwanDream.running = false;
        setDark(false);
        shade.animate().cancel();
        tick.removeCallbacks(everySecond);
        if (Weather.listener == onWeather) Weather.listener = null;
        dhikrText.animate().cancel();
        dhikrMeta.animate().cancel();
        if (list != null && period > Adhkar.NONE) Adhkar.setPosition(period, index);
    }

    // ---------------------------------------------------------------- render

    private void render() {
        Date now = new Date();
        // The call to prayer: the screensaver wakes the TV so the call screen can open over what was on. Over the
        // activity it simply comes up, as over any app.
        if (host.isDream() && day != null && Reminders.dueCall(c, day, now) >= 0) {
            AwanDream.running = false;
            host.wake();
            return;
        }
        long minute = now.getTime() / 60000;
        boolean arrived = day != null && Screen.secondsUntil(day.nextTime, now) == 0;
        if (day == null || arrived || minute != lastMinute) {
            lastMinute = minute;
            day = new PrayerDay(c, arrived ? new Date(now.getTime() + 1000) : now);
            CharSequence clock = Screen.clock(t, now, h24, 0.3f);
            clockBig.setText(clock);
            clockSmall.setText(clock);
            Locale loc = Screen.dateLocale(t);
            String hijriText = t.d(day.hijriDay + " " + t.hijriMonths()[day.hijriMonth] + " " + day.hijriYear) + t.hijriSuffix();
            gregorian.setText(t.d(new SimpleDateFormat("d MMMM yyyy", loc).format(now)));
            cityDay.setText(t.city() + t.sep() + new SimpleDateFormat("EEEE", loc).format(now));
            // The coming occasion, within 30 days, after the Hijri date in gold.
            // The one gold line (the occasion, or tomorrow's sunnah fast in the evening).
            String occasion = Hijri.notice(c, t, day, now);
            hijri.setText(withOccasion(hijriText, occasion));
            String n = note(now);
            note.setText(n == null ? "" : n);
            note.setVisibility(n == null ? View.GONE : View.VISIBLE);
            dateLine.setText(withOccasion(t.d(new SimpleDateFormat("EEEE d MMMM", loc).format(now)) + t.sep() + hijriText, occasion));
            onWeather.run();
            dim(now);
            showPhoto(now);
            renderCells(cellsFull, now, false);
            renderCells(cellsCompact, now, true);
            int p = Adhkar.period(c, day, now);
            // The ten minutes of adhkar after a prayer take the middle of the full layout, also in the
            // morning and evening adhkar times (the windows begin and end on whole minutes).
            afterPrayerStart = Hadith.afterPrayerWindow(c, day, now);
            if (afterPrayerStart >= 0) p = Adhkar.NONE;
            if (p != period) {
                if (list != null && period > Adhkar.NONE) Adhkar.setPosition(period, index);
                period = p;
                list = Adhkar.of(c, p, day, now);
                // No adhkar to show (the asset failed to load): the full layout, not an empty middle.
                showLayout(list.isEmpty() ? Adhkar.NONE : p);
                index = list.isEmpty() ? 0 : Adhkar.position(p) % list.size();
                if (!list.isEmpty()) showDhikr(false);
                hadithShown = null;
            } else if (p > Adhkar.NONE) {
                // The night-only verses join at Maghrib: keep the place in the round by the dhikr shown.
                List<Adhkar.Dhikr> fresh = Adhkar.of(c, p, day, now);
                if (fresh.size() != list.size()) {
                    Adhkar.Dhikr at = list.isEmpty() ? null : list.get(index);
                    list = fresh;
                    index = Math.max(0, at == null ? 0 : list.indexOf(at));
                }
            }
        }
        if (period <= Adhkar.NONE || list == null || list.isEmpty()) showHadith(now.getTime());
        long secs = Screen.secondsUntil(day.nextTime, now);
        // Always to the second, so something on the screen moves.
        String left = t.in(Screen.duration(secs, true));
        String next = names()[day.nextIndex] + " " + left;
        // Only when the prayer changes: the name line stays put, the countdown line alone changes each second.
        String label = t.nextIn(names()[day.nextIndex]);
        // In Ramadan's night "Suhoor ends in"; on a Friday whose khutbah has its own time (a country's,
        // or the viewer's), the centre counts to the khutbah, from before Dhuhr until it begins.
        long centreSecs = secs;
        if (day.nextIndex == PrayerDay.FAJR && day.fajrRamadan) label = t.suhoorEndsIn();
        Date khutbah = Jumuah.at(c, day);
        if (khutbah != null && now.before(khutbah)
                && (day.nextIndex == PrayerDay.DHUHR || !now.before(day.times[PrayerDay.DHUHR]))) {
            label = t.khutbahIn();
            centreSecs = Screen.secondsUntil(khutbah, now);
        }
        if (!label.contentEquals(nextLabel.getText())) nextLabel.setText(label);
        // Always hours, minutes and seconds ("00:19:44"), so it never reads as a clock time.
        nextFull.setText(t.d(String.format(Locale.US, "%02d:%02d:%02d", centreSecs / 3600, (centreSecs % 3600) / 60, centreSecs % 60)));
        nextBar.setText(next);
        cellsFull[day.nextIndex].left.setText(left);
        if (period > Adhkar.NONE && !list.isEmpty()) advanceDhikr(now.getTime());
        renderRadio(radioFull, now.getTime());
        renderRadio(radioBar, now.getTime());
    }

    private void showLayout(int p) {
        boolean adhkar = p > Adhkar.NONE;
        topFull.setVisibility(adhkar ? View.GONE : View.VISIBLE);
        topBar.setVisibility(adhkar ? View.VISIBLE : View.GONE);
        middle.setVisibility(adhkar ? View.VISIBLE : View.GONE);
        hadithBox.setVisibility(adhkar ? View.GONE : View.VISIBLE);
        stripFull.setVisibility(adhkar ? View.GONE : View.VISIBLE);
        stripCompact.setVisibility(adhkar ? View.VISIBLE : View.GONE);
        if (adhkar) dhikrLabel.setText(p == Adhkar.MORNING ? t.morningAdhkar() : t.eveningAdhkar());
    }

    /**
     * The largest size, 56 down to 26, at which the text and its source fit the middle; measured with
     * the text's own layout. Before the first layout the middle has no height yet: it tries again once it has.
     */
    private void fitHadith() {
        int boxH = hadithBox.getHeight(), w = hadithBox.getWidth() - Ui.ipx(120);
        if (boxH <= 0 || w <= 0) {
            hadithBox.post(() -> { if (hadithBox.getHeight() > 0) fitHadith(); });
            return;
        }
        CharSequence text = hadithText.getText();
        int room = boxH - hadithSource.getHeight() - Ui.ipx(10);
        android.text.TextPaint paint = new android.text.TextPaint(hadithText.getPaint());
        float size = Ui.px(56), min = Ui.px(26);
        for (; size > min; size -= Ui.px(2)) {
            paint.setTextSize(size);
            android.text.StaticLayout l = android.text.StaticLayout.Builder.obtain(text, 0, text.length(), paint, w)
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0, 1.1f)
                .setIncludePad(false)
                .build();
            if (l.getHeight() <= room) break;
        }
        hadithText.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(size, min));
    }

    /** "… · Ramadan in 23 days" with the occasion in gold, or the line as it is. */
    private CharSequence withOccasion(String line, String occasion) {
        if (occasion == null) return line;
        android.text.SpannableString s = new android.text.SpannableString(line + t.sep() + occasion);
        s.setSpan(new android.text.style.ForegroundColorSpan(NEXT), s.length() - occasion.length(), s.length(),
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /**
     * This ten minutes' hadith (Hadith), faded in when it changes; nothing in the middle when it is
     * turned off.
     */
    private void showHadith(long now) {
        Hadith.Item after = Hadith.afterPrayerItem(c, afterPrayerStart, now);
        Hadith.Item h = after != null ? after : Prefs.hadith(c) ? Hadith.at(c, now) : null;
        if (h == hadithShown && (h != null || hadithText.getText().length() == 0)) return;
        boolean fade = hadithShown != null && h != null;
        hadithShown = h;
        hadithText.animate().cancel();
        hadithSource.animate().cancel();
        Runnable set = () -> {
            if (h == null) {
                hadithText.setText("");
                hadithSource.setText("");
                return;
            }
            if (h == after) {
                // A dhikr after the prayer: no quotation marks (the verses carry their own), and its label, count
                // and source under it.
                hadithText.setText(h.text);
                StringBuilder meta = new StringBuilder(t.afterPrayerLabel());
                String count = Languages.pick(h.counts, t);
                String ref = h.bukhari > 0 || h.muslim > 0 ? t.hadithSource(h.bukhari, h.muslim) : Languages.pick(h.refs, t);
                if (!count.isEmpty()) meta.append(t.sep()).append(count);
                if (!ref.isEmpty()) meta.append(t.sep()).append(ref);
                hadithSource.setText(meta.toString());
            } else {
                hadithText.setText("«" + h.text + "»");
                hadithSource.setText(t.hadithSource(h.bukhari, h.muslim));
            }
            fitHadith();
        };
        if (!fade) {
            set.run();
            hadithText.setAlpha(1f);
            hadithSource.setAlpha(1f);
            return;
        }
        hadithText.animate().alpha(0f).setDuration(DHIKR_FADE_MS).withEndAction(() -> {
            set.run();
            hadithText.animate().alpha(1f).setDuration(DHIKR_FADE_MS).start();
            hadithSource.animate().alpha(1f).setDuration(DHIKR_FADE_MS).start();
        }).start();
        hadithSource.animate().alpha(0f).setDuration(DHIKR_FADE_MS).start();
    }

    /**
     * From two hours after Isha until Fajr the whole screen at half strength, eased over a few seconds
     * (Settings > Awan TV). Before dawn the Isha slot is still today's, ahead; after Isha the Fajr slot is tomorrow's.
     */
    /** The photo behind the screensaver now (Settings > Awan TV > Photos), or null: black. */
    private Photos.Photo photo;
    private boolean photos;

    /** Once a minute: a new photo with each ten minutes; from Isha until Fajr only the dark ones. */
    private void showPhoto(Date now) {
        boolean on = Prefs.saverPhotos(c);
        if (on != photos) {
            photos = on;
            if (!on) {
                photo = null;
                root.setBackgroundColor(GROUND);
            }
            // A soft shadow under the middle's words, so a bright part of a photo behind them never takes their edge.
            float r = on ? Ui.px(14) : 0;
            for (TextView v : new TextView[] {hadithText, hadithSource, dhikrLabel, dhikrText, dhikrMeta}) {
                v.setShadowLayer(r, 0, 0, on ? 0xE6000000 : 0);
            }
        }
        if (!on) return;
        boolean night = day.nextIndex == PrayerDay.FAJR
            && now.getTime() >= PrayerDay.lastIsha(c, day, now).getTime();
        Photos.Photo p = Photos.forSaver(c, Photos.turn(c, false, day, now), night);
        if (p == null || p == photo) return;
        android.graphics.drawable.Drawable d = Photos.saverBackground(c, p);
        if (d == null) return;
        photo = p;
        root.setBackground(d);
    }

    private void dim(Date now) {
        boolean night = false;
        if (Prefs.nightDim(c) && day.nextIndex == PrayerDay.FAJR) {
            // After Isha, or after midnight before Fajr (when it counts from yesterday's Isha).
            night = now.getTime() >= PrayerDay.lastIsha(c, day, now).getTime() + 2 * 3600_000L;
        }
        if (dimKnown && night == dimmed) return;
        boolean first = !dimKnown;
        dimKnown = true;
        dimmed = night;
        shade.animate().cancel();
        float to = night ? 1f - NIGHT_ALPHA : 0f;
        // Opened at night: dim at once, not a bright screen fading down.
        if (first) shade.setAlpha(to);
        else shade.animate().alpha(to).setDuration(3000).start();
    }

    /** The dhikr at {@link #index}, faded in; its time on screen starts now. */
    private void showDhikr(boolean fade) {
        // A fade still running would put the previous dhikr back when it ends.
        dhikrText.animate().cancel();
        dhikrMeta.animate().cancel();
        Adhkar.Dhikr d = list.get(index);
        shownAt = System.currentTimeMillis();
        Runnable set = () -> {
            dhikrText.setText(d.text);
            String count = Languages.pick(d.counts, t);
            String ref = Languages.pick(d.refs, t);
            dhikrMeta.setText(ref.isEmpty() ? count : count + t.sep() + ref);
        };
        if (!fade) {
            set.run();
            dhikrText.setAlpha(1f);
            dhikrMeta.setAlpha(1f);
            return;
        }
        dhikrText.animate().alpha(0f).setDuration(DHIKR_FADE_MS).withEndAction(() -> {
            set.run();
            dhikrText.animate().alpha(1f).setDuration(DHIKR_FADE_MS).start();
            dhikrMeta.animate().alpha(1f).setDuration(DHIKR_FADE_MS).start();
        }).start();
        dhikrMeta.animate().alpha(0f).setDuration(DHIKR_FADE_MS).start();
    }

    /** The bar fills over the dhikr's time on screen; then the next one, round and round. */
    private void advanceDhikr(long now) {
        Adhkar.Dhikr d = list.get(index);
        long total = d.seconds() * 1000L;
        float done = Math.min(1f, (now - shownAt) / (float) total);
        barFill.setScaleX(done);
        if (now - shownAt >= total) {
            index = (index + 1) % list.size();
            showDhikr(true);
        }
    }

    private String[] names() {
        String[] names = t.prayerNames();
        if (day.friday) names[PrayerDay.DHUHR] = t.jumuah();
        // Ramadan's Maghrib is the iftar ("Iftar in"), as on Home.
        if (day.ramadan) names[PrayerDay.MAGHRIB] = t.iftar();
        return names;
    }

    /**
     * The line under the countdown, or null. Ramadan: the day and when suhoor ends (or the imsak, when
     * the viewer set one), at night the imsak and Fajr. Friday, from Thursday's Maghrib to Friday's: Al-Kahf and the
     * salawat on the Prophet. Ramadan's comes first.
     */
    private String note(Date now) {
        Date imsak = Jumuah.imsak(c, day);
        if (day.nextIndex == PrayerDay.FAJR && day.fajrRamadan) {
            CharSequence fajr = Screen.clock(t, day.suhoorEnd, h24, 1f);
            if (imsak != null) return t.imsakAt(Screen.clock(t, imsak, h24, 1f)) + t.sep() + t.fajrAt(fajr);
            return day.ramadan ? t.ramadanDay(day.hijriDay) : t.suhoorEnds(fajr.toString());
        }
        if (day.ramadan) {
            // The last day's evening: tomorrow is Eid, no suhoor tonight.
            boolean fastTomorrow = day.hijriDay < 29 || new PrayerDay(c, new Date(now.getTime() + 24 * 3600_000L)).ramadan;
            if (!fastTomorrow || day.nextIndex == PrayerDay.FAJR) return t.ramadanDay(day.hijriDay);
            String end = imsak != null ? t.imsakAt(Screen.clock(t, imsak, h24, 1f)) : t.suhoorEnds(Screen.clock(t, day.suhoorEnd, h24, 1f).toString());
            return t.ramadanDay(day.hijriDay) + t.sep() + end;
        }
        if (day.friday && now.before(day.times[PrayerDay.MAGHRIB])) return t.fridayLine();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(now);
        if (cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.THURSDAY && !now.before(day.times[PrayerDay.MAGHRIB])) {
            return t.fridayEveLine();
        }
        return null;
    }

    /** Each prayer's time and iqamah; the next one framed in gold, the ones gone by dimmed. */
    private void renderCells(Cell[] cells, Date now, boolean compact) {
        String[] names = names();
        boolean iqamaOn = Iqama.mode(c) != Iqama.OFF;
        for (int i = 0; i < 6; i++) {
            Cell cell = cells[i];
            boolean isNext = i == day.nextIndex;
            boolean past = !isNext && !day.times[i].after(now);
            cell.name.setText(names[i]);
            cell.time.setText(Screen.clock(t, day.times[i], h24, 0.45f));
            Date at = Iqama.at(c, day, i);
            if (!iqamaOn) {
                cell.iqama.setVisibility(View.GONE);
            } else if (at != null) {
                cell.iqama.setVisibility(View.VISIBLE);
                cell.iqama.setText(t.iqamaAt(Screen.clock(t, at, h24, 1f)));
            } else if (day.friday && i == PrayerDay.DHUHR) {
                cell.iqama.setVisibility(View.VISIBLE);
                // The khutbah's own time where it has one (a country's, or the viewer's).
                Date k = Jumuah.at(c, day);
                cell.iqama.setText(k == null ? t.khutbah() : t.khutbahAt(Screen.clock(t, k, h24, 1f)));
            } else {
                // Sunrise: no iqamah line at all; its room is kept so every cell has the same height.
                cell.iqama.setText("");
                cell.iqama.setVisibility(View.INVISIBLE);
            }
            if (cell.left != null) cell.left.setVisibility(isNext ? View.VISIBLE : View.INVISIBLE);

            GradientDrawable box = new GradientDrawable();
            box.setCornerRadius(Ui.px(compact ? 18 : 22));
            // Over a photo the boxes keep a dark ground of their own, so the times stay readable.
            if (isNext) {
                box.setColor(photos ? 0xC00D0C0A : NEXT_GROUND);
                box.setStroke(Math.max(2, Ui.ipx(2)), NEXT);
            } else {
                box.setColor(photos ? 0x8C000000 : GROUND);
                box.setStroke(Math.max(1, Ui.ipx(1)), past ? LINE_PAST : LINE_LATER);
            }
            cell.box.setBackground(box);
            cell.name.setTypeface(isNext ? medium : regular);
            cell.name.setTextColor(isNext ? NEXT : past ? PAST : TEXT);
            cell.time.setTextColor(isNext ? NEXT_TIME : past ? PAST : CLOCK);
            cell.iqama.setTextColor(past ? PAST : TEXT);
            if (cell.left != null) cell.left.setTextColor(TEXT);
        }
    }

    /** On air: a quiet line with the bars; after the chosen minutes the screen goes dark and the radio plays on. */
    private void renderRadio(Radio2 r, long now) {
        boolean on = Radio.on();
        // The full layout keeps the radio line's room, so the hadith below it stays put.
        r.row.setVisibility(on ? View.VISIBLE : r == radioFull ? View.INVISIBLE : View.GONE);
        if (on) {
            String line = t.station(Radio.station(c)) + t.sep() + t.radioStatus(Radio.state());
            // Only when it changes: a new text is a layout pass, every second otherwise.
            if (!line.contentEquals(r.text.getText())) r.text.setText(line);
            r.bars.setLive(Radio.state() == Radio.PLAYING);
        }
        if (r != radioFull) return;
        int minutes = Prefs.radioScreenOff(c);
        boolean due = on && minutes >= 0 && now - started >= minutes * 60_000L;
        if (due != dark) setDark(due);
    }

    /**
     * Dark: TCL's Audio Only (its countdown, then the panel off and the TV asleep with the radio playing). Where
     * there is none, the screen blanks itself at its lowest brightness instead.
     */
    private void setDark(boolean on) {
        if (dark == on) return;
        dark = on;
        if (on && ScreenOffActivity.audioOnly(c)) return;
        group.setVisibility(on ? View.INVISIBLE : View.VISIBLE);
        darkNote.animate().cancel();
        darkNote.setVisibility(on ? View.VISIBLE : View.GONE);
        darkBars.setLive(on);
        if (on) {
            darkText.setText(t.station(Radio.station(c)) + "\n" + t.screenOffNote());
            darkNote.setAlpha(1f);
            // Faded out, the bars stop too: nothing redraws on the dark screen.
            darkNote.animate().alpha(0f).setStartDelay(6000).setDuration(1500)
                .withEndAction(() -> darkBars.setLive(false)).start();
        }
        Window w = host.window();
        if (w != null) {
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.screenBrightness = on ? android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
                                     : android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            w.setAttributes(lp);
        }
    }

    /** Eases the whole screen to a new spot a few pixels from its place, so nothing sits on the same pixels for long. */
    private void shift() {
        lastShift = System.currentTimeMillis();
        float x = Ui.px(random.nextInt(2 * SHIFT_X + 1) - SHIFT_X);
        float y = Ui.px(random.nextInt(2 * SHIFT_Y + 1) - SHIFT_Y);
        group.animate().translationX(x).translationY(y).setDuration(SHIFT_EASE_MS).start();
    }

    // ---------------------------------------------------------------- building

    private LinearLayout strip(Cell[] cells, boolean compact) {
        LinearLayout strip = new LinearLayout(c);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        int dir = t.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR;
        for (int i = 0; i < 6; i++) {
            Cell cell = new Cell();
            cell.box = new LinearLayout(c);
            cell.box.setOrientation(LinearLayout.VERTICAL);
            int v = Ui.ipx(compact ? 16 : 24);
            cell.box.setPadding(Ui.ipx(18), v, Ui.ipx(18), v);
            // Each line fills the cell, centred, and shrinks to fit it (a 12-hour "10:00 PM" is the widest).
            float nameSize = compact ? 26 : 30, timeSize = compact ? 40 : 54, small = compact ? 20 : 22;
            cell.name = centre(fit(text(regular, nameSize, TEXT), nameSize));
            cell.time = centre(fit(text(light, timeSize, CLOCK), timeSize));
            cell.time.setTextDirection(dir);
            cell.iqama = centre(fit(text(light, small, TEXT), small));
            cell.iqama.setTextDirection(dir);
            cell.box.addView(cell.name, fill(0, nameSize));
            cell.box.addView(cell.time, fill(compact ? 4 : 8, timeSize));
            cell.box.addView(cell.iqama, fill(compact ? 4 : 8, small));
            if (!compact) {
                // The countdown under the next prayer; in the adhkar layout it is in the top line instead.
                cell.left = centre(fit(text(light, small, TEXT), small));
                cell.left.setTextDirection(dir);
                cell.box.addView(cell.left, fill(4, small));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(Ui.ipx(compact ? 20 : 24));
            strip.addView(cell.box, lp);
            cells[i] = cell;
        }
        return strip;
    }

    private Radio2 radio() {
        Radio2 r = new Radio2();
        r.row = new LinearLayout(c);
        r.row.setGravity(Gravity.CENTER_VERTICAL);
        r.bars = new RadioBars(c, 3);
        r.bars.setColor(NEXT);
        r.row.addView(r.bars, new LinearLayout.LayoutParams(Ui.ipx(22), Ui.ipx(20)));
        r.text = text(regular, 26, DIM);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(Ui.ipx(12));
        r.row.addView(r.text, lp);
        return r;
    }

    private TextView text(Typeface tf, float size, int color) {
        TextView v = new TextView(c);
        v.setTypeface(tf);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(size));
        v.setTextColor(color);
        v.setGravity(Gravity.START);
        v.setSingleLine(true);
        return v;
    }

    private static TextView centre(TextView v) {
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        return v;
    }

    /** Shrinks the text (down to 60%) when it would not fit its width; one line, never cut. */
    private static TextView fit(TextView v, float size) {
        int max = Math.max(2, Math.round(Ui.px(size)));
        int min = Math.max(1, Math.round(Ui.px(size * 0.6f)));
        v.setIncludeFontPadding(false);
        v.setAutoSizeTextTypeUniformWithConfiguration(min, max, 1, TypedValue.COMPLEX_UNIT_PX);
        return v;
    }

    /** Full width and a set height (1.6 lines of the text size), so the text can shrink to fit. */
    private static LinearLayout.LayoutParams fill(float top, float size) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.ipx(size * 1.6f));
        lp.topMargin = Ui.ipx(top);
        return lp;
    }

    private static LinearLayout.LayoutParams wrap(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.ipx(top);
        return lp;
    }

    private static LinearLayout.LayoutParams centered(int w, int h, float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = Ui.ipx(top);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(Ui.px(radius));
        return d;
    }
}
