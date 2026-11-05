package app.awanpro.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.icu.util.IslamicCalendar;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Madhab;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Afnon Home: the mihrab arch on the start side, the courtyard (clock, prayer strip, apps) beside it. */
public class MainActivity extends Screen {

    // Focus nodes, in the order of design/afnon-mihrab/design/FocusMap.dc.html.
    // Section labels sit 10px above the design (478 / 722) so the focus ring and 1.06 scale never touch them.
    // Six square favourites and six smaller squares in More, so the rows moved.
    // The labels 12px higher and without the font's padding, so a focused square (ring and scale)
    // no longer covers their lower part.
    private static final int LABEL_ABOVE_FAV = 450, LABEL_ABOVE_MORE = 688;
    private static final float FAV_Y = 504, MORE_Y = 742, STRIP_Y = 270, DATES_Y = 176;
    private static final int FAVS = 6, MORES = 6;

    // Awan TV adds SAVER: "Screensaver now", between Wi-Fi and Settings.
    private static final int ARCH = 0, WIFI = 1, SETTINGS = 2, F0 = 3, M0 = F0 + FAVS, ALL = M0 + MORES, RADIO = ALL + 1,
        SAVER = RADIO + 1, NODES = SAVER + 1;

    private final View[] nodes = new View[NODES];
    private final TextView[] cellTag = new TextView[6], cellName = new TextView[6], cellTime = new TextView[6];
    private final View[] cell = new View[6];
    /** The bar along the bottom of the current prayer's cell: how far it is to the next time. */
    private final View[] cellBar = new View[6], cellFill = new View[6];
    /** The short arch at the top of the start side, the Hijri month's calendar below. */
    // The arch, the calendar and the button one column, 600 wide, lapis with the
    // arch's 3px gold edge.
    private static final float ARCH_TOP = 40, ARCH_SHORT_H = 440, CAL_Y = 500, CAL_H = 386, DAY_BUTTON_Y = 912, DAY_BUTTON_H = 80;
    private LinearLayout calendar;
    private String calendarFor = "";
    private final String[] nodeLabel = new String[NODES];

