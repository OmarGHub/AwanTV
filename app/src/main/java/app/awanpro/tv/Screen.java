package app.awanpro.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * What every Afnon screen shares: language and digit settings, fonts, design-px placement, the clock format,
 * a once-a-second tick, and the mihrab arch with its crown, next prayer and countdown.
 */
abstract class Screen extends Activity {

    final Handler tick = new Handler(Looper.getMainLooper());

    Text t;
    boolean rtl;
    boolean h24;
    Typeface ui400, ui500, ui600, display, displayBold, sacred;
    FrameLayout root;
    ArchView arch;
    TextView archLabel, archName, archCount, archSub, archSub2;
    /** {@link Prefs#key} when the screen was last built. */
    private String builtSettings;
    /** The theme the screen was built in; the automatic one changes at sunrise and Maghrib. */
    private int builtTheme = -1;
    private long themeMinute = -1;

    private final Runnable everySecond = new Runnable() {
        @Override
        public void run() {
            // Once a minute, the automatic theme may have turned (sunrise, Maghrib): build again in it.
            // And any setting changed from outside (AwanTV-settings.ps1), not the theme alone.
            long minute = System.currentTimeMillis() / 60_000;
            if (minute != themeMinute) {
                themeMinute = minute;
                rebuildIfChanged();
            }
            onTick();
            long now = System.currentTimeMillis();
            // Test builds: any text cut on this screen, every two seconds (CropCheck).
            if ((now / 1000) % 2 == 0 && t != null) CropCheck.scan(Screen.this, getWindow().getDecorView(), Screen.this.getClass().getSimpleName(), t.lang);
            tick.postDelayed(this, 1000 - (now % 1000) + 5);
        }
    };

    /** Builds the whole screen into a fresh {@link #root}; called again when the language changes. */
    abstract void build();