    private TextView clock, gdate, hdate, cityLine;
    /** The weather beside the clock. */
    private WeatherBadge weather;
    private final Runnable onWeather = () -> { if (weather != null) weather.update(); };
    /**
     * The last key on Home, and whether Awan TV opens its own screensaver after idle minutes here (its
     * screensaver is on, and the TV's own is not Awan TV's); looked up each minute.
     */
    private long lastKey = System.currentTimeMillis();
    private boolean ownSaver;
    private long ownSaverAt = -1;
    private LinearLayout hints;
    private PrayerDay day;
    private long lastMinute = -1;
    private int lastFav = F0;
    private boolean archFocused;
    /** What Home was built with, so it rebuilds when All apps changes favourites or apps come and go. */
    private String builtApps = "";
    /** The reminder card (design/afnon-mihrab/design/Home.dc.html, mode "approach") and the prayer it is for. */
    private LinearLayout card;
    private FocusTile cardOk, cardDay;
    private Date cardFor;
    /** The radio's header pill and, while open, its card. */
    private Icon radioIcon, playIcon;
    private RadioBars pillBars, cardBars;
    private TextView radioLabel, cardStatus, playLabel;
    private LinearLayout radioCard;
    private FocusTile cardPlay, cardStation, cardDark;
    /** The radio card's station name, renamed when the Station list picks another. */
    private TextView cardTitle;
    /** The Station list over Home (its groups, then a group's stations); null when closed. */
    private ListLayer stations;
    /** Back in the Station list: to its groups from a group, else null (closes it, focus on Station). */
    private Runnable stationsBack;
    private final Radio.Listener onRadio = state -> styleRadio();
    /** Test only (shiftTo): how far Home's clock is moved. */
    private long shift;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        takeSaverBefore(getIntent());
        runTest(getIntent());
    }

    /**
     * Tools/AwanTV-install.ps1 makes Awan TV the TV's screensaver before the app ever runs, so it hands
     * over the one it replaced: {@code --es saver_before <component>}. Kept to put back later ({@link Saver}).
     */
    private void takeSaverBefore(Intent i) {
        String before = i == null ? null : i.getStringExtra("saver_before");
        if (before == null) return;
        i.removeExtra("saver_before");
        Saver.remember(this, before);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean changed = loadSettings(intent);
        if (changed) build();
        // Home key from anywhere: back to Home with focus on the first favourite app. Posted, because the window puts back its previously focused view when it regains
        // focus after onNewIntent.
        root.post(this::focusStart);
        takeSaverBefore(intent);
        runTest(intent);
    }

    /**
     * Checking reminders on the TV without waiting for a prayer: `--es test reminder` shows Home's card now, with
     * the spoken reminder if it is on; `banner`, `call` and `announce` come from the remote buttons service 8 s
     * later, time enough to open another app.
     * `--es station <id>` (ids in Radio.STATIONS) picks a radio station as Settings does, before any test runs.
     */
    private void runTest(Intent i) {
        String station = i == null ? null : i.getStringExtra("station");
        if (station != null) {
            i.removeExtra("station");
            for (int k = 0; k < Radio.STATIONS.length; k++) if (Radio.STATIONS[k].id.equals(station)) Radio.switchTo(this, k);
        }
        String kind = i == null ? null : i.getStringExtra("test");
        if (kind == null) return;
        i.removeExtra("test");
        if ("reminder".equals(kind)) root.post(() -> {
            showReminder();
            if (Prefs.remindVoice(this)) Announce.before(this, day.nextIndex, day.friday);
        });
        else if ("friday".equals(kind) || "ramadan".equals(kind) || "now".equals(kind)) shiftTo(kind);
        else if ("radio".equals(kind)) Radio.toggle(this);
        else if (RemoteKeys.watch != null) RemoteKeys.watch.test(kind, 8000);
    }

    /**
     * Test only: Home's clock jumps to the coming Friday at 10:30 (Jumu'ah next) or to 15 Ramadan at 16:30
     * (iftar next) and keeps ticking from there; "now" puts it back. Nothing else in Afnon is shifted.
     */
    private void shiftTo(String kind) {
        Calendar c = Calendar.getInstance();
        if ("friday".equals(kind)) {
            while (c.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) c.add(Calendar.DAY_OF_MONTH, 1);
            c.set(Calendar.HOUR_OF_DAY, 10);
            c.set(Calendar.MINUTE, 30);
        } else if ("ramadan".equals(kind)) {
            IslamicCalendar h = new IslamicCalendar();
            h.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            if (h.get(IslamicCalendar.MONTH) > IslamicCalendar.RAMADAN) h.add(IslamicCalendar.YEAR, 1);
            h.set(IslamicCalendar.MONTH, IslamicCalendar.RAMADAN);
            h.set(IslamicCalendar.DAY_OF_MONTH, 15);
            c.setTime(h.getTime());
            c.set(Calendar.HOUR_OF_DAY, 16);
            c.set(Calendar.MINUTE, 30);
        }
        shift = "now".equals(kind) ? 0 : c.getTimeInMillis() - System.currentTimeMillis();
        lastMinute = -1;
        day = null;
    }

    private Date now() {
        return new Date(System.currentTimeMillis() + shift);
    }

    @Override
    protected void onResume() {
        if (!builtApps.equals(appsKey())) build();
        lastMinute = -1;
        updateWifi();
        super.onResume();
        Reminders.home = this::showReminder;
        // Home shows the iqamah itself, so the countdown over other apps goes at once.
        if (RemoteKeys.watch != null) RemoteKeys.watch.refreshCounter();
        Radio.listen(onRadio);
        styleRadio();
        // An app installed or removed, or a new default Home, since the service last looked.
        shown = true;
        shownAt = android.os.SystemClock.uptimeMillis();
        HomeGuard.front(this, true);
        if (RemoteKeys.guard != null) RemoteKeys.guard.refresh();
        // The service on in the settings but not running (a TCL BeyondTV, Android 11): bind it again.
        ServiceCheck.soon(this, "home");
        Saver.remember(this);
        Weather.listener = onWeather;
        try {
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            if (cm != null) cm.registerDefaultNetworkCallback(onNetwork);
        } catch (RuntimeException ignored) {
            // Already registered, or not allowed: the button is still set on each return to Home.
        }
        lastKey = System.currentTimeMillis();
        ownSaverAt = -1;
        SelfUpdate.listen(this::selfUpdateChanged);
        updateLeft = false;
        if (!askedCity && !Prefs.citySet(this)) {
            askedCity = true;
            startSafely(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.FIRST_RUN, true));
        } else {
            // After an update: what is new, once, over Home; else a newer version out, if it is due.
            showNews();
            if (news == null) showUpdate();
        }
    }

    @Override
    protected void onPause() {
        shown = false;
        HomeGuard.front(this, false);
        try {
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            if (cm != null) cm.unregisterNetworkCallback(onNetwork);
        } catch (RuntimeException ignored) {
            // Was not registered.
        }
        if (Weather.listener == onWeather) Weather.listener = null;
        Reminders.home = null;
        Radio.unlisten(onRadio);
        SelfUpdate.listen(null);
        super.onPause();
    }

    /** What's new after an update, as a layer over Home (NewsActivity.card); null when not shown. */
    private NewsActivity.Card news;

    /**
     * What's new once after an update, the location set first. A layer of Home's own, not an activity over it: HomeGuard
     * opens Home again when the stock home seems to stay in front, and that closed an activity over Home (a TCL TV).
     * Seen only when closed, so a rebuild of Home (a new photo, the theme at Maghrib) shows it again until then.
     */
    private void showNews() {
        if (news != null || card != null || radioCard != null || !Prefs.citySet(this)) return;
        java.util.List<String> versions = NewsActivity.pending(this);
        if (versions.isEmpty()) return;
        news = NewsActivity.card(this, versions, false, this::closeNews);
        root.addView(news.layer, new FrameLayout.LayoutParams(-1, -1));
        for (View n : nodes) if (n != null) n.setFocusable(false);
        root.post(news.ok::requestFocus);
        layerHints();
    }

    private void closeNews() {
        if (news == null) return;
        Prefs.of(this).edit().putString(NewsActivity.SEEN, NewsActivity.current(this)).apply();
        root.removeView(news.layer);
        news = null;
        for (View n : nodes) if (n != null) n.setFocusable(true);
        focusStart();
    }

    /** A newer version out, as a layer over Home (UpdateNotice); null when not shown. */
    private UpdateNotice.Card update;

    /**
     * A newer version, when due (UpdateNotice.due), or Update now's download or install going on (SelfUpdate): never
     * with What's new, a reminder or the radio card.
     */
    private void showUpdate() {
        if (update != null || news != null || card != null || radioCard != null || !Prefs.citySet(this)) return;
        // Closed while downloading: the minute tick must not bring it back; Home's next resume does.
        if (updateLeft && SelfUpdate.busy()) return;
        String v = SelfUpdate.busy() || SelfUpdate.state() == SelfUpdate.FAILED ? SelfUpdate.version() : UpdateNotice.due(this);
        if (v == null) return;
        String version = v;
        update = UpdateNotice.card(this, version, new UpdateNotice.Actions() {
            public void update() { SelfUpdate.start(MainActivity.this, version); }
            public void later() { closeUpdate(false); }
            public void mute() { closeUpdate(true); }
            public void cancel() { SelfUpdate.cancel(); }
            public void confirm() { SelfUpdate.confirm(MainActivity.this); }
        });
        root.addView(update.layer, new FrameLayout.LayoutParams(-1, -1));
        for (View n : nodes) if (n != null) n.setFocusable(false);
        root.post(update.ok::requestFocus);
        layerHints();
    }

    /** Closed while the download went on: not shown again until Home comes back (onResume). */
    private boolean updateLeft;

    /** SelfUpdate moved on: the window again in its new state, or only its line and bar while downloading. */
    private void selfUpdateChanged() {
        if (update == null) {
            if (shown && SelfUpdate.busy()) showUpdate();
            return;
        }
        if (UpdateNotice.mode(update.version) == update.mode) {
            UpdateNotice.progress(update, t);
            return;
        }
        dropUpdate();
        showUpdate();
    }

    /** A layer over Home (What's new, a newer version): its own key hints, over the dimmed screen, not Home's. */
    private void layerHints() {
        java.util.List<String> h = new java.util.ArrayList<>();
        h.add(t.hintChoose());
        h.add(t.hintBackClose());
        setHints(hints, h);
        hints.bringToFront();
    }

    /** OK or Back: again in a week; mute: not for this version. */
    private void closeUpdate(boolean mute) {
        if (update == null) return;
        updateLeft = SelfUpdate.busy();
        SelfUpdate.reset();
        UpdateNotice.closed(this, update.version, mute);
        dropUpdate();
        focusStart();
    }

    /** Taken away unread (a reminder came, or Home was rebuilt): still due. */
    private void dropUpdate() {
        if (update == null) return;
        root.removeView(update.layer);
        update = null;
        for (View n : nodes) if (n != null) n.setFocusable(true);
    }

    /** The gold mark on Settings' button while a newer version is out (UpdateNotice.mark). */
    private View settingsMark;

    private void refreshMark() {
        if (settingsMark != null) settingsMark.setVisibility(UpdateNotice.newer(this) != null ? View.VISIBLE : View.GONE);
    }

    /** Home is the screen in front (between onResume and onPause), for HomeGuard. */
    static volatile boolean shown;
    static volatile long shownAt;
    /** When Home last opened something itself (an app, Settings): HomeGuard leaves that be. */
    static volatile long launchedAt;

    @Override
    public void startActivity(Intent intent, android.os.Bundle options) {
        launchedAt = android.os.SystemClock.uptimeMillis();
        super.startActivity(intent, options);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Home went behind something: if that was the TV's own home screen after quick Home presses,
        // whose window event can come before Home's own, HomeGuard brings Home back.
        if (RemoteKeys.guard != null) RemoteKeys.guard.homeStopped();
    }

    /** The city list has been offered on this launch (a new install has none picked yet). */
    private boolean askedCity;

    @Override
    void onTick() {
        render(false);
        // The prayer the card was about has arrived (the call takes over) or the card outlived its prayer.
        if (card != null && day != null && !day.nextTime.equals(cardFor)) dismissReminder();
        shift();
        idle();
        Updates.refresh(this);
        refreshMark();
        // The daily answer may come while Home is open: told when someone is at the remote (a key this minute).
        if (shown && System.currentTimeMillis() - lastKey < 60_000) showUpdate();
    }

    /**
     * Where the TV's own screensaver is not Awan TV's (Google TV without the adb step), Home keeps the
     * screen on and opens Awan TV's screensaver itself after the chosen minutes without a key
     * (Settings > Awan TV > Screensaver after). The screensaver then lets the TV sleep as it is set to.
     */
    private void idle() {
        long now = System.currentTimeMillis();
        if (ownSaverAt < 0 || now - ownSaverAt >= 60_000) {
            ownSaverAt = now;
            boolean want = Prefs.saver(this) && !Saver.isOurs(this);
            if (want != ownSaver) {
                ownSaver = want;
                if (want) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
        if (!ownSaver || now - lastKey < Prefs.saverIdle(this) * 60_000L) return;
        long since = lastKey;
        lastKey = now;
        startSafely(new Intent(this, SaverActivity.class).putExtra(SaverActivity.IDLE_SINCE, since));
    }

    /** The minute Home's layout last moved. */
    private long shiftedAt = -1;

    /**
     * Burn-in care (the method of Masjid TV, github.com/obaid/masjid-tv-tcl, MIT): every three minutes
     * the whole of Home eases a few pixels to a new spot, so nothing static sits on the same pixels all day.
     */
    private void shift() {
        long minute = System.currentTimeMillis() / 60_000;
        if (root == null || minute == shiftedAt || minute % 3 != 0) return;
        shiftedAt = minute;
        java.util.Random r = new java.util.Random(minute);
        root.animate().translationX(Ui.px(r.nextInt(13) - 6)).translationY(Ui.px(r.nextInt(9) - 4))
            .setDuration(4000).start();
    }

    // ---------------------------------------------------------------- layout

    @Override
    void build() {
        newRoot();
        // The day's photo under the theme's colour instead of the pattern (Settings > Display), and the
        // right column a little see-through over it.
        homePhoto = null;
        panelColor = Tokens.LAPIS_800;
        if (Prefs.background(this) == Photos.PHOTOS) {
            Date at = new Date();
            PrayerDay d0 = day != null ? day : new PrayerDay(this, at);
            Photos.Photo p = Photos.forHome(this, Photos.turn(this, true, d0, at));
            android.graphics.drawable.Drawable d = p == null ? null : Photos.homeBackground(this, p);
            if (d != null) {
                homePhoto = p;
                panelColor = Photos.panel(p);
                root.setBackground(d);
            }
        }
        card = null;   // a reminder card went with the old root
        radioCard = null;
        stations = null;
        stationsBack = null;
        // The short arch and the month's calendar instead of Afnon's tall arch.
        buildShortArch();
        buildCalendar();

        nodes[ARCH] = archButton();

        // Courtyard: clock and dates, then Wi-Fi and Settings at the end side.
        LinearLayout when = new LinearLayout(this);
        when.setOrientation(LinearLayout.VERTICAL);
        // Amiri's digits, one width (as the arch's countdown): the English display font's run from narrow to wide, so
        // the clock changed width every minute and the weather beside it shifted.
        clock = text(sacred, 96, Tokens.PLASTER_100);
        clock.setIncludeFontPadding(false);
        clock.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        // Under the clock one line across the courtyard: the Hijri date at the start, the city and the day
        // in the middle (in mist), the Gregorian date at the end; both dates alike, a little larger than Afnon's
        // two lines.
        hdate = text(ui400, 29, Tokens.PLASTER_100);
        cityLine = text(ui400, 29, Tokens.MIST_400);
        gdate = text(ui400, 29, Tokens.PLASTER_100);
        // The weather on the clock's line, at its height: the sky in gold, the degrees in the clock's
        // font and colour.
        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.CENTER_VERTICAL);
        clockRow.setClipChildren(false);
        clockRow.addView(clock);
        weather = new WeatherBadge(this, t, sacred, 72, 80, 32, Tokens.GOLD_400, Tokens.PLASTER_100, Tokens.LINE_SOFT);
        clockRow.addView(weather);
        when.addView(clockRow);
        lineHeight(clock, 1f);
        when.setClipChildren(false);
        place(when, Tokens.COURT_X, 56, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout dates = new LinearLayout(this);
        dates.setGravity(Gravity.CENTER_VERTICAL);
        hdate.setGravity(Gravity.START);
        gdate.setGravity(Gravity.END);
        for (TextView v : new TextView[] {hdate, cityLine, gdate}) v.setSingleLine(true);
        dates.addView(hdate, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams plpd = new LinearLayout.LayoutParams(-2, -2);
        plpd.setMarginStart(Ui.ipx(24));
        plpd.setMarginEnd(Ui.ipx(24));
        dates.addView(cityLine, plpd);
        dates.addView(gdate, new LinearLayout.LayoutParams(0, -2, 1));
        place(dates, Tokens.COURT_X, DATES_Y, Tokens.COURT_W, ViewGroup.LayoutParams.WRAP_CONTENT);

        // Radio and Wi-Fi side by side, ending 20px before the Settings button.
        nodes[RADIO] = radioPill();
        nodes[WIFI] = wifiPill();
        LinearLayout pills = new LinearLayout(this);
        pills.setClipChildren(false);
        float reach = FocusTile.REACH;
        pills.addView(nodes[RADIO], new LinearLayout.LayoutParams(-2, Ui.ipx(72 + 2 * reach)));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(Ui.ipx(72 + 2 * reach), Ui.ipx(72 + 2 * reach));
        wlp.setMarginStart(Ui.ipx(20 - 2 * reach));
        pills.addView(nodes[WIFI], wlp);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        // One more round button (Screensaver now) before Settings.
        plp.setMarginEnd(Ui.ipx(1920 - (Tokens.COURT_X + Tokens.COURT_W) + 2 * (72 + 20) - reach));
        plp.topMargin = Ui.ipx(56 - reach);
        root.addView(pills, plp);
        nodes[SETTINGS] = settingsButton();
        nodes[SAVER] = saverButton();

        // The prayer strip: information only, never focusable.
        float cw = (Tokens.COURT_W - 5 * 12) / 6f;
        for (int i = 0; i < 6; i++) {
            LinearLayout v = new LinearLayout(this);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setPadding(Ui.ipx(20), Ui.ipx(16), Ui.ipx(20), Ui.ipx(16));
            cellTag[i] = text(ui400, 20, Tokens.PLASTER_100);
            cellName[i] = text(ui400, 26, Tokens.PLASTER_100);
            cellTime[i] = text(ui400, 34, Tokens.PLASTER_100);
            cellTime[i].setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
            // Every line of a cell centred.
            for (TextView tv : new TextView[] {cellTag[i], cellName[i], cellTime[i]}) {
                tv.setGravity(Gravity.CENTER_HORIZONTAL);
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            }
            cellTime[i].setFontFeatureSettings("tnum");
            cellTime[i].setSingleLine(true);
            v.addView(cellTag[i], new LinearLayout.LayoutParams(-1, 0, 1));
            v.addView(cellName[i], new LinearLayout.LayoutParams(-1, -2));
            v.addView(cellTime[i], new LinearLayout.LayoutParams(-1, -2));
            // The current prayer's bar, filled from the start side as its time goes by.
            FrameLayout bar = new FrameLayout(this);
            GradientDrawable track = new GradientDrawable();
            track.setColor(Tokens.alpha(Tokens.LAPIS_900, 0x33));
            track.setCornerRadius(Ui.px(2));
            bar.setBackground(track);
            View fill = new View(this);
            GradientDrawable fg = new GradientDrawable();
            fg.setColor(Tokens.LAPIS_900);
            fg.setCornerRadius(Ui.px(2));
            fill.setBackground(fg);
            fill.setPivotX(rtl ? Ui.px(cw - 40) : 0);
            fill.setScaleX(0);
            bar.addView(fill, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Math.max(2, Ui.ipx(4)));
            blp.topMargin = Ui.ipx(8);
            v.addView(bar, blp);
            bar.setVisibility(View.INVISIBLE);
            cellBar[i] = bar;
            cellFill[i] = fill;
            lineHeight(cellName[i], 1.1f);
            lineHeight(cellTime[i], 1.1f);
            v.setClipChildren(false);
            cell[i] = v;
            place(v, Tokens.COURT_X + i * (cw + 12), STRIP_Y, cw, 146);
        }

        TextView fav = caps(text(ui400, 22, Tokens.MIST_400));
        fav.setIncludeFontPadding(false);
        fav.setText(t.fav());
        place(fav, Tokens.COURT_X, LABEL_ABOVE_FAV, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        float fw = (Tokens.COURT_W - (FAVS - 1) * 24) / (float) FAVS;
        List<String> favs = Apps.slots(this, t);
        for (int i = 0; i < FAVS; i++) {
            nodes[F0 + i] = favouriteTile(i < favs.size() ? favs.get(i) : null, F0 + i, Tokens.COURT_X + i * (fw + 24), FAV_Y, fw);
        }

        TextView more = caps(text(ui400, 22, Tokens.MIST_400));
        more.setIncludeFontPadding(false);
        more.setText(t.more());
        place(more, Tokens.COURT_X, LABEL_ABOVE_MORE, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Six apps and All apps, seven equal squares.
        float mw = (Tokens.COURT_W - MORES * 28) / (float) (MORES + 1);
        List<String> moreApps = Apps.more(this, t, MORES);
        for (int i = 0; i < MORES; i++) {
            nodes[M0 + i] = moreTile(i < moreApps.size() ? moreApps.get(i) : null, M0 + i, Tokens.COURT_X + i * (mw + 28), MORE_Y, mw);
        }
        nodes[ALL] = allAppsTile(Tokens.COURT_X + MORES * (mw + 28), MORE_Y, mw);

        hints = new LinearLayout(this);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        place(hints, Tokens.COURT_X, 962, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        for (int i = 0; i < NODES; i++) {
            final int n = i;
            nodes[i].setOnFocusChangeListener((v, has) -> { if (has) onNodeFocused(n); else if (n == ARCH) setArchFocused(false); });
        }

        setContentView(root);
        builtApps = appsKey();
        lastMinute = -1;
        render(true);
        focusStart();
        // What's new was open over the old root: again over the new one (it is seen only once closed).
        if (news != null) {
            news = null;
            showNews();
        }
        if (update != null) {
            update = null;
            showUpdate();
        }
    }

    /**
     * Where Home starts: the first favourite app, what is opened most from Home (it was the arch, the
     * prayer times, which are on screen anyway); the arch where there is no first tile.
     */
    private void focusStart() {
        View first = nodes[F0];
        if (first != null && first.isFocusable() && first.getVisibility() == View.VISIBLE) first.requestFocus();
        else nodes[ARCH].requestFocus();
    }

    private View archButton() {
        // No place set yet (the times follow a city guessed from the time zone): "Set your location" in red, opening the
        // first run's location choice, in place of Today & Qibla, whose Qibla needs the place too.
        boolean unset = !Prefs.citySet(this);
        int rest = unset ? Tokens.STATUS_BAD : Tokens.PLASTER_100;
        FocusTile b = new FocusTile(this, 24);
        b.setRest(unset ? (Tokens.STATUS_BAD & 0x00FFFFFF) | 0x33000000 : panelColor, unset ? Tokens.STATUS_BAD : Tokens.GOLD_400, 3);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        Icon compass = new Icon(this, unset ? Icon.PIN : Icon.COMPASS, 1.8f);
        TextView label = text(unset ? ui500 : ui400, 26, rest);
        label.setText(unset ? t.setYourLocation() : t.today());
        row.addView(compass, new LinearLayout.LayoutParams(Ui.ipx(30), Ui.ipx(30)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(Ui.ipx(14));
        row.addView(label, lp);
        b.addView(row, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> {
            int c = f ? Tokens.LAPIS_900 : rest;
            label.setTextColor(c);
            compass.setColor(c);
        });
        if (unset) {
            b.setOnClickListener(v -> startSafely(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.FIRST_RUN, true)));
        } else {
            b.setOnClickListener(v -> startActivity(new Intent(this, PrayerDayActivity.class)));
        }
        nodeLabel[ARCH] = unset ? t.setYourLocation() : t.today();
        placeTile(b, Tokens.ARCH_X, DAY_BUTTON_Y, Tokens.ARCH_W, DAY_BUTTON_H);
        return b;
    }

    /**
     * A round button with the Wi-Fi icon, like Settings; the network's name is in the hint line when it
     * is focused (and in Settings > Network). The clock's line needs the room for the weather.
     */
    private View wifiPill() {
        FocusTile b = new FocusTile(this, 36);
        b.setRest(Tokens.LAPIS_800, Tokens.GOLD_400_60, 2);  // Soft gold, as the column's edges
        Icon icon = new Icon(this, Icon.WIFI, 2f);
        netIcon = icon;
        b.addView(icon, new FrameLayout.LayoutParams(Ui.ipx(30), Ui.ipx(30), Gravity.CENTER));
        b.setContentDescription(t.network());
        // Struck through in gold while there is no network or no internet (updateWifi).
        b.setLook(f -> icon.setColor(f ? Tokens.LAPIS_900 : netOff ? Tokens.GOLD_400 : Tokens.PLASTER_100));
        b.setOnClickListener(v -> startSafely(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        nodeLabel[WIFI] = t.network();
        return b;
    }

    private View settingsButton() {
        FocusTile b = new FocusTile(this, 36);
        b.setRest(Tokens.LAPIS_800, Tokens.GOLD_400_60, 2);  // Soft gold, as the column's edges
        Icon gear = new Icon(this, Icon.GEAR, 1.8f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.ipx(34), Ui.ipx(34), Gravity.CENTER);
        b.addView(gear, lp);
        b.setContentDescription(t.settings());
        b.setLook(f -> gear.setColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        b.setOnClickListener(v -> startSafely(new Intent(this, SettingsActivity.class)));
        nodeLabel[SETTINGS] = t.settings();
        // A newer version out: the gold mark on the button's corner until the TV has it.
        settingsMark = UpdateNotice.mark(this, Tokens.LAPIS_900);
        b.addView(settingsMark, new FrameLayout.LayoutParams(Ui.ipx(24), Ui.ipx(24), Gravity.TOP | Gravity.RIGHT));
        refreshMark();
        placeEnd(b, 1920 - (Tokens.COURT_X + Tokens.COURT_W), 56, 72, 72);
        return b;
    }

    /** "Screensaver now", a round button like Settings, just before it. */
    private View saverButton() {
        FocusTile b = new FocusTile(this, 36);
        b.setRest(Tokens.LAPIS_800, Tokens.GOLD_400_60, 2);  // Soft gold, as the column's edges
        Icon icon = new Icon(this, Icon.SAVER, 1.8f);
        b.addView(icon, new FrameLayout.LayoutParams(Ui.ipx(34), Ui.ipx(34), Gravity.CENTER));
        b.setContentDescription(t.saverNowRow());
        b.setLook(f -> icon.setColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        b.setOnClickListener(v -> startSafely(SaverActivity.now(this)));
        nodeLabel[SAVER] = t.saverNowRow();
        placeEnd(b, 1920 - (Tokens.COURT_X + Tokens.COURT_W) + 72 + 20, 56, 72, 72);
        return b;
    }

    /** A favourite, a square the app's icon fills, no name (the hint line names it). */
    private View favouriteTile(String pkg, int node, float x, float y, float w) {
        return iconTile(pkg, node, x, y, w, 24, 12);
    }

    /** One of More's six, the same as a favourite but smaller. */
    private View moreTile(String pkg, int node, float x, float y, float w) {
        return iconTile(pkg, node, x, y, w, 20, 10);
    }

    private View iconTile(String pkg, int node, float x, float y, float size, float radius, float inset) {
        FocusTile b = new FocusTile(this, radius);
        b.setRest(Tokens.LAPIS_800, 0, 0);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable d = pkg == null ? null : Apps.bigIcon(this, pkg);
        // Drawn once at the square's own size: a large (adaptive) icon redrawn every frame of a focus move is work
        // a weak TV GPU feels.
        int px = Ui.ipx(size - 2 * inset);
        if (d != null && px > 0) {
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
            d.setBounds(0, 0, px, px);
            d.draw(cv);
            icon.setImageBitmap(bmp);
        }
        // Rounded like the square around it, so a square, full-bleed icon (an older app's) has the
        // same soft corners as the shaped ones.
        final float corner = Ui.px(Math.max(4, radius - inset));
        icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        icon.setClipToOutline(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        int in = Ui.ipx(inset);
        lp.setMargins(in, in, in, in);
        b.addView(icon, lp);
        String name = pkg == null ? "" : Apps.name(this, t, pkg);
        b.setContentDescription(name);
        b.setOnClickListener(v -> { if (pkg != null) Apps.launch(this, pkg); });
        nodeLabel[node] = name;
        placeTile(b, x, y, size, size);
        return b;
    }

    private View allAppsTile(float x, float y, float w) {
        FocusTile b = new FocusTile(this, Tokens.RADIUS_CELL);
        b.setRest(0, Tokens.LINE_DASH, 2).setDashed();
        TextView label = text(ui400, 22, Tokens.PLASTER_100);
        // Two lines: the dashed tile is too narrow for "All apps · N" on one. Centred in its square.
        label.setText(t.all(Apps.installed(this).size()));
        label.setPadding(Ui.ipx(10), Ui.ipx(10), Ui.ipx(10), Ui.ipx(10));
        label.setGravity(Gravity.CENTER);
        label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        b.addView(label, new FrameLayout.LayoutParams(-1, -1));
        b.setLook(f -> label.setTextColor(f ? Tokens.LAPIS_900 : Tokens.PLASTER_100));
        b.setOnClickListener(v -> startActivity(new Intent(this, AllAppsActivity.class)));
        nodeLabel[ALL] = t.allTitle();
        placeTile(b, x, y, w, w);
        return b;
    }

    // ---------------------------------------------------------------- live content

    /** Called every second. Recomputes the prayer day once a minute; the countdown changes every call. */
    private void render(boolean force) {
        Date now = now();
        long minute = now.getTime() / 60000;
        if (force || minute != lastMinute || day == null) {
            lastMinute = minute;
            day = new PrayerDay(this, now);
            renderMinute(now);
        }
        if (secondsUntil(day.nextTime, now) == 0) {
            // The prayer time has arrived: move on to the next one at once.
            day = new PrayerDay(this, new Date(now.getTime() + 1000));
            renderMinute(now);
        }
        // The button that lights the arch is now under the calendar, away from it.
        renderCountdown(day, now, false);
    }

    /**
     * The short arch: the next prayer, its countdown to the second, the coming occasion
     * in gold, and Friday's or Ramadan's line. The verse, the adhan's time and the city are elsewhere on Home.
     */
    private void buildShortArch() {
        arch = new ArchView(this, true);
        arch.setFill(panelColor);
        place(arch, Tokens.ARCH_X, ARCH_TOP, Tokens.ARCH_W, ARCH_SHORT_H);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        // Centred in the arch below its crown, whether or not the occasion and Friday's or Ramadan's lines show.
        info.setGravity(Gravity.CENTER);
        archLabel = caps(text(ui400, 22, Tokens.MIST_400));
        archName = text(displayBold, 76, Tokens.GOLD_400);
        archCount = countText(112);
        archSub = text(ui400, 24, Tokens.GOLD_200);
        archSub2 = text(ui400, 22, Tokens.MIST_400);
        // Alexandria's lines without the font's tall padding, so all five fit in the short arch.
        for (TextView v : new TextView[] {archLabel, archSub, archSub2}) v.setIncludeFontPadding(false);
        for (TextView v : new TextView[] {archLabel, archName, archCount, archSub, archSub2}) {
            v.setGravity(Gravity.CENTER_HORIZONTAL);
            v.setSingleLine(true);
            info.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        ((LinearLayout.LayoutParams) archSub.getLayoutParams()).topMargin = Ui.ipx(10);
        ((LinearLayout.LayoutParams) archSub2.getLayoutParams()).topMargin = Ui.ipx(4);
        lineHeight(archName, 1.15f);
        lineHeight(archCount, 1f);
        info.setClipChildren(false);
        place(info, 136, 116, ARCH_INFO_W, 352);
    }

    /** The calendar's card; its days are filled by {@link #renderCalendar} once a day. */
    private void buildCalendar() {
        calendar = new LinearLayout(this);
        calendar.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panelColor);
        bg.setCornerRadius(Ui.px(24));
        bg.setStroke(Ui.ipx(3), Tokens.GOLD_400);
        calendar.setBackground(bg);
        calendar.setPadding(Ui.ipx(22), Ui.ipx(20), Ui.ipx(22), Ui.ipx(20));
        place(calendar, Tokens.ARCH_X, CAL_Y, Tokens.ARCH_W, CAL_H);
        calendarFor = "";
    }

    /**
     * This Hijri month by Umm al-Qura (with the Hijri adjustment in Settings), the week
     * from Sunday as its printed calendar has it: each Hijri day with the Gregorian one under it (with the month's
     * name on its first), today in a gold cell, Fridays' numbers in gold. Information only, never focusable.
     */
    private void renderCalendar(Date now) {
        // Once a day (or when the Hijri adjustment or the language changes), not every minute.
        String key = new SimpleDateFormat("yyyyMMdd", Locale.US).format(now) + "/" + Prefs.hijriOffset(this) + "/" + t.ar + t.eastern;
        if (calendar == null || key.equals(calendarFor)) return;
        Hijri.Month m = Hijri.month(this, now);
        if (m.days.isEmpty()) return;
        calendarFor = key;
        calendar.removeAllViews();

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(ui400, 26, Tokens.PLASTER_100);
        title.setText(t.d(t.hijriMonths()[m.month] + " " + m.year) + t.hijriSuffix());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView source = text(ui400, 18, Tokens.MIST_400);
        source.setText(t.calendarSource());
        source.setSingleLine(true);
        title.setIncludeFontPadding(false);
        source.setIncludeFontPadding(false);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(source, new LinearLayout.LayoutParams(-2, -2));
        calendar.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout week = new LinearLayout(this);
        for (String w : t.weekdaysShort()) {
            TextView v = text(ui400, 17, Tokens.MIST_400);
            v.setText(w);
            v.setGravity(Gravity.CENTER);
            v.setSingleLine(true);
            v.setIncludeFontPadding(false);
            week.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.topMargin = Ui.ipx(12);
        calendar.addView(week, wlp);

        Calendar g = Calendar.getInstance();
        g.setTime(m.days.get(0));
        int col = g.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int rows = (col + m.days.size() + 6) / 7;
        // Smaller figures when a month runs over six weeks (the rows share the card's height).
        boolean six = rows > 5;
        Locale greg = t.ar ? Locale.forLanguageTag("ar-EG-u-nu-latn") : Locale.UK;
        SimpleDateFormat monthName = new SimpleDateFormat(t.ar ? "MMMM" : "MMM", greg);
        int i = 0;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 7; c++, col--) {
                LinearLayout cellBox = new LinearLayout(this);
                cellBox.setOrientation(LinearLayout.VERTICAL);
                cellBox.setGravity(Gravity.CENTER);
                if (col <= 0 && i < m.days.size()) {
                    Date d = m.days.get(i);
                    g.setTime(d);
                    boolean today = i == m.today, friday = g.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
                    // Today in a gold cell; the White Days and the occasions in a
                    // soft gold outline of the same shape, which no neighbouring cell can be mistaken for.
                    if (today || Hijri.marked(m.month, i + 1)) {
                        GradientDrawable gold = new GradientDrawable();
                        if (today) gold.setColor(Tokens.GOLD_400);
                        else gold.setStroke(Math.max(1, Ui.ipx(2)), Tokens.GOLD_400_60);
                        gold.setCornerRadius(Ui.px(12));
                        cellBox.setBackground(gold);
                    }
                    TextView hd = text(ui400, six ? 22 : 24, today ? Tokens.LAPIS_900 : friday ? Tokens.GOLD_400 : Tokens.PLASTER_100);
                    hd.setText(t.d(String.valueOf(i + 1)));
                    hd.setFontFeatureSettings("tnum");
                    int gd = g.get(Calendar.DAY_OF_MONTH);
                    TextView gl = text(ui400, six ? 14 : 15, today ? Tokens.LAPIS_900 : Tokens.MIST_400);
                    gl.setText(t.d(gd == 1 ? gd + " " + monthName.format(d) : String.valueOf(gd)));
                    for (TextView v : new TextView[] {hd, gl}) {
                        v.setIncludeFontPadding(false);
                        v.setSingleLine(true);
                        v.setGravity(Gravity.CENTER);
                        cellBox.addView(v, new LinearLayout.LayoutParams(-2, -2));
                    }
                    ((LinearLayout.LayoutParams) gl.getLayoutParams()).topMargin = Ui.ipx(six ? 1 : 3);
                    i++;
                }
                // A little room around each cell, so two outlined days side by side stay two.
                LinearLayout.LayoutParams cbl = new LinearLayout.LayoutParams(0, -1, 1);
                cbl.setMargins(Ui.ipx(2), Ui.ipx(1), Ui.ipx(2), Ui.ipx(1));
                row.addView(cellBox, cbl);
            }
            // The rows share what the card has left, whatever the header's lines measure on the TV.
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, 0, 1);
            if (r == 0) rlp.topMargin = Ui.ipx(6);
            calendar.addView(row, rlp);
        }
    }
    /** The photo Home shows now, or null with the pattern; the right column's colour over it. */
    private Photos.Photo homePhoto;
    private int panelColor;

    /**
     * A new turn, a new photo (Settings > Display > Photo change): Home is built again in it, but not while the viewer
     * is moving through it (a minute after the last key), so nothing flickers under the remote.
     */
    private void showHomePhoto(Date now) {
        if (homePhoto == null || day == null) return;
        if (System.currentTimeMillis() - lastKey < 60_000) return;
        Photos.Photo p = Photos.forHome(this, Photos.turn(this, true, day, now));
        if (p != null && p != homePhoto) root.post(this::build);
    }

    private void renderMinute(Date now) {
        showHomePhoto(now);
        readClockFormat();
        clock.setText(time(now, 0.4f));
        Locale dateLocale = t.ar ? Locale.forLanguageTag("ar-EG-u-nu-latn") : Locale.UK;
        gdate.setText(t.d(new SimpleDateFormat("d MMMM yyyy", dateLocale).format(now)));
        hdate.setText(hijri(day));
        cityLine.setText(t.city() + t.sep() + new SimpleDateFormat("EEEE", dateLocale).format(now));
        if (weather != null) weather.update();

        String[] names = t.prayerNames();
        if (day.friday) names[PrayerDay.DHUHR] = t.jumuah();
        String label = t.nextPrayer(), name = names[day.nextIndex];
        String sub2 = "";
        if (day.friday && day.nextIndex == PrayerDay.DHUHR) label = t.jumuahLabel();
        if (day.friday && now.before(day.times[PrayerDay.MAGHRIB])) sub2 = t.kahf();
        // Friday's night begins at Thursday's Maghrib.
        java.util.Calendar dow = java.util.Calendar.getInstance();
        dow.setTime(now);
        if (dow.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.THURSDAY && !now.before(day.times[PrayerDay.MAGHRIB])) sub2 = t.kahfTonight();
        if (day.ramadan) {
            // The imsak instead, when the viewer set one (Settings > Prayer times).
            Date imsak = Jumuah.imsak(this, day);
            sub2 = imsak != null ? t.imsakAt(time(imsak, 1f)) : t.suhoorEnds(time(day.suhoorEnd, 1f).toString());
            if (day.nextIndex == PrayerDay.MAGHRIB) {
                label = t.iftarIn();
                name = t.iftar();
            }
        }
        // The coming occasion (Ramadan, the two Eids, Arafah) from 30 days before, in gold; in the
        // evening tomorrow's sunnah fast in its place (Hijri.notice).
        String notice = Hijri.notice(this, t, day, now);
        String sub = notice == null ? "" : notice;
        // No place set yet: that, in red, in the occasion's place, until one is chosen.
        boolean unset = !Prefs.citySet(this);
        if (unset) sub = t.noLocation();
        archSub.setTextColor(unset ? Tokens.STATUS_BAD : Tokens.GOLD_200);
        archLabel.setText(label);
        archName.setText(name);
        archSub.setText(sub);
        archSub.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
        archSub2.setText(t.d(sub2));
        archSub2.setVisibility(sub2.isEmpty() ? View.GONE : View.VISIBLE);
        renderCalendar(now);

        for (int i = 0; i < 6; i++) {
            String tag = i == day.nowIndex ? t.nowTag() : (i == day.nextIndex ? t.nextTag() : "");
            if (day.ramadan && i == PrayerDay.FAJR) tag = t.suhoorTag();
            if (day.ramadan && i == PrayerDay.MAGHRIB) tag = t.iftarTag();
            boolean past = i != day.nextIndex && !day.times[i].after(now);
            int bg, fg, ring = 0;
            boolean bold;
            if (i == day.nowIndex) { bg = Tokens.PLASTER_100; fg = Tokens.LAPIS_900; bold = true; }
            else if (i == day.nextIndex) { bg = 0; fg = Tokens.GOLD_400; ring = Tokens.GOLD_400; bold = true; }
            else if (past) { bg = Tokens.LAPIS_800_60; fg = Tokens.MIST_400; bold = false; }
            else { bg = Tokens.LAPIS_800; fg = Tokens.PLASTER_100; bold = false; }
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Ui.px(Tokens.RADIUS_CELL));
            g.setColor(bg);
            if (ring != 0) g.setStroke(Ui.ipx(3), ring);
            cell[i].setBackground(g);
            cellTag[i].setText(tag);
            // How far the current prayer's time has gone, to the next time (Sunrise ends Fajr's). After
            // midnight, before Fajr, it is still Isha's: from yesterday's Isha.
            boolean beforeDawn = day.nowIndex == -1 && now.before(day.times[PrayerDay.FAJR]);
            boolean current = i == day.nowIndex || (beforeDawn && i == PrayerDay.ISHA);
            cellBar[i].setVisibility(current ? View.VISIBLE : View.INVISIBLE);
            if (current) {
                long from = (i == PrayerDay.ISHA ? PrayerDay.lastIsha(this, day, now) : day.times[i]).getTime();
                long to = (i == PrayerDay.ISHA ? day.times[PrayerDay.FAJR] : day.times[i + 1]).getTime();
                float done = to > from ? (now.getTime() - from) / (float) (to - from) : 1f;
                cellFill[i].setScaleX(Math.max(0f, Math.min(1f, done)));
                // Lapis on the current cell's plaster; plaster on Isha's darker cell after midnight.
                boolean light = i == day.nowIndex;
                ((GradientDrawable) cellFill[i].getBackground()).setColor(light ? Tokens.LAPIS_900 : Tokens.PLASTER_100);
                ((GradientDrawable) cellBar[i].getBackground()).setColor(light ? Tokens.alpha(Tokens.LAPIS_900, 0x33) : Tokens.alpha(Tokens.PLASTER_100, 0x33));
            }
            cellName[i].setText(names[i]);
            // English 12-hour ("12:41 PM") is too wide for a cell at 34px; Arabic's ص/م is narrow enough.
            float timeSize = Ui.px(!h24 && !t.ar ? 30 : 34);
            if (cellTime[i].getTextSize() != timeSize) {
                cellTime[i].setTextSize(TypedValue.COMPLEX_UNIT_PX, timeSize);
                lineHeight(cellTime[i], 1.1f);
            }
            cellTime[i].setText(time(day.times[i], 0.55f));
            Typeface tf = bold ? ui600 : ui400;
            for (TextView v : new TextView[] {cellTag[i], cellName[i], cellTime[i]}) v.setTextColor(fg);
            cellTag[i].setTypeface(ui400);
            cellName[i].setTypeface(tf);
            cellTime[i].setTypeface(tf);
            if (!t.ar) cellTag[i].setLetterSpacing(0.2f);
        }
    }

    /**
     * The round network button follows the connection: Wi-Fi (its name in the hint), a cable, or struck
     * through in gold with no network or no internet. Called on resume and whenever the network changes.
     */
    private void updateWifi() {
        android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities caps = null;
        try {
            if (cm != null) caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        } catch (RuntimeException ignored) {
            // Not allowed or gone: shown as not connected.
        }
        boolean wired = caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
        boolean online = caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        String state;
        if (caps == null) state = t.netOff();
        else if (!online) state = t.netNoInternet();
        else if (wired) state = t.netWired();
        else state = ssid();
        netOff = caps == null || !online;
        if (netIcon != null) {
            netIcon.setKind(netOff ? Icon.NET_OFF : wired ? Icon.ETHERNET : Icon.WIFI);
            boolean focused = nodes[WIFI] != null && nodes[WIFI].isFocused();
            netIcon.setColor(focused ? Tokens.LAPIS_900 : netOff ? Tokens.GOLD_400 : Tokens.PLASTER_100);
        }
        nodeLabel[WIFI] = state == null ? t.network() : t.network() + t.sep() + state;
        if (nodes[WIFI] != null && nodes[WIFI].isFocused()) onNodeFocused(WIFI);
    }

    private Icon netIcon;
    private boolean netOff;
    private final android.net.ConnectivityManager.NetworkCallback onNetwork = new android.net.ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(android.net.Network n) { tick.post(MainActivity.this::updateWifi); }
        @Override public void onLost(android.net.Network n) { tick.post(MainActivity.this::updateWifi); }
        @Override public void onCapabilitiesChanged(android.net.Network n, android.net.NetworkCapabilities c) {
            tick.post(MainActivity.this::updateWifi);
        }
    };

    // ---------------------------------------------------------------- focus

    private void onNodeFocused(int n) {
        if (n >= F0 && n < M0) lastFav = n;
        setArchFocused(n == ARCH);
        List<String> h = new ArrayList<>();
        if (n == ARCH) {
            h.add(t.hintOk(nodeLabel[ARCH]));
            h.add(t.hintApps());
        } else if (n == RADIO) {
            h.add(t.hintOk(nodeLabel[n]));
        } else if (n >= F0 && n < M0) {
            h.add(t.hintOk(t.open(nodeLabel[n])));
            h.add(t.hintMore());
        } else if (n >= M0 && n <= ALL) {
            h.add(t.hintOk(n == ALL ? nodeLabel[n] : t.open(nodeLabel[n])));
            h.add(t.hintFavs());
        } else if (n == SETTINGS) {
            // The version and the site where they are looked for, not on Home all day.
            h.add(t.hintOk(nodeLabel[n]));
            String v = "";
            try {
                v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                // No version to show.
            }
            h.add(t.d("Awan TV " + v) + t.sep() + "awanpro.app");
        } else {
            h.add(t.hintOk(nodeLabel[n]));
        }
        setHints(hints, h);
    }

    private void setArchFocused(boolean f) {
        archFocused = f;
        if (arch != null) render(false);
    }

    // ---------------------------------------------------------------- radio

    /** The header pill: "Radio" with a broadcast icon when off; level bars and "On air" (gold outline) when on. */
    private View radioPill() {
        FocusTile b = new FocusTile(this, 36);
        b.setRest(Tokens.LAPIS_800, Tokens.GOLD_400_60, 2);  // Soft gold, as the column's edges
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.ipx(28), 0, Ui.ipx(28), 0);
        FrameLayout glyph = new FrameLayout(this);
        radioIcon = new Icon(this, Icon.RADIO, 2f);
        glyph.addView(radioIcon, new FrameLayout.LayoutParams(-1, -1));
        pillBars = new RadioBars(this, 3);
        glyph.addView(pillBars, new FrameLayout.LayoutParams(Ui.ipx(22), Ui.ipx(22), Gravity.CENTER));
        radioLabel = text(ui400, 24, Tokens.PLASTER_100);
        radioLabel.setSingleLine(true);
        row.addView(glyph, new LinearLayout.LayoutParams(Ui.ipx(30), Ui.ipx(30)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(Ui.ipx(14));
        row.addView(radioLabel, lp);
        b.addView(row, new FrameLayout.LayoutParams(-2, -1));
        nodes[RADIO] = b;
        b.setLook(f -> styleRadio());
        b.setOnClickListener(v -> showRadioCard());
        nodeLabel[RADIO] = t.station(Radio.station(this));
        return b;
    }

    /** Puts the pill and the open card in step with the radio. */
    private void styleRadio() {
        if (radioLabel == null || nodes[RADIO] == null) return;
        int state = Radio.state();
        boolean on = Radio.on(), playing = state == Radio.PLAYING;
        boolean focused = nodes[RADIO].isFocused();
        // While on, the pill is its bars alone (the weather needs the clock line's room); the hint and the
        // card say the station and how it is doing.
        int shown = state == Radio.RETRYING ? Radio.CONNECTING : state;
        nodeLabel[RADIO] = on ? t.station(Radio.station(this)) + t.sep() + t.radioStatus(shown) : t.station(Radio.station(this));
        radioLabel.setText(t.radio());
        radioLabel.setVisibility(on ? View.GONE : View.VISIBLE);
        radioIcon.setVisibility(on ? View.GONE : View.VISIBLE);
        pillBars.setVisibility(on ? View.VISIBLE : View.GONE);
        pillBars.setLive(playing);
        int fg = focused ? Tokens.LAPIS_900 : Tokens.PLASTER_100;
        radioLabel.setTextColor(fg);
        radioIcon.setColor(fg);
        pillBars.setColor(focused ? Tokens.LAPIS_900 : Tokens.GOLD_400);
        // Soft gold at rest like its neighbours; on air, full gold and thicker.
        ((FocusTile) nodes[RADIO]).setRest(Tokens.LAPIS_800, on ? Tokens.GOLD_400 : Tokens.GOLD_400_60, on ? 3 : 2);
        if (radioCard != null) {
            cardStatus.setText(t.stationWhere(Radio.station(this)) + t.sep() + t.radioStatus(state));
            cardBars.setLive(playing);
            playLabel.setText(on ? t.radioStop() : t.radioPlay());
            playIcon.setKind(on ? Icon.STOP : Icon.PLAY);
        }
    }

    /**
     * The card under the pill: the station, its state and live bars; Play/Stop (focused) and Screen off. Back
     * closes it and the radio carries on. Built like the reminder card, in the lapis of the header.
     */
    private void showRadioCard() {
        if (radioCard != null || card != null) return;
        radioCard = new LinearLayout(this);
        radioCard.setOrientation(LinearLayout.VERTICAL);
        radioCard.setPadding(Ui.ipx(36), Ui.ipx(30), Ui.ipx(36), Ui.ipx(30));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tokens.LAPIS_700);
        bg.setCornerRadius(Ui.px(24));
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
        radioCard.setBackground(bg);
        radioCard.setClipChildren(false);
        radioCard.setClipToPadding(false);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        cardBars = new RadioBars(this, 5);
        cardBars.setColor(Tokens.GOLD_400);
        top.addView(cardBars, new LinearLayout.LayoutParams(Ui.ipx(72), Ui.ipx(64)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView label = caps(text(ui400, 20, Tokens.MIST_400));
        label.setText(t.radio());
        TextView title = text(displayBold, 48, Tokens.PLASTER_100);
        title.setText(t.station(Radio.station(this)));
        cardTitle = title;
        cardStatus = text(ui400, 24, Tokens.GOLD_400);
        words.addView(label);
        words.addView(title);
        words.addView(cardStatus);
        lineHeight(title, 1.15f);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -2, 1);
        wlp.setMarginStart(Ui.ipx(28));
        top.addView(words, wlp);
        radioCard.addView(top);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        playIcon = new Icon(this, Icon.PLAY, 2f);
        playLabel = text(ui400, 24, Tokens.PLASTER_100);
        cardPlay = radioButton(playIcon, playLabel, t.radioPlay());
        cardPlay.setOnClickListener(v -> Radio.toggle(this));
        // Another station without going to Settings (Afnon 0.8): the same list as Settings > Radio > Station.
        cardStation = radioButton(new Icon(this, Icon.RADIO, 2f), text(ui400, 24, Tokens.PLASTER_100), t.stationRow());
        cardStation.setOnClickListener(v -> openStationGroups());
        cardDark = radioButton(new Icon(this, Icon.MOON, 2f), text(ui400, 24, Tokens.PLASTER_100), t.screenOff());
        cardDark.setOnClickListener(v -> {
            closeRadioCard(true);
            ScreenOffActivity.start(this);
        });
        float r = FocusTile.REACH;
        buttons.addView(cardPlay, new LinearLayout.LayoutParams(-2, Ui.ipx(68 + 2 * r)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, Ui.ipx(68 + 2 * r));
        slp.setMarginStart(Ui.ipx(16 - 2 * r));
        buttons.addView(cardStation, slp);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, Ui.ipx(68 + 2 * r));
        dlp.setMarginStart(Ui.ipx(16 - 2 * r));
        buttons.addView(cardDark, dlp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.ipx(28 - r);
        blp.setMarginStart(-Ui.ipx(r));
        radioCard.addView(buttons, blp);

        TextView note = text(ui400, 20, Tokens.MIST_400);
        note.setText(t.radioNote());
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = Ui.ipx(20 - r);
        radioCard.addView(note, nlp);

        // Hangs from the header, end-aligned with the Settings button, over the dates and the prayer strip.
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.ipx(720), -2, Gravity.TOP | Gravity.END);
        lp.setMarginEnd(Ui.ipx(1920 - (Tokens.COURT_X + Tokens.COURT_W)));
        lp.topMargin = Ui.ipx(56 + 72 + 24);
        root.addView(radioCard, lp);
        for (View n : nodes) if (n != null) n.setFocusable(false);
        styleRadio();
        cardPlay.requestFocus();
        cardHints(cardPlay);
    }

    private void cardHints(View focused) {
        if (radioCard == null || hints == null) return;
        List<String> h = new ArrayList<>();
        h.add(t.hintOk(focused == cardDark ? t.screenOff() : focused == cardStation ? t.stationRow()
            : (Radio.on() ? t.radioStop() : t.radioPlay())));
        h.add(t.hintClose());
        setHints(hints, h);
    }

    /** A 68px pill on the card with an icon: lapis at rest, gold when focused. */
    private FocusTile radioButton(Icon icon, TextView v, String label) {
        FocusTile b = new FocusTile(this, 34);
        b.setRest(Tokens.LAPIS_900, Tokens.LINE_SOFT, 2);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.ipx(28), 0, Ui.ipx(32), 0);
        v.setText(label);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.ipx(26), Ui.ipx(26)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(Ui.ipx(12));
        row.addView(v, lp);
        b.addView(row, new FrameLayout.LayoutParams(-2, -1));
        b.setLook(f -> {
            int c = f ? Tokens.LAPIS_900 : Tokens.PLASTER_100;
            v.setTextColor(c);
            icon.setColor(c);
            if (f) cardHints(b);
        });
        return b;
    }

    // ---------------------------------------------------------------- the radio card's Station list

    /** The groups (Quran radio stations, reciters, and saved recordings when there are some), the current one marked. */
    private void openStationGroups() {
        Radio.Station now = Radio.station(this);
        String[] groups = t.stationGroups();
        if (Library.savedKeys(this).isEmpty()) groups = java.util.Arrays.copyOf(groups, 2);
        int sel = now.url == null ? 2 : now.reciter ? 1 : 0;
        showStations(t.stationRow(), groups, sel, i -> {
            if (i == 2) openSavedStations();
            else openStationList(i == 1);
        }, null);
    }

    /** One group's stations (Afnon's, then mp3quran.net's other reciter channels); OK moves the radio there. */
    private void openStationList(boolean reciters) {
        List<Radio.Station> found = new ArrayList<>();
        for (Radio.Station s : Radio.STATIONS) if (s.reciter == reciters) found.add(s);
        if (reciters) found.addAll(Radios.extra(this));
        String[] names = new String[found.size()];
        String current = Radio.station(this).id;
        int sel = -1;
        for (int k = 0; k < names.length; k++) {
            names[k] = t.stationChoice(found.get(k));
            if (found.get(k).id.equals(current)) sel = k;
        }
        showStations(t.stationGroups()[reciters ? 1 : 0], names, sel, k -> {
            Radio.switchToId(this, found.get(k).id);
            stationPicked();
        }, this::openStationGroups);
    }

    private void openSavedStations() {
        List<String> keys = Library.savedKeys(this);
        String[] names = new String[keys.size()];
        String now = Radio.station(this).savedKey();
        int sel = -1;
        for (int k = 0; k < names.length; k++) {
            names[k] = t.d(Library.name(this, keys.get(k), t.ar));
            if (keys.get(k).equals(now)) sel = k;
        }
        showStations(t.stationGroups()[2], names, sel, k -> {
            Radio.switchToSaved(this, keys.get(k));
            stationPicked();
        }, this::openStationGroups);
    }

    /** One step of the list over Home and the card: the card's buttons wait under it. */
    private void showStations(String title, String[] items, int selected, java.util.function.IntConsumer pick, Runnable back) {
        closeStations(false);
        stations = new ListLayer(this, title, items, selected, i -> root.post(() -> pick.accept(i)));
        stationsBack = back;
        root.addView(stations.layer, new FrameLayout.LayoutParams(-1, -1));
        for (View b : new View[] {cardPlay, cardStation, cardDark}) if (b != null) b.setFocusable(false);
        if (stations.start != null) root.post(stations.start::requestFocus);
        List<String> h = new ArrayList<>();
        h.add(t.hintMove());
        h.add(t.hintChoose());
        h.add(t.hintBackCancel());
        setHints(hints, h);
    }

    /** The list gone: the card's buttons back, focus on Station when asked. */
    private void closeStations(boolean focusStation) {
        if (stations == null) return;
        root.removeView(stations.layer);
        stations = null;
        stationsBack = null;
        for (View b : new View[] {cardPlay, cardStation, cardDark}) if (b != null) b.setFocusable(true);
        if (focusStation && cardStation != null) {
            cardStation.requestFocus();
            cardHints(cardStation);
        }
    }

    /** A station picked: the list closes, the card names it (the radio, if on, has moved there already). */
    private void stationPicked() {
        closeStations(true);
        if (cardTitle != null) cardTitle.setText(t.station(Radio.station(this)));
        styleRadio();
    }

    private void closeRadioCard(boolean focusPill) {
        if (radioCard == null) return;
        closeStations(false);
        root.removeView(radioCard);
        radioCard = null;
        for (View n : nodes) if (n != null) n.setFocusable(true);
        if (focusPill) nodes[RADIO].requestFocus();
    }

    // ---------------------------------------------------------------- reminder card

    /** "Asr in 13 minutes" at the bottom end, over the apps; it takes focus until Got it, Today's prayers or Back. */
    private void showReminder() {
        // The reminder is due now: What's new, if open, gives way (and counts as seen); a newer version's notice
        // too, still due.
        closeNews();
        dropUpdate();
        if (card != null) dismissReminder();
        closeRadioCard(false);
        Date now = new Date();
        day = new PrayerDay(this, now);
        cardFor = day.nextTime;
        readClockFormat();

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.ipx(36), Ui.ipx(32), Ui.ipx(36), Ui.ipx(32));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tokens.PLASTER_100);
        bg.setCornerRadius(Ui.px(24));
        bg.setStroke(Ui.ipx(6), Tokens.ON_PLASTER_BORDER);
        card.setBackground(bg);
        card.setClipChildren(false);
        card.setClipToPadding(false);

        String[] names = t.prayerNames();
        if (day.friday) names[PrayerDay.DHUHR] = t.jumuah();
        TextView label = caps(text(ui400, 20, Tokens.ON_PLASTER_LABEL));
        label.setText(t.apLabel());
        TextView title = text(displayBold, 52, Tokens.LAPIS_900);
        title.setText(t.apTitle(names[day.nextIndex], Reminders.minutesLeft(day, now), duration(secondsUntil(day.nextTime, now), false)));
        TextView sub = text(ui400, 24, Tokens.ON_PLASTER_SECONDARY);
        sub.setText(t.apSub(time(day.nextTime, 1f)));
        card.addView(label);
        card.addView(title);
        card.addView(sub);
        lineHeight(title, 1.15f);
        ((LinearLayout.LayoutParams) sub.getLayoutParams()).topMargin += Ui.ipx(6);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        cardOk = cardButton(t.apOk(), true);
        cardOk.setOnClickListener(v -> dismissReminder());
        cardDay = cardButton(t.dayTitle(), false);
        cardDay.setOnClickListener(v -> {
            dismissReminder();
            startSafely(new Intent(this, PrayerDayActivity.class));
        });
        float r = FocusTile.REACH;
        buttons.addView(cardOk, new LinearLayout.LayoutParams(-2, Ui.ipx(68 + 2 * r)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, Ui.ipx(68 + 2 * r));
        dlp.setMarginStart(Ui.ipx(16 - 2 * r));
        buttons.addView(cardDay, dlp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.ipx(24 - r);
        blp.setMarginStart(-Ui.ipx(r));
        card.addView(buttons, blp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.ipx(680), -2, Gravity.BOTTOM | Gravity.END);
        lp.setMarginEnd(Ui.ipx(96));
        lp.bottomMargin = Ui.ipx(56);
        root.addView(card, lp);
        for (View n : nodes) if (n != null) n.setFocusable(false);
        cardOk.requestFocus();
        List<String> h = new ArrayList<>();
        h.add(t.hintOk(t.apOk()));
        h.add(t.hintBackHome().replace(t.home(), t.dismiss()));
        setHints(hints, h);
    }

    /** A 68px pill on the cream card: lapis (Got it) or outlined; gold when focused. */
    private FocusTile cardButton(String label, boolean filled) {
        FocusTile b = new FocusTile(this, 34);
        b.setRest(filled ? Tokens.LAPIS_900 : 0, Tokens.ON_PLASTER_BUTTON_BORDER, filled ? 0 : 2);
        TextView v = text(ui400, 24, filled ? Tokens.PLASTER_100 : Tokens.LAPIS_900);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setPadding(Ui.ipx(32), 0, Ui.ipx(32), 0);
        b.addView(v, new FrameLayout.LayoutParams(-2, -1));
        b.setLook(f -> v.setTextColor(f ? Tokens.LAPIS_900 : (filled ? Tokens.PLASTER_100 : Tokens.LAPIS_900)));
        return b;
    }

    private void dismissReminder() {
        if (card == null) return;
        root.removeView(card);
        card = null;
        for (View n : nodes) if (n != null) n.setFocusable(true);
        focusStart();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        lastKey = System.currentTimeMillis();
        if (news != null && NewsActivity.key(news, e, this::closeNews)) return true;
        if (update != null && UpdateNotice.key(update, e, () -> closeUpdate(false), t.ar)) return true;
        // Source, where the firmware leaves it to the app in front (CVTE) and nothing else opens the source menu.
        if (k == KeyEvent.KEYCODE_TV_INPUT) {
            Intent source = FirmwareKeys.sourceMenu(this);
            if (source != null) {
                if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                    try {
                        startActivity(source.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException ignored) {
                        // Not exported or gone after all; the key just does nothing.
                    }
                }
                return true;
            }
        }
        if (stations != null) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) {
                    Runnable back = stationsBack;
                    if (back != null) back.run();
                    else closeStations(true);
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            return super.dispatchKeyEvent(e);
        }
        if (radioCard != null) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) closeRadioCard(true);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // Play, Station, Screen off along the card (mirrored in Arabic).
                boolean toEnd = (k == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl;
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    View[] row = {cardPlay, cardStation, cardDark};
                    int at = 0;
                    for (int i = 0; i < row.length; i++) if (row[i] != null && row[i].isFocused()) at = i;
                    int to = Math.max(0, Math.min(row.length - 1, at + (toEnd ? 1 : -1)));
                    row[to].requestFocus();
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            return super.dispatchKeyEvent(e);
        }
        if (card != null) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) dismissReminder();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // Got it sits at the start; toward the end is Today's prayers (Left in Arabic).
                boolean toEnd = (k == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl;
                if (e.getAction() == KeyEvent.ACTION_DOWN) (toEnd ? cardDay : cardOk).requestFocus();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            return super.dispatchKeyEvent(e);
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            // Back on Home returns focus to where Home starts (The first favourite) and never leaves Home.
            if (e.getAction() == KeyEvent.ACTION_UP) focusStart();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) move(k);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** design/afnon-mihrab/design/FocusMap.dc.html, written for LTR; in Arabic every Left/Right swaps. */
    private void move(int key) {
        if (rtl && key == KeyEvent.KEYCODE_DPAD_LEFT) key = KeyEvent.KEYCODE_DPAD_RIGHT;
        else if (rtl && key == KeyEvent.KEYCODE_DPAD_RIGHT) key = KeyEvent.KEYCODE_DPAD_LEFT;
        int from = currentNode();
        if (from < 0) { focusStart(); return; }
        boolean left = key == KeyEvent.KEYCODE_DPAD_LEFT, right = key == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean up = key == KeyEvent.KEYCODE_DPAD_UP, down = key == KeyEvent.KEYCODE_DPAD_DOWN;
        int to = -1;
        switch (from) {
            case ARCH: to = right ? F0 : up ? RADIO : -1; break;
            // Six favourites under the header, each going up to what is above it.
            case RADIO: to = left ? ARCH : right ? WIFI : down ? F0 + 3 : -1; break;
            case WIFI: to = left ? RADIO : right ? SAVER : down ? F0 + 4 : -1; break;
            case SAVER: to = left ? WIFI : right ? SETTINGS : down ? F0 + 5 : -1; break;
            case SETTINGS: to = left ? SAVER : down ? F0 + 5 : -1; break;
            default:
                if (from >= F0 && from < M0) {
                    int i = from - F0;
                    if (left) to = i == 0 ? ARCH : from - 1;
                    else if (right) to = i == FAVS - 1 ? -1 : from + 1;
                    else if (up) to = i == 5 ? SETTINGS : i == 4 ? WIFI : RADIO;
                    // Down: the More square nearest below (seven squares under six).
                    else to = new int[] {M0, M0 + 1, M0 + 2, M0 + 4, M0 + 5, ALL}[i];
                } else {
                    int i = from - M0;
                    if (left) to = i == 0 ? ARCH : from - 1;
                    else if (right) to = from == ALL ? -1 : from + 1;
                    else if (up) to = lastFav;
                }
        }
        if (to >= 0 && nodes[to] != null) nodes[to].requestFocus();
    }

    private int currentNode() {
        View f = getCurrentFocus();
        for (int i = 0; i < NODES; i++) if (nodes[i] == f) return i;
        return -1;
    }

    // ---------------------------------------------------------------- apps

    private String appsKey() {
        // And the More row the viewer arranged.
        return Apps.favourites(this) + "|" + Apps.moreSaved(this) + "|" + Apps.installed(this).size();
    }
}