    /** Once a second while the screen is in front. */
    abstract void onTick();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // For the safety net: a stop while this screen is built counts as in front (Crash).
        Crash.screenStarted = android.os.SystemClock.uptimeMillis();
        Ui.init(this);
        loadSettings(getIntent());
        build();
    }

    /** The TV's language changed (the manifest keeps the activity alive for it); rebuild if Afnon follows it. */
    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        String was = t.lang;
        loadSettings(null);
        if (!t.lang.equals(was)) build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        front = this;
        // Back from Afnon Settings with something changed: rebuild in the new language, place or method.
        rebuildIfChanged();
        tick.removeCallbacks(everySecond);
        everySecond.run();
        // A language chosen on another screen, still waiting for Keep: its question here, or it goes back now. Or the
        // question this screen showed was answered on another meanwhile: it goes.
        if (undoChosen != null || keepLayer != null) keepStep();
    }

    @Override
    protected void onPause() {
        if (front == this) front = null;
        tick.removeCallbacks(everySecond);
        tick.removeCallbacks(keepCount);
        super.onPause();
    }

    /** The screen in front, for {@link #settingsChanged}; null when none of Awan TV's is. */
    private static Screen front;

    /** The screen in front's name, for the safety net (Crash): null when none of Awan TV's is. */
    static String frontName() {
        Screen f = front;
        return f == null ? null : f.getClass().getSimpleName();
    }

    /**
     * Settings changed from outside the screens (an import or restore from AwanTV-settings.ps1): the
     * screen in front builds again at once (it waited for a return to it before). Always, not by {@link Prefs#key}:
     * an import may change what the key leaves out (favourites, iqamah, weather). Main thread.
     */
    static void settingsChanged() {
        if (front == null || front.isFinishing()) return;
        front.loadSettings(null);
        front.build();
    }

    /** Builds again when the language, digits, place, method or theme differ from what the screen was built with. */
    private void rebuildIfChanged() {
        Tokens.apply(this);
        if (builtSettings != null && (!builtSettings.equals(Prefs.key(this)) || builtTheme != Tokens.applied)) {
            loadSettings(null);
            build();
        }
    }

    // ---------------------------------------------------------------- settings

    /**
     * Language and digits. "locale" is "system" (default: Arabic only when the TV's language is Arabic), "ar" or
     * "en"; "digits" is empty (Eastern in Arabic, Western in English), "eastern" or "western". Set in Afnon
     * Settings, or for testing: `adb shell am start -n app.awanpro.tv/.MainActivity --es locale ar|en|system`.
     *
     * @return whether a setting changed since the screen was built: by the intent, or while it was away (Settings,
     *     then the Home key brings Home back through onNewIntent, which would otherwise stamp the new settings as
     *     built and leave the old layout, with only the parts redrawn each minute in the new language or colours)
     */
    boolean loadSettings(Intent i) {
        SharedPreferences p = prefs();
        String wasSettings = builtSettings;
        int wasTheme = builtTheme;
        boolean changed = false;
        if (i != null && i.hasExtra("locale")) {
            p.edit().putString("locale", i.getStringExtra("locale")).apply();
            changed = true;
        }
        if (i != null && i.hasExtra("digits")) {
            p.edit().putString("digits", i.getStringExtra("digits")).apply();
            changed = true;
        }
        t = loadText(this);
        rtl = t.rtl();
        builtSettings = Prefs.key(this);
        // The colour theme, before anything is built with it.
        Tokens.apply(this);
        builtTheme = Tokens.applied;
        return changed || (wasSettings != null && (!wasSettings.equals(builtSettings) || wasTheme != builtTheme));
    }

    /**
     * The language and digits from the settings ("locale", "digits") and the TV's language: {Arabic, Eastern digits}.
     * Kept apart from the Context so the text dump (TextDump) checks it for every combination.
     */
    static boolean[] language(String locale, String tvLanguage, String digits) {
        // Digits as the language unless set; unknown = by language, too.
        boolean ar = "ar".equals(languageTag(locale, tvLanguage));
        boolean eastern = "eastern".equals(digits) || (!"western".equals(digits) && ar);
        return new boolean[] {ar, eastern};
    }

    /**
     * The screens' language (Languages.TAGS) for the setting "locale" and the TV's language. A value this version
     * does not know (a language from a newer one, or written by adb) counts as the TV's, as Settings shows it ("TV");
     * before 1.16.0 it read as English.
     */
    static String languageTag(String locale, String tvLanguage) {
        if (Languages.known(locale)) return locale;
        // "TV": the TV's language when Awan TV has it (phase 3: any announced language), else English.
        String tv = Languages.fromAndroid(tvLanguage);
        return Languages.known(tv) ? tv : "en";
    }

    /** The TV's own language now (its first locale's language code). */
    String tvLanguage() {
        return getResources().getConfiguration().getLocales().get(0).getLanguage();
    }

    /** The locale of Gregorian dates: month and day names in the screen's language, Western digits (t.d() decides). */
    static Locale dateLocale(Text t) {
        return t.dateLocale();
    }

    /** The saved language and digits, for screens and for the screensaver, which is not an Activity. */
    static Text loadText(Context c) {
        SharedPreferences p = Prefs.of(c);
        String locale = p.getString("locale", "system");
        String tv = c.getResources().getConfiguration().getLocales().get(0).getLanguage();
        boolean[] lang = language(locale, tv, p.getString("digits", ""));
        Text t = Text.of(c, languageTag(locale, tv), lang[1]);
        Prefs.City city = Prefs.city(c);
        t.cityName = t.ar ? city.ar : city.en;
        return t;
    }

    /**
     * The direction the viewer faces when watching, in degrees from true north (read once from a phone compass
     * while facing the TV); -1 until it is set.
     */
    int facing() {
        return prefs().getInt("facing", -1);
    }

    void setFacing(int degrees) {
        prefs().edit().putInt("facing", degrees).apply();
    }

    SharedPreferences prefs() {
        return Prefs.of(this);
    }

    // ---------------------------------------------------------------- shared layout

    /** Fonts for the current language, and an empty root laid out in its direction over the star lattice. */
    void newRoot() {
        ui400 = getResources().getFont(R.font.alexandria_regular);
        ui500 = getResources().getFont(R.font.alexandria_medium);
        ui600 = getResources().getFont(R.font.alexandria_semibold);
        // Amiri for everything large in both languages (it has Latin letters), so titles and numbers are one family;
        // the English titles were in Marcellus, whose digits run from narrow to wide. Bold for the Arabic names only.
        display = getResources().getFont(R.font.amiri_regular);
        displayBold = t.arabicScript() ? getResources().getFont(R.font.amiri_bold) : display;
        sacred = getResources().getFont(R.font.amiri_regular);

        root = new FrameLayout(this);
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        root.setBackground(Ui.starLattice(this));
        root.setClipChildren(false);
    }

    /**
     * The arch on the start side: crown verse (never focusable, never covered), reference, rule, then the info
     * column at infoTop: label, prayer name, countdown and two sub lines.
     */
    void buildArch(float infoTop, float subSize, float subGap, int sub2Color, float sub2Gap) {
        arch = new ArchView(this);
        place(arch, Tokens.ARCH_X, Tokens.ARCH_Y, Tokens.ARCH_W, Tokens.ARCH_H);

        TextView verse = text(sacred, 32, Tokens.GOLD_400);
        verse.setText("إِنَّ الصَّلَاةَ كَانَتْ\nعَلَى الْمُؤْمِنِينَ\nكِتَابًا مَّوْقُوتًا");
        verse.setGravity(Gravity.CENTER_HORIZONTAL);
        verse.setTextDirection(View.TEXT_DIRECTION_RTL);
        verse.setLineHeight(Ui.ipx(32 * 1.55f));
        place(verse, 156, 238, 480, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView ref = text(ui400, 20, Tokens.MIST_400);
        ref.setText(t.ref());
        ref.setGravity(Gravity.CENTER_HORIZONTAL);
        place(ref, 156, 410, 480, ViewGroup.LayoutParams.WRAP_CONTENT);

        View rule = new View(this);
        rule.setBackgroundColor(Tokens.GOLD_400);
        rule.setAlpha(0.6f);
        place(rule, 336, 460, 120, 2);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_HORIZONTAL);
        archLabel = caps(text(ui400, 22, Tokens.MIST_400));
        archName = text(displayBold, 88, Tokens.GOLD_400);
        archCount = countText(120);
        archSub = text(ui400, subSize, Tokens.MIST_400);
        archSub2 = text(ui400, 24, sub2Color);
        for (TextView v : new TextView[] {archLabel, archName, archCount, archSub, archSub2}) {
            v.setGravity(Gravity.CENTER_HORIZONTAL);
            info.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        archCount.setSingleLine(true);
        ((LinearLayout.LayoutParams) archSub.getLayoutParams()).topMargin = Ui.ipx(subGap);
        ((LinearLayout.LayoutParams) archSub2.getLayoutParams()).topMargin = Ui.ipx(sub2Gap);
        lineHeight(archName, 1.15f);
        lineHeight(archCount, 1f);
        info.setClipChildren(false);
        place(info, 136, infoTop, ARCH_INFO_W, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** The arch's info column, on Home and on Prayer day. */
    static final int ARCH_INFO_W = 520;

    /**
     * The arch countdown's text: Amiri's digits, all one width, so hh:mm:ss is always the same width: centred in the
     * arch, never moving with the seconds, one size.
     */
    TextView countText(float size) {
        TextView v = text(sacred, size, Tokens.PLASTER_100);
        v.setTextDirection(View.TEXT_DIRECTION_LTR);
        return v;
    }

    /**
     * The arch countdown to day's next prayer, always hh:mm:ss (07:44:28, 00:05:28), and the arch outline turns
     * gold-200 inside the last {@link Tokens#APPROACH_MIN} minutes.
     *
     * @return seconds left
     */
    long renderCountdown(PrayerDay day, Date now, boolean archFocused) {
        long left = secondsUntil(day.nextTime, now);
        archCount.setText(t.d(duration(left, true)));
        boolean approaching = left <= Tokens.APPROACH_MIN * 60L;
        arch.setState(approaching ? ArchView.APPROACHING : (archFocused ? ArchView.FOCUSED : ArchView.REST));
        return left;
    }

    static long secondsUntil(Date when, Date now) {
        return Math.max(0, (when.getTime() - now.getTime() + 999) / 1000);
    }

    /**
     * hh:mm:ss, or hh:mm without seconds: two digits a field always, so a count keeps one shape and 00:42 is never
     * read as 42 hours (it was mm:ss under an hour and h:mm above). Western digits; pass through t.d().
     */
    static String duration(long seconds, boolean withSeconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return withSeconds ? String.format(Locale.US, "%02d:%02d:%02d", h, m, s) : String.format(Locale.US, "%02d:%02d", h, m);
    }

    /** "Wednesday 21 October 2026" in the screen's language and digits. */
    String gregorian(Date now) {
        return t.d(new SimpleDateFormat("EEEE d MMMM yyyy", dateLocale(t)).format(now));
    }

    /** "10 Jumada al-Ula 1448 AH". */
    String hijri(PrayerDay day) {
        return t.d(day.hijriDay + " " + t.hijriMonths()[day.hijriMonth] + " " + day.hijriYear + t.hijriSuffix());
    }

    /**
     * A clock time in the 12/24-hour format from Afnon Settings, by default the TV's own (Settings → Date & time);
     * call {@link #readClockFormat()} first. In 12-hour the marker (ص/م, AM/PM) is drawn at markerScale of the digits; 1 for running text.
     */
    CharSequence time(Date d, float markerScale) {
        return clock(t, d, h24, markerScale);
    }

    static CharSequence clock(Text t, Date d, boolean h24, float markerScale) {
        if (h24) return t.d(new SimpleDateFormat("HH:mm", Locale.US).format(d));
        String hm = t.d(new SimpleDateFormat("h:mm", Locale.US).format(d));
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        SpannableString s = new SpannableString(hm + " " + t.ampm(c.get(Calendar.AM_PM) == Calendar.AM));
        if (markerScale != 1f) s.setSpan(new RelativeSizeSpan(markerScale), hm.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    void readClockFormat() {
        h24 = Prefs.clock24(this);
    }

    /** The key hints along the bottom, 40px apart. */
    void setHints(LinearLayout hints, List<String> h) {
        hints.removeAllViews();
        for (int i = 0; i < h.size(); i++) {
            TextView v = text(ui400, 22, Tokens.MIST_400);
            v.setText(h.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.setMarginStart(Ui.ipx(40));
            hints.addView(v, lp);
        }
    }

    /** The connected Wi-Fi network's name, or null when there is none or it can't be read. */
    String ssid() {
        String ssid = null;
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm == null ? null : wm.getConnectionInfo();
            if (info != null) ssid = info.getSSID();
        } catch (SecurityException ignored) {
            // Location not granted: no name.
        }
        if (ssid == null || ssid.isEmpty() || "<unknown ssid>".equals(ssid)) return null;
        if (ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) ssid = ssid.substring(1, ssid.length() - 1);
        return ssid;
    }

    void startSafely(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Nothing to open on this firmware; stay where we are.
        }
    }

    // ---------------------------------------------------------------- a language chosen: keep it or go back

    /** How long the question waits before the language goes back by itself. */
    static final long KEEP_LANGUAGE_MS = 10_000;
    /**
     * A language just chosen (Home's language button, Settings) and waiting for Keep: the setting before it, the one
     * chosen, and when it goes back (uptime). In memory only: should Awan TV restart in between, the chosen one
     * stays, so nothing saved can bring a language back later.
     */
    private static String undoLocale, undoChosen;
    private static long undoAt;
    private FrameLayout keepLayer;
    private TextView keepLine;
    private FocusTile keepYes, keepNo;
    private int keepSeconds = -1;
    /** The key pressed down while the question is up: only its release answers (not the pick's own release). */
    private int keepDown = -1;
    private final Runnable keepCount = this::keepStep;

    /** Whether this screen asks Keep or Go back over itself (Home and Settings); others let the time run out. */
    boolean asksKeep() {
        return false;
    }

    /** The screen's key hints, for the question's own; null for none. */
    LinearLayout hintsView() {
        return null;
    }

    /** After the question (kept, or gone back) and after a pick that left the language as it was: focus back. */
    void languageSettled() {
    }

    /**
     * The language list, Home's and Settings': the globe, the word and "Language" at its head; "TV (its language
     * now)", then each language by its own name, all at the screen's start edge; the setting marked ✓. pick gets the
     * index in Languages.values().
     */
    ListLayer languageList(java.util.function.IntConsumer pick) {
        String[] values = Languages.values();
        CharSequence[] items = new CharSequence[values.length];
        String tv = t.languageTv(Languages.name(languageTag("system", tvLanguage())));
        // "(English now)" a little smaller and softer than "TV", as an aside.
        int open = tv.indexOf('(');
        if (open > 0) {
            SpannableString sp = new SpannableString(tv);
            sp.setSpan(new RelativeSizeSpan(22f / 26), open, tv.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new android.text.style.ForegroundColorSpan(Tokens.MIST_400), open, tv.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            items[0] = sp;
        } else {
            items[0] = tv;
        }
        System.arraycopy(Languages.NAMES, 0, items, 1, Languages.NAMES.length);
        int sel = java.util.Arrays.asList(values).indexOf(prefs().getString("locale", "system"));
        if (sel < 0) sel = 0;   // a language this version does not know reads as the TV's (languageTag)

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Icon globe = new Icon(this, Icon.GLOBE, 1.5f);
        globe.setColor(Tokens.GOLD_400);
        head.addView(globe, new LinearLayout.LayoutParams(Ui.ipx(40), Ui.ipx(40)));
        TextView word = text(displayBold, Tokens.FONT_SIZE_DISPLAY_S, Tokens.GOLD_400);
        word.setText(t.language());
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
        wlp.setMarginStart(Ui.ipx(16));
        word.setLayoutParams(wlp);
        head.addView(word);
        lineHeight(word, 1.2f);
        String both = t.languageBoth(), own = t.language();
        if (!both.equals(own)) {
            TextView en = text(ui400, 30, Tokens.MIST_400);
            en.setText(both.substring(own.length()).trim());
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-2, -2);
            elp.setMarginStart(Ui.ipx(12));
            head.addView(en, elp);
        }
        ListLayer l = new ListLayer(this, head, items, sel, true, 760, pick);
        alignToScreen(l.layer);
        return l;
    }

    /**
     * Saves the language picked (a Languages.values() value) and builds the screen in it. When the screen's language
     * changed, asks whether to keep it: one who picked a language they cannot read is back in theirs in ten seconds
     * without reading a word.
     */
    void chooseLanguage(String value) {
        SharedPreferences p = prefs();
        String was = p.getString("locale", "system");
        if (value.equals(was)) {
            languageSettled();
            return;
        }
        String tv = tvLanguage();
        boolean differs = !languageTag(was, tv).equals(languageTag(value, tv));
        p.edit().putString("locale", value).apply();
        if (differs) {
            undoLocale = was;
            undoChosen = value;
            undoAt = android.os.SystemClock.uptimeMillis() + KEEP_LANGUAGE_MS;
        }
        loadSettings(null);
        build();
        if (differs) keepStep();
        else languageSettled();
    }

    /** Once a second (on the second of the countdown) while a choice waits: the question, its seconds, the time out. */
    private void keepStep() {
        tick.removeCallbacks(keepCount);
        if (undoChosen == null) {
            if (keepLayer != null) {
                closeKeep();
                languageSettled();
            }
            return;
        }
        long left = undoAt - android.os.SystemClock.uptimeMillis();
        if (left <= 0) {
            undoLanguage();
            return;
        }
        // Built again meanwhile (an import, the theme turning): the question again over the new root.
        if (asksKeep() && (keepLayer == null || keepLayer.getParent() != root)) showKeep();
        int seconds = (int) ((left + 999) / 1000);
        if (keepLine != null && seconds != keepSeconds) {
            keepSeconds = seconds;
            keepLine.setText(t.languageGoesBack(seconds, Languages.name(languageTag(undoLocale, tvLanguage()))));
        }
        tick.postDelayed(keepCount, (left - 1) % 1000 + 1);
    }

    /** The question: a card as Settings' confirmations, Keep focused, the seconds counting down under the title. */
    private void showKeep() {
        if (keepLayer != null && keepLayer.getParent() != null) ((ViewGroup) keepLayer.getParent()).removeView(keepLayer);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.ipx(48), Ui.ipx(40), Ui.ipx(48), Ui.ipx(36));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Tokens.LAPIS_800);
        bg.setCornerRadius(Ui.px(24));
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
        card.setBackground(bg);
        card.setClipChildren(false);
        card.setClipToPadding(false);

        TextView title = text(displayBold, Tokens.FONT_SIZE_DISPLAY_S, Tokens.GOLD_400);
        title.setText(t.languageKeepTitle());
        title.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        lineHeight(title, 1.2f);
        card.addView(title);
        keepLine = text(ui400, 22, Tokens.MIST_400);
        keepLine.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        lineHeight(keepLine, 1.55f);
        card.addView(keepLine);
        keepSeconds = -1;

        LinearLayout buttons = new LinearLayout(this);
        float r = FocusTile.REACH;
        keepYes = keepButton(t.languageKeep());
        keepNo = keepButton(t.languageUndo());
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
        b.setMarginStart(Ui.ipx(24));
        buttons.addView(keepYes, a);
        buttons.addView(keepNo, b);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.ipx(30 - r);
        blp.setMarginStart(-Ui.ipx(r));
        blp.setMarginEnd(-Ui.ipx(r));
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        card.addView(buttons, blp);

        keepLayer = new FrameLayout(this);
        keepLayer.setBackgroundColor(Tokens.SCRIM_MENU);
        keepLayer.setClickable(true);
        keepLayer.setClipChildren(false);
        keepLayer.addView(card, new FrameLayout.LayoutParams(Ui.ipx(760), -2, Gravity.CENTER));
        root.addView(keepLayer, new FrameLayout.LayoutParams(-1, -1));
        keepDown = -1;
        LinearLayout hints = hintsView();
        if (hints != null) {
            List<String> h = new java.util.ArrayList<>();
            h.add(t.hintChoose());
            h.add(t.hintBackUndo());
            setHints(hints, h);
            hints.bringToFront();
        }
        FocusTile first = keepYes;
        root.post(() -> { if (keepYes == first) first.requestFocus(); });
    }

    private FocusTile keepButton(String label) {
        FocusTile b = new FocusTile(this, 14);
        b.setRest(Tokens.LAPIS_700, 0, 0);
        TextView v = text(ui500, 26, Tokens.PLASTER_100);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        b.addView(v, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> v.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        b.setFocusable(true);
        return b;
    }

    private void closeKeep() {
        tick.removeCallbacks(keepCount);
        if (keepLayer != null && keepLayer.getParent() != null) ((ViewGroup) keepLayer.getParent()).removeView(keepLayer);
        keepLayer = null;
        keepLine = null;
        keepYes = null;
        keepNo = null;
    }

    private void keepLanguage() {
        undoLocale = null;
        undoChosen = null;
        closeKeep();
        languageSettled();
    }

    /** Back to the language before; unless the setting changed again meanwhile (an import), which then stays. */
    private void undoLanguage() {
        String back = undoLocale, chosen = undoChosen;
        undoLocale = null;
        undoChosen = null;
        closeKeep();
        SharedPreferences p = prefs();
        if (chosen != null && chosen.equals(p.getString("locale", "system"))) p.edit().putString("locale", back).apply();
        loadSettings(null);
        build();
        languageSettled();
    }

    /**
     * Keys while the question is up, first in the screen's dispatchKeyEvent: true when taken. ◀▶ between the two,
     * OK answers, Back goes back; the volume stays the TV's, and every other key waits for the answer.
     */
    boolean keepKey(KeyEvent e) {
        if (undoChosen == null || !asksKeep()) return false;
        if (keepLayer == null || keepLayer.getParent() != root) keepStep();
        if (keepLayer == null) return false;
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down) (keepYes.isFocused() ? keepNo : keepYes).requestFocus();
            return true;
        }
        if (ok || k == KeyEvent.KEYCODE_BACK) {
            if (down) {
                if (e.getRepeatCount() == 0) keepDown = k;
            } else if (keepDown == k && !e.isCanceled()) {
                keepDown = -1;
                if (ok && !keepNo.isFocused()) keepLanguage();
                else undoLanguage();
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * One line that grows smaller to fit its width once laid out, as far as minScale of its size, then ends in "…"
     * (1.18.0c: help lines and the screensaver's gold line, for longer languages too; CropCheck lists the "…").
     * Its width must not follow its text (MATCH_PARENT or a set width), or it would never grow back.
     */
    static void fitWidth(TextView v, float minScale) {
        final float base = v.getTextSize();
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.addOnLayoutChangeListener((x, l, tp, r, b, ol, ot, or, ob) -> {
            int room = v.getWidth() - v.getTotalPaddingLeft() - v.getTotalPaddingRight();
            CharSequence s = v.getText();
            if (room <= 0 || s == null || s.length() == 0) return;
            android.text.TextPaint p = new android.text.TextPaint(v.getPaint());
            p.setTextSize(base);
            float w = android.text.Layout.getDesiredWidth(s, p);
            float size = w > room ? base * Math.max(minScale, room / w) : base;
            if (Math.abs(v.getTextSize() - size) > 0.5f) v.post(() -> v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size));
        });
        // A new text in a view of set size lays nothing out again (TextView only redraws), so the fit above would
        // keep the old text's size: ask for a layout on every change (1.20.0b: Home's cell tags and Hijri date change
        // in place every minute and day).
        v.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int n, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int n, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) { v.requestLayout(); }
        });
    }

    /**
     * Every text in a list at the screen's start edge, whatever its own script: a list of languages mixes them, and
     * each would otherwise sit at its own script's start ("العربية" at the right of an English list).
     */
    static void alignToScreen(View v) {
        if (v instanceof TextView) v.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) alignToScreen(g.getChildAt(i));
        }
    }

    TextView text(Typeface tf, float sizeDesignPx, int color) {
        TextView v = new TextView(this);
        v.setTypeface(tf);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(sizeDesignPx));
        v.setTextColor(color);
        return v;
    }

    /**
     * CSS line-height for a single line already in a LinearLayout: the glyph box is centred in a line box of
     * lh × size. Android keeps the font's full ascent and descent instead, which for Amiri is far taller than the
     * design, so negative margins take back the difference. Safe to call again after the text size changes.
     */
    static void lineHeight(TextView v, float lh) {
        v.setIncludeFontPadding(false);
        android.graphics.Paint.FontMetricsInt fm = v.getPaint().getFontMetricsInt();
        int half = Math.round((v.getTextSize() * lh - (fm.descent - fm.ascent)) / 2);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        Object base = v.getTag();
        int[] b = base instanceof int[] ? (int[]) base : new int[] {lp.topMargin, lp.bottomMargin};
        v.setTag(b);
        lp.topMargin = b[0] + half;
        lp.bottomMargin = b[1] + half;
        v.setLayoutParams(lp);
    }

    /** English caps labels get 0.2em tracking; Arabic is never letter-spaced. */
    TextView caps(TextView v) {
        if (!t.arabicScript()) v.setLetterSpacing(0.2f);
        return v;
    }

    /** Places a view at a design rect, measured from the start edge, so Arabic mirrors it. */
    void place(View v, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size(w), size(h), Gravity.TOP | Gravity.START);
        lp.setMarginStart(Ui.ipx(x));
        lp.topMargin = Ui.ipx(y);
        root.addView(v, lp);
    }

    /** Places a view measured from the end edge; a focus tile grows by the ring's reach. */
    void placeEnd(View v, float endInset, float y, float w, float h) {
        float r = v instanceof FocusTile ? FocusTile.REACH : 0;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            w < 0 ? (int) w : size(w + 2 * r), size(h + 2 * r), Gravity.TOP | Gravity.END);
        lp.setMarginEnd(Ui.ipx(endInset - r));
        lp.topMargin = Ui.ipx(y - r);
        root.addView(v, lp);
    }

    /** Places a focus tile by its design rect; the view grows by the ring's reach on each side. */
    void placeTile(FocusTile v, float x, float y, float w, float h) {
        float r = FocusTile.REACH;
        place(v, x - r, y - r, w + 2 * r, h + 2 * r);
    }

    static int size(float designPx) {
        return designPx < 0 ? (int) designPx : Ui.ipx(designPx);
    }
}
