package app.awanpro.tv;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.batoulapps.adhan.Madhab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Afnon Settings (design/afnon-mihrab/design/Settings.dc.html): sections down the start side, the section's rows beside them, and a
 * preview of today's Asr under the current choices. Every change is saved at once; the other screens rebuild
 * when they come back to the front.
 *
 * <p>Keys: the arrows only ever move. Up/Down go through the sections or the rows, toward the end side (or OK)
 * goes into the rows, toward the start side goes back to the sections. OK is the only key that changes anything:
 * it opens a list, flips a switch, or starts editing a choice or a stepper. While editing, ◀▶ move the choice,
 * OK saves it and Back cancels. Back on a row returns to the sections, Back again goes Home. Network and Device
 * settings open the TV's own screens. Every save shows ✓ Saved at the end of the hint line for a moment.
 */
public class SettingsActivity extends Screen {

    // Awan TV adds Iqamah, Awan TV (what runs) and About.
    private static final int PRAYER = 0, IQAMA = 1, REMINDERS = 2, RADIO = 3, DISPLAY = 4, RUN = 5, NETWORK = 6, DEVICE = 7,
        ABOUT = 8, SECTIONS = 9;
    private static final int VALUE = 0, SEG = 1, STEP = 2, TOGGLE = 3;
    /** A row that only reads (About, the country's iqamah): no control, OK does nothing. */
    private static final int INFO = 4;
    private static final float ROWS_TOP = 112, ROW_H = 88, ROW_GAP = 10;
    // Nine sections fit above the preview at 70 + 8 (Afnon's eight: 76 + 10).
    private static final float NAV_TOP = 152, NAV_H = 70, NAV_GAP = 8;

    /** One row: what it shows and what OK (and ◀▶ while editing) do. */
    private abstract class Row {
        final int kind;
        FocusTile tile;
        TextView label, help;
        LinearLayout control;

        Row(int kind) {
            this.kind = kind;
        }

        abstract String label();

        String help() { return ""; }

        /** VALUE rows. */
        String value() { return ""; }

        /** SEG rows: the choices (already in the screen's digits) and the chosen one. */
        String[] segs() { return new String[0]; }

        int selected() { return 0; }

        void select(int i) {}

        /** VALUE rows: the help line is a warning (gold, full strength). */
        boolean warn() { return false; }

        /** STEP rows: the saved amount, its range, how far one press moves it, and how an amount reads. */
        int amount() { return 0; }

        int step() { return 1; }

        void setAmount(int n) {}

        int lowest() { return 0; }

        int highest() { return 0; }

        String valueOf(int n) { return ""; }

        boolean on() { return false; }

        void set(boolean on) {}

        /** TOGGLE rows: false when flipping it is an action (play the radio), not a saved setting. */
        boolean saves() { return true; }

        /** VALUE rows: OK. */
        void open() {}

        /** INFO rows of App status: a dot of this colour before value(); 0 for none. */
        int dot() { return 0; }
    }

    /** About > Check for updates now: what the last check said ("" before one), and whether one is running. */
    private String checkNowAnswer = "";
    private boolean checkingNow;

    private void checkNow(Row r) {
        if (checkingNow) return;
        checkingNow = true;
        checkNowAnswer = t.checking();
        render(r, r.tile.isFocused());
        Updates.checkNow(this, latest -> {
            checkingNow = false;
            if (isFinishing() || isDestroyed()) return;
            boolean newer = latest != null && !latest.isEmpty() && Updates.compare(latest, versionName()) > 0;
            checkNowAnswer = latest == null ? t.checkFailed() : newer ? t.newVersion(latest) : t.upToDate();
            styleNav(ABOUT);
            // The row is redrawn only while it is still on screen (the section may have changed meanwhile).
            if (rows.contains(r)) {
                render(r, r.tile.isFocused());
                // A newer version: how to get it, at once, unless something else is open over the rows.
                if (newer && chooser == null && resetStage == 0) openUpdate(latest);
            }
        });
    }

    /** About shows App status's rows instead of its own (Back returns to About). */
    private boolean statusOpen;
    /** About shows the sources and licences instead of its own rows (Back returns to About). */
    private boolean creditsOpen;

    /** Device settings shows the remote buttons instead of its own rows (Back returns to Device settings). */
    private boolean buttonsOpen;
    /** Prayer times shows the times' and the Hijri date's adjustments instead of its own rows (Back returns). */
    private boolean adjustOpen;
    /** Prayer times shows the custom calculation instead of its own rows (Back returns to the method's row). */
    private boolean calcOpen;
    /** The note under a sub-page's rows (remote buttons, adjustments); null elsewhere. */
    private TextView pageNote;
    /** At most this many custom buttons: the rows, Add a button and the note fit above the hints. */
    private static final int MAX_BUTTONS = 6;

    /** The Iqamah rows edit Ramadan's own minutes (true) or the other days (false); not saved. */
    private boolean iqamaRamadan;

    /** Opened by Home on a new install; the city list opens at once. */
    static final String FIRST_RUN = "first_run";

    /**
     * Opened by Home for the first run (a new install, or after Reset settings) and the city is now
     * picked: back to Home, as a first run should end, instead of staying in Settings. Once per opening.
     */
    private void firstRunDone() {
        if (!getIntent().getBooleanExtra(FIRST_RUN, false) || !Prefs.citySet(this)) return;
        getIntent().removeExtra(FIRST_RUN);
        root.postDelayed(this::finish, Tokens.MOTION_SAVED_LIFETIME_MS / 2);
    }
    private boolean firstRunPending = true;

    // ---------------------------------------------------------------- the first run's location

    /** The first run's proposed place, as shown, and what choosing it saves; null until it is known. */
    private String firstShown;
    private Runnable firstSave;
    private boolean locating;
    private View firstChooser;
    /** The location question's buttons (resetStage 3). */
    private FocusTile locSet, locLater;

    /** A first run (a new install, or after Reset settings) that has no place set yet. */
    private boolean firstRunUnset() {
        return getIntent().getBooleanExtra(FIRST_RUN, false) && !Prefs.citySet(this);
    }

    /**
     * "Choose your location": the TV's own place first (Locate: from the Wi-Fi around it, where its location service
     * gives one), named after the nearest listed place ("Near ..." beyond 15 km) but kept by its own coordinates, so the
     * times are right in a village or the desert too; else the city guessed from the TV's time zone. Then Another
     * location, the usual choice. Back asks first (openLocationAsk).
     */
    private void openFirstLocation() {
        if (firstShown == null && !locating) {
            locating = true;
            Context app = getApplicationContext();
            Locate.once(this, 10_000, where -> {
                if (where == null) {
                    firstGuess();
                    return;
                }
                double lat = where.getLatitude(), lng = where.getLongitude();
                new Thread(() -> {
                    Places.Place near = null;
                    try {
                        near = Places.nearest(app, lat, lng);
                    } catch (Exception ignored) {
                        // No list: the guess below.
                    }
                    Places.Place found = near;
                    runOnUiThread(() -> firstFound(found, lat, lng));
                }, "awan-nearest").start();
            });
        }
        showFirstChooser();
    }

    private void firstFound(Places.Place near, double lat, double lng) {
        if (near == null) {
            firstGuess();
            return;
        }
        locating = false;
        String name = Places.km(lat, lng, near.lat, near.lng) <= 15 ? near.name(t) : t.near(near.name(t));
        String country = Prefs.countryName(near.cc, t);
        firstShown = country.isEmpty() ? name : t.place(name, country);
        firstSave = () -> Prefs.setCustom(this, name, lat, lng, near.cc);
        reopenFirstChooser();
    }

    /** No place from the TV: the city guessed from its time zone (what Prefs.city gives until one is set). */
    private void firstGuess() {
        locating = false;
        int i = Math.max(0, Prefs.cityIndex(this));
        Prefs.City city = Prefs.CITIES[i];
        firstShown = t.place(t.ar ? city.ar : city.en, Prefs.countryOf(city, t));
        firstSave = () -> Prefs.setCity(this, i);
        reopenFirstChooser();
    }

    private void showFirstChooser() {
        String first = firstShown != null ? firstShown : t.locating();
        openChooser(t.chooseLocation(), new String[] {first, t.otherLocation()}, 0, i -> {
            if (i == 1) root.post(this::openLocationRow);
            else if (firstSave == null) root.post(this::showFirstChooser);   // still finding it: stay
            else {
                firstSave.run();
                showSaved();
                firstRunDone();
            }
        });
        firstChooser = chooser;
        chooserBack = () -> root.post(this::openLocationAsk);
        // Back here leads to "Later" (through its question), not to a plain cancel.
        List<String> h = new ArrayList<>();
        h.add(t.hintMove());
        h.add(t.hintChoose());
        h.add(t.hintBackLater());
        setHints(hints, h);
    }

    /** The place came in while its choice is open: shown at once, focus still on it. */
    private void reopenFirstChooser() {
        if (chooser == null || chooser != firstChooser) return;
        closeChooser();
        showFirstChooser();
    }

    /** Another location: the Location row's own choice (regions, countries, cities, or any place). */
    private void openLocationRow() {
        if (section != PRAYER) {
            section = PRAYER;
            showSection();
        }
        focusRow(0);
        rows.get(0).open();
    }

    /** Back with no place set on the first run: what that leaves, with Set location focused (Back too returns to it). */
    private void openLocationAsk() {
        LinearLayout card = resetCard(820);
        card.addView(resetTitle(t.locationAskTitle(), Gravity.START));
        Prefs.City guess = Prefs.city(this);
        TextView body = resetLine(t.locationAskBody(t.ar ? guess.ar : guess.en), Tokens.MIST_400, 24, null);
        body.setLineSpacing(0, 1.35f);
        // Room under the title, as the reset question's lines have.
        ((LinearLayout.LayoutParams) body.getLayoutParams()).topMargin = Ui.ipx(14);
        card.addView(body);
        LinearLayout buttons = new LinearLayout(this);
        float r = FocusTile.REACH;
        locSet = resetButton(t.setLocation());
        locLater = resetButton(t.later());
        locSet.setOnClickListener(v -> locationAnswer(true));
        locLater.setOnClickListener(v -> locationAnswer(false));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
        b.setMarginStart(Ui.ipx(24));
        buttons.addView(locSet, a);
        buttons.addView(locLater, b);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.ipx(30 - r);
        blp.setMarginStart(-Ui.ipx(r));
        blp.setMarginEnd(-Ui.ipx(r));
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        card.setClipToPadding(false);
        card.addView(buttons, blp);
        showReset(card, 820, 3);
        root.post(locSet::requestFocus);
        List<String> h = new ArrayList<>();
        h.add(t.hintChoose());
        h.add(t.hintBackLocate());
        setHints(hints, h);
    }

    private void locationAnswer(boolean set) {
        closeReset();
        if (set) openFirstLocation();
        else finish();
    }

    private int section = PRAYER;
    private boolean onNav = true;
    private int rowIndex;
    private final FocusTile[] nav = new FocusTile[SECTIONS];
    private final TextView[] navLabel = new TextView[SECTIONS];
    private final List<Row> rows = new ArrayList<>();
    private TextView sectionLabel, previewValue;
    private LinearLayout hints;
    private TextView saved;
    private final Runnable hideSaved = () -> saved.animate().alpha(0).setDuration(Tokens.MOTION_OVERLAY_FADE_MS);
    /** The SEG or STEP row being edited (null: none) and its unsaved choice or amount. */
    private Row editing;
    private int draft;
    private FrameLayout chooser;
    /** What Back in the open chooser goes back to (the city list returns to the countries); null closes it. */
    private Runnable chooserBack;
    /** The voice being previewed in the adhan list. */
    private android.media.MediaPlayer previewPlayer;
    private PrayerDay day;

    @Override
    void build() {
        // A rebuild (back with settings changed elsewhere, or a language change) drops an open chooser with the
        // old root; left set, it would swallow every key while nothing shows.
        stopPreview();
        chooser = null;
        chooserBack = null;
        editing = null;
        // An open reset dialog goes with the old root too (and a wait for a button with it).
        stopHold();
        stopPress();
        resetView = null;
        resetRing = null;
        resetStage = 0;
        newRoot();
        root.setBackgroundColor(Tokens.LAPIS_900);
        readClockFormat();

        View side = new View(this);
        side.setBackgroundColor(Tokens.LAPIS_950);
        place(side, 0, 0, Tokens.LAYOUT_SETTINGS_SIDEBAR_WIDTH, 1080);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Icon mihrab = new Icon(this, Icon.MIHRAB, 1.3f);
        mihrab.setColor(Tokens.GOLD_400);
        head.addView(mihrab, new LinearLayout.LayoutParams(Ui.ipx(36), Ui.ipx(46)));
        TextView title = text(displayBold, 52, Tokens.PLASTER_100);
        title.setText(t.settings());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.setMarginStart(Ui.ipx(16));
        head.addView(title, tlp);
        lineHeight(title, 1.1f);
        // The version by the title, large and gold, on every section, so it is
        // found at a glance when checking an install. "v" before it says what the number is; Latin digits, as the tools
        // print it.
        TextView version = text(ui500, 34, Tokens.GOLD_400);
        version.setText("v" + versionName());
        version.setTextDirection(View.TEXT_DIRECTION_LTR);
        version.setIncludeFontPadding(false);
        // The sections' own corners (the list beside it: 14), not a pill.
        GradientDrawable chip = round(Tokens.LAPIS_800, 14);
        chip.setStroke(Ui.ipx(2), Tokens.GOLD_400);
        version.setBackground(chip);
        version.setPadding(Ui.ipx(20), Ui.ipx(8), Ui.ipx(20), Ui.ipx(8));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.setMarginStart(Ui.ipx(20));
        head.addView(version, vlp);
        head.setClipChildren(false);
        place(head, 96, 56, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        for (int i = 0; i < SECTIONS; i++) nav[i] = navItem(i);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(Ui.ipx(24), Ui.ipx(20), Ui.ipx(24), Ui.ipx(20));
        preview.setBackground(round(Tokens.LAPIS_800, 16));
        TextView pl = text(ui400, 20, Tokens.MIST_400);
        pl.setText(t.preview());
        previewValue = text(display, Tokens.FONT_SIZE_DISPLAY_XS, Tokens.GOLD_400);
        preview.addView(pl);
        preview.addView(previewValue);
        lineHeight(previewValue, 1.2f);
        ((LinearLayout.LayoutParams) previewValue.getLayoutParams()).topMargin += Ui.ipx(6);
        place(preview, 96, 1080 - 56 - 124, 424, 124);

        sectionLabel = caps(text(ui400, 22, Tokens.MIST_400));
        sectionLabel.setAllCaps(!t.arabicScript());
        // Capitals by the screen's language, not the TV's: Turkish "i" is "İ" ("NAMAZ VAKİTLERİ").
        sectionLabel.setTextLocale(java.util.Locale.forLanguageTag(t.lang));
        place(sectionLabel, 640, 56, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        hints = new LinearLayout(this);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        place(hints, 640, 976, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        // ✓ Saved: on the hint line, at the rows' end edge, so the hints never shift when it comes and goes.
        saved = text(ui500, 22, Tokens.GOLD_400);
        saved.setText(t.saved());
        saved.setAlpha(0);
        placeEnd(saved, 1920 - 640 - Tokens.LAYOUT_SETTINGS_ROW_WIDTH, 976,
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        setContentView(root);
        showSection();
        if (onNav || rows.isEmpty()) focusNav();
        else focusRow(Math.min(rowIndex, rows.size() - 1));
        if (firstRunPending && getIntent().getBooleanExtra(FIRST_RUN, false) && section == PRAYER && !rows.isEmpty()) {
            firstRunPending = false;
            focusRow(0);
            root.post(this::openFirstLocation);
        }
    }

    @Override
    void onTick() {
        // Nothing moves on this screen; the preview changes only with the settings.
    }

    // ---------------------------------------------------------------- sections

    private String sectionName(int i) {
        switch (i) {
            case PRAYER: return t.sectionPrayer();
            case IQAMA: return t.sectionIqama();
            case REMINDERS: return t.sectionReminders();
            case RADIO: return t.sectionRadio();
            case DISPLAY: return t.sectionDisplay();
            case NETWORK: return t.sectionNetwork(ssid());
            case RUN: return t.sectionRun();
            case ABOUT: return t.sectionAbout();
            default: return t.sectionDevice();
        }
    }

    private FocusTile navItem(int i) {
        FocusTile b = new FocusTile(this, 14);
        b.setFocusScale(1.04f);
        TextView v = text(ui400, 26, Tokens.MIST_400);
        v.setText(sectionName(i));
        // The screen's direction, not the word's: Urdu's "Awan TV" stood at the left of a right-to-left menu.
        v.setTextDirection(t.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        v.setPadding(Ui.ipx(24), 0, Ui.ipx(24), 0);
        v.setSingleLine(true);
        b.addView(v, new FrameLayout.LayoutParams(-1, -1));
        navLabel[i] = v;
        if (i == ABOUT) {
            // A newer version out: the gold mark at About's end, until the TV has it (UpdateNotice).
            aboutMark = UpdateNotice.mark(this, Tokens.LAPIS_800);
            FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(Ui.ipx(22), Ui.ipx(22), Gravity.CENTER_VERTICAL | Gravity.END);
            mlp.setMarginEnd(Ui.ipx(28));
            b.addView(aboutMark, mlp);
        }
        b.setLook(f -> styleNav(i));
        placeTile(b, 96, NAV_TOP + i * (NAV_H + NAV_GAP), 424, NAV_H);
        return b;
    }

    /** The open section keeps a gold inset ring when focus is in its rows; focused, it is the gold tile. */
    private void styleNav(int i) {
        boolean focused = nav[i] != null && nav[i].isFocused();
        boolean open = i == section;
        if (nav[i] != null) nav[i].setRest(open ? Tokens.LAPIS_800 : 0, Tokens.GOLD_400, open ? 2 : 0);
        navLabel[i].setTextColor(focused ? Tokens.LAPIS_900 : (open ? Tokens.PLASTER_100 : Tokens.MIST_400));
        if (i == ABOUT && aboutMark != null) {
            aboutMark.setVisibility(UpdateNotice.newer(this) != null ? View.VISIBLE : View.GONE);
            // On the focused gold tile, the mark in the tile's dark ink.
            ((GradientDrawable) aboutMark.getBackground()).setColor(focused ? Tokens.LAPIS_900 : Tokens.GOLD_400);
        }
    }

    /** Settings > About's mark of a newer version (UpdateNotice.mark). */
    private View aboutMark;

    private void showSection() {
        for (Row r : rows) root.removeView(r.tile);
        rows.clear();
        sectionLabel.setText(sectionName(section));
        if (section == NETWORK) sectionLabel.setText(t.sectionNetwork(null));
        if (section != ABOUT) {
            statusOpen = false;
            creditsOpen = false;
        }
        if (statusOpen) sectionLabel.setText(t.statusSection());
        if (creditsOpen) sectionLabel.setText(t.creditsSection());
        if (section != DEVICE) buttonsOpen = false;
        if (buttonsOpen) sectionLabel.setText(t.buttonsSection());
        if (section != PRAYER) {
            adjustOpen = false;
            calcOpen = false;
        }
        if (adjustOpen) sectionLabel.setText(t.adjustSection());
        if (calcOpen) sectionLabel.setText(t.calcSection());
        for (int i = 0; i < SECTIONS; i++) styleNav(i);
        rows.addAll(rowsFor(section));
        for (int i = 0; i < rows.size(); i++) addRow(rows.get(i), i);
        if (qrPanel != null) root.removeView(qrPanel);
        qrPanel = qrShown() ? qrPanel() : null;
        if (pageNote != null) root.removeView(pageNote);
        pageNote = buttonsOpen ? buttonsNote() : adjustOpen ? note(t.adjustNote()) : calcOpen ? note(calcNote()) : null;
        refresh();
    }

    // ---------------------------------------------------------------- About's links

    /** About's rows end here, leaving the QR codes their column at the end (as wide as the repository address). */
    private static final float ABOUT_ROW_W = 850, QR_GAP = 30, QR_SIDE = 180, QR_QUIET = 18, QR_LOGO = 40;
    /** The QR codes' own light ground and dark ink, the same on every theme (tools/AwanTV-build-qr.py). */
    private static final int QR_LIGHT = 0xFFEFE7D6, QR_DARK = 0xFF0C1B33;
    private View qrPanel;

    private boolean qrShown() {
        return section == ABOUT && !statusOpen && !creditsOpen;
    }

    /**
     * The two addresses as QR codes, for a phone's camera (most TVs have no browser), after awanpro.app's own
     * download box: one box holding a heading and a box for each code, everything centred. Each code sits on a light
     * rounded tile whose padding is its quiet zone, one pixel per module (res/drawable-nodpi, from
     * tools/AwanTV-build-qr.py) drawn scaled up without smoothing, with its owner's logo in the middle: AwanPro's on
     * the site, Awan TV's arch on the repository (level H codes still read with it).
     */
    private View qrPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = round(Tokens.LAPIS_800, 22);
        bg.setStroke(Ui.ipx(1), (Tokens.GOLD_400 & 0x00FFFFFF) | 0x55000000);
        panel.setBackground(bg);
        panel.setPadding(Ui.ipx(14), Ui.ipx(22), Ui.ipx(14), Ui.ipx(22));
        TextView name = centred(text(ui500, 24, Tokens.PLASTER_100));
        name.setText("Awan TV");
        panel.addView(name, wide(0));
        TextView hint = centred(text(ui400, 19, Tokens.MIST_400));
        // Two lines when the panel is too narrow for one (English, longer languages).
        hint.setSingleLine(false);
        hint.setMaxLines(2);
        hint.setText(t.qrHint());
        panel.addView(hint, wide(4));
        panel.addView(qrBox(R.drawable.qr_site, siteLogo(), t.siteTitle(), Updates.shown(Updates.SITE)), wide(16));
        panel.addView(qrBox(R.drawable.qr_repo, archLogo(), t.sourceRow(), Updates.shown(Updates.PAGE)), wide(14));
        float x = 640 + ABOUT_ROW_W + QR_GAP;
        place(panel, x, ROWS_TOP, 640 + Tokens.LAYOUT_SETTINGS_ROW_WIDTH - x, ViewGroup.LayoutParams.WRAP_CONTENT);
        return panel;
    }

    private LinearLayout.LayoutParams wide(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2);
        lp.topMargin = Ui.ipx(top);
        return lp;
    }

    private static TextView centred(TextView v) {
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        return v;
    }

    /** One code's box: the code on its tile, whose name, and its address in one line. */
    private View qrBox(int image, View logo, String title, String address) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(round(Tokens.LAPIS_700, 16));
        box.setPadding(Ui.ipx(10), Ui.ipx(16), Ui.ipx(10), Ui.ipx(14));

        box.addView(qrTile(image, logo, title + " " + address), new LinearLayout.LayoutParams(-2, -2));

        TextView name = centred(text(ui500, 22, Tokens.GOLD_400));
        name.setText(title);
        // One line, smaller where wider than the code (1.19.0b: Turkish "Kaynak ve güncellemeler" by 22 px).
        name.setSingleLine(true);
        Screen.fitWidth(name, 0.8f);
        box.addView(name, wide(12));
        TextView where = centred(text(ui400, 15, Tokens.MIST_400));
        where.setText(address);
        where.setTextDirection(View.TEXT_DIRECTION_LTR);
        box.addView(where, wide(2));
        return box;
    }

    /** A code on its light rounded tile (the padding is its quiet zone), drawn without smoothing, its logo in the middle. */
    private FrameLayout qrTile(int image, View logo, String description) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(round(QR_LIGHT, 14));
        int quiet = Ui.ipx(QR_QUIET);
        tile.setPadding(quiet, quiet, quiet, quiet);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap bits = BitmapFactory.decodeResource(getResources(), image, o);
        BitmapDrawable d = new BitmapDrawable(getResources(), bits);
        d.setFilterBitmap(false);
        d.setAntiAlias(false);
        ImageView code = new ImageView(this);
        code.setImageDrawable(d);
        code.setScaleType(ImageView.ScaleType.FIT_XY);
        code.setContentDescription(description);
        tile.addView(code, new FrameLayout.LayoutParams(Ui.ipx(QR_SIDE), Ui.ipx(QR_SIDE)));
        // The logo with a ring of the light ground around it, so no module touches it.
        FrameLayout ring = new FrameLayout(this);
        ring.setBackground(round(QR_LIGHT, 13));
        int r = Ui.ipx(4);
        ring.setPadding(r, r, r, r);
        ring.addView(logo, new FrameLayout.LayoutParams(Ui.ipx(QR_LOGO), Ui.ipx(QR_LOGO)));
        tile.addView(ring, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return tile;
    }

    /**
     * A newer version out: which, what this TV has, a QR code for its release page (releases/latest, so one image
     * serves every version) and how to update; Open the release page where the TV has a browser, and Close.
     */
    private void openUpdate(String newer) {
        LinearLayout card = resetCard(1180);
        LinearLayout across = new LinearLayout(this);
        across.setGravity(Gravity.TOP);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setClipChildren(false);
        words.setClipToPadding(false);
        words.addView(resetTitle(t.updateTitle(newer), Gravity.START));
        TextView have = resetLine(t.updateHave(versionName()), Tokens.PLASTER_100, 24, null);
        words.addView(have);
        TextView why = resetLine(t.updateWhy(), Tokens.PLASTER_100, 24, null);
        why.setLineSpacing(0, 1.3f);
        ((LinearLayout.LayoutParams) why.getLayoutParams()).topMargin = Ui.ipx(10);
        words.addView(why);
        boolean self = SelfUpdate.allowed(this);
        TextView how = resetLine(self ? t.updateHowSelf() : t.updateHow(), Tokens.MIST_400, 24, null);
        how.setLineSpacing(0, 1.3f);
        ((LinearLayout.LayoutParams) how.getLayoutParams()).topMargin = Ui.ipx(10);
        words.addView(how);
        TextView where = resetLine(Updates.shown(Updates.RELEASE), Tokens.MIST_400, 18, null);
        where.setTextDirection(View.TEXT_DIRECTION_LTR);
        where.setGravity(Gravity.START);
        words.addView(where);
        if (!self) {
            // Installed by hand (no install appop): how to get Update now, instead of a button that can't work.
            TextView manual = resetLine(t.updateManual(), Tokens.GOLD_400, 22, null);
            manual.setLineSpacing(0, 1.3f);
            ((LinearLayout.LayoutParams) manual.getLayoutParams()).topMargin = Ui.ipx(10);
            words.addView(manual);
        }
        boolean browse = !self && canBrowse(Updates.RELEASE);
        LinearLayout buttons = new LinearLayout(this);
        float r = FocusTile.REACH;
        askFirst = resetButton(self ? t.updateNow() : browse ? t.updateOpen() : t.close());
        askSecond = self || browse ? resetButton(t.close()) : null;
        // Update now: the download starts, and Home shows it (its window follows SelfUpdate).
        askOnFirst = self ? () -> {
            SelfUpdate.start(this, newer);
            startSafely(new Intent(this, MainActivity.class));
            finish();
        } : browse ? () -> startSafely(link(Updates.RELEASE)) : null;
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
        buttons.addView(askFirst, a);
        if (askSecond != null) {
            LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
            b.setMarginStart(Ui.ipx(24));
            buttons.addView(askSecond, b);
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.ipx(30 - r);
        blp.setMarginStart(-Ui.ipx(r));
        blp.setMarginEnd(-Ui.ipx(r));
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        words.addView(buttons, blp);
        across.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-2, -2);
        qlp.setMarginStart(Ui.ipx(40));
        across.addView(qrTile(R.drawable.qr_release, archLogo(), Updates.shown(Updates.RELEASE)), qlp);
        across.setClipChildren(false);
        card.setClipToPadding(false);
        card.addView(across, new LinearLayout.LayoutParams(-1, -2));
        showReset(card, 1180, ASK);
        root.post(askFirst::requestFocus);
        List<String> h = new ArrayList<>();
        h.add(t.hintChoose());
        h.add(t.hintBackClose());
        setHints(hints, h);
    }

    /** AwanPro's logo (res/drawable-nodpi/awanpro_logo.png), with its rounded corners as on its site. */
    private View siteLogo() {
        ImageView v = new ImageView(this);
        v.setImageResource(R.drawable.awanpro_logo);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        v.setBackground(round(QR_DARK, 10));
        v.setClipToOutline(true);
        return v;
    }

    /** Awan TV's arch, gold on the codes' dark ink. */
    private View archLogo() {
        FrameLayout v = new FrameLayout(this);
        v.setBackground(round(QR_DARK, 10));
        Icon arch = new Icon(this, Icon.MIHRAB, 1.3f);
        arch.setColor(Tokens.GOLD_400);
        v.addView(arch, new FrameLayout.LayoutParams(Ui.ipx(18), Ui.ipx(23), Gravity.CENTER));
        return v;
    }

    private static Intent link(String url) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** True when something on this TV opens a web page (a browser some TVs have, or one the viewer added). */
    private boolean canBrowse(String url) {
        try {
            return link(url).resolveActivity(getPackageManager()) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private List<Row> rowsFor(int s) {
        Context c = this;
        switch (s) {
            case PRAYER: if (adjustOpen) return adjustRows();
                if (calcOpen) return calcRows();
                return withHabous(Arrays.asList(
                new Row(VALUE) {
                    String label() { return t.location(); }
                    String help() {
                        Prefs.City city = Prefs.city(c);
                        return t.locationHelp(city.lat, city.lng, Prefs.CUSTOM.equals(city.id));
                    }
                    String value() {
                        Prefs.City city = Prefs.city(c);
                        // A place by its coordinates has only the name the viewer gave it (and its country,
                        // when it came from the world list).
                        if (Prefs.CUSTOM.equals(city.id) && city.countryEn.isEmpty()) return city.ar;
                        return t.place(t.ar ? city.ar : city.en, Prefs.countryOf(city, t));
                    }
                    void open() {
                        // After the six regions, any other place by its coordinates (PlaceActivity).
                        String[] regions = Arrays.copyOf(t.regions(), Prefs.REGION_CUSTOM + 1);
                        regions[Prefs.REGION_CUSTOM] = t.otherPlace();
                        // The next list opens once this chooser has closed (closeChooser runs after the pick).
                        openChooser(t.region(), regions, Prefs.region(Prefs.city(c)), i -> {
                            if (i == Prefs.REGION_CUSTOM) startSafely(new Intent(c, PlaceActivity.class));
                            else root.post(() -> openCountries(i, this::open));
                        });
                    }
                },
                new Row(VALUE) {
                    String label() { return t.method(); }
                    String help() {
                        // A new location just took the custom calculation or the minutes away: said here, once.
                        if (Prefs.placeReset) return t.calcReset();
                        Prefs.Calc k = Prefs.calc(c);
                        if (k != null) return t.calcSummary(k);
                        Method m = Prefs.method(c);
                        return m == Method.MOROCCO_HABOUS ? t.habousAngles(Habous.table(c) != null) : t.methodAngles(m);
                    }
                    boolean warn() { return Prefs.placeReset; }
                    String value() { return Prefs.calcOn(c) ? t.calcName() : t.methodName(Prefs.method(c)); }
                    void open() {
                        // The methods, then Custom calculation (Muslim Pro's and Pillars' place for it: the list's end).
                        String[] names = new String[Prefs.METHODS.length + 1];
                        int sel = 0;
                        for (int i = 0; i < Prefs.METHODS.length; i++) {
                            names[i] = t.methodName(Prefs.METHODS[i]);
                            if (Prefs.METHODS[i] == Prefs.method(c)) sel = i;
                        }
                        names[Prefs.METHODS.length] = t.calcItem();
                        if (Prefs.calcOn(c)) sel = Prefs.METHODS.length;
                        openChooser(t.method(), names, sel, i -> {
                            Prefs.placeReset = false;
                            if (i == Prefs.METHODS.length) {
                                // Its page, on: from the method in use, or as it was last set.
                                Prefs.setCalcOn(c, true);
                                calcOpen = true;
                                root.post(() -> { showSection(); focusRow(0); });
                                return;
                            }
                            Prefs.setMethod(c, Prefs.METHODS[i]);
                            showSaved();
                            // Morocco's method brings its Ministry table row, another takes it away.
                            if (habousShown != (habousTable() != null)) root.post(() -> { showSection(); focusRow(1); });
                        });
                    }
                },
                new Row(SEG) {
                    String label() { return t.asrSchool(); }
                    String help() { return t.asrSchoolHelp(); }
                    String[] segs() { return t.asrSchools(); }
                    int selected() { return Prefs.madhab(c) == Madhab.HANAFI ? 1 : 0; }
                    void select(int i) { Prefs.setMadhab(c, i == 1 ? Madhab.HANAFI : Madhab.SHAFI); }
                },
                // The six times' minutes and the Hijri date on a page of their own (Prayer times had no room left).
                new Row(VALUE) {
                    String label() { return t.adjustRow(); }
                    String help() { return t.adjustRowHelp(); }
                    String value() { return t.adjustSummary(Prefs.adjustments(c), Prefs.hijriOffset(c)); }
                    void open() {
                        adjustOpen = true;
                        showSection();
                        focusRow(0);
                    }
                },
                new Row(VALUE) {
                    String label() { return t.roomRow(); }
                    String help() { return t.roomHelp(); }
                    String value() { int f = facing(); return f < 0 ? t.notSet() : t.bearingLabel(f); }
                    void open() { startSafely(new Intent(c, PrayerDayActivity.class).putExtra("room", true)); }
                }));
            case IQAMA: return iqamaRows();
            case REMINDERS: return Arrays.asList(
                new Row(VALUE) {
                    // Six choices are too many for a segmented control: a list, like the adhan voices.
                    final int[] minutes = {5, 10, 15, 20, 30, 0};
                    String label() { return t.remind(); }
                    String help() { return t.remindHelp(); }
                    int selected() {
                        int m = Prefs.remindMinutes(c);
                        for (int i = 0; i < minutes.length; i++) if (minutes[i] == m) return i;
                        return 2;
                    }
                    String value() { return digits(t.remindChoices())[selected()]; }
                    void open() {
                        openChooser(t.remind(), digits(t.remindChoices()), selected(),
                            i -> { Prefs.setRemindMinutes(c, minutes[i]); showSaved(); });
                    }
                },
                new Row(TOGGLE) {
                    String label() { return t.remindVoiceRow(); }
                    String help() { return t.remindVoiceHelp(); }
                    boolean on() { return Prefs.remindVoice(c); }
                    void set(boolean on) { Prefs.setRemindVoice(c, on); }
                },
                new Row(TOGGLE) {
                    String label() { return t.adhanRow(); }
                    String help() { return t.adhanHelp(); }
                    boolean on() { return Prefs.adhan(c); }
                    void set(boolean on) { Prefs.setAdhan(c, on); }
                },
                new Row(TOGGLE) {
                    String label() { return t.announceRow(); }
                    String help() { return t.announceHelp(); }
                    boolean on() { return Prefs.announce(c); }
                    void set(boolean on) { Prefs.setAnnounce(c, on); }
                },
                new Row(SEG) {
                    final int[] minutes = {0, 1, 5, 10};
                    String label() { return t.callCloseRow(); }
                    String help() { return t.callCloseHelp(); }
                    String[] segs() { return digits(t.callCloseChoices()); }
                    int selected() {
                        int m = Prefs.callCloseMinutes(c);
                        for (int i = 0; i < minutes.length; i++) if (minutes[i] == m) return i;
                        return minutes.length - 1;
                    }
                    void select(int i) { Prefs.setCallCloseMinutes(c, minutes[i]); }
                },
                voiceRow(false),
                voiceRow(true),
                // The morning and evening adhkar on the screensaver.
                new Row(SEG) {
                    String label() { return t.adhkarRow(); }
                    String help() { return t.adhkarHelp(Adhkar.mode(c)); }
                    String[] segs() { return t.adhkarModes(); }
                    int selected() { return Adhkar.mode(c); }
                    void select(int i) { Adhkar.setMode(c, i); }
                },
                // Tomorrow's sunnah fast in the gold line, on Home and the screensaver alike.
                new Row(TOGGLE) {
                    String label() { return t.fastRow(); }
                    String help() { return t.fastHelp(); }
                    boolean on() { return Prefs.fastReminder(c); }
                    void set(boolean on) { Prefs.setFastReminder(c, on); }
                });
            case RADIO:
                // Mp3quran.net's list of reciter channels, read again when a week old.
                Radios.refresh(c);
                return Arrays.asList(
                new Row(TOGGLE) {
                    String label() { return t.station(Radio.station(c)); }
                    String help() { return t.radioRowHelp(Radio.station(c)); }
                    boolean on() { return Radio.on(); }
                    void set(boolean on) { if (on) Radio.start(c); else Radio.stop(); }
                    boolean saves() { return false; }
                },
                new Row(VALUE) {
                    String label() { return t.stationRow(); }
                    String help() { Radio.Station s = Radio.station(c); return t.stationGroups()[s.url == null ? 2 : s.reciter ? 1 : 0]; }
                    String value() { return t.stationChoice(Radio.station(c)); }
                    void open() {
                        Radio.Station now = Radio.station(c);
                        int sel = now.url == null ? 2 : now.reciter ? 1 : 0;
                        // The third group, the recordings saved on the TV, only when there are some.
                        String[] groups = t.stationGroups();
                        if (Library.savedKeys(c).isEmpty()) groups = Arrays.copyOf(groups, 2);
                        // The station list opens once this chooser has closed (closeChooser runs after the pick).
                        openChooser(t.stationRow(), groups, sel, i -> root.post(() -> {
                            if (i == 2) openSaved(this::open);
                            else openStations(i == 1, this::open);
                        }));
                    }
                },
                // The recitations saved on the TV (LibraryActivity).
                new Row(VALUE) {
                    String label() { return t.libraryRow(); }
                    String help() {
                        int n = Library.savedKeys(c).size();
                        return t.libraryHelp(n, t.bytes(Library.savedBytes(c, null)));
                    }
                    void open() { startSafely(new Intent(c, LibraryActivity.class)); }
                },
                new Row(SEG) {
                    String label() { return t.afterAdhanRow(); }
                    String help() { return t.afterAdhanHelp(); }
                    String[] segs() { return t.afterAdhanChoices(); }
                    int selected() { return Prefs.radioResume(c) ? 0 : 1; }
                    void select(int i) { Prefs.setRadioResume(c, i == 0); }
                },
                new Row(SEG) {
                    final int[] minutes = {0, 15, -1};
                    String label() { return t.radioDarkRow(); }
                    String help() { return t.radioDarkHelp(); }
                    String[] segs() { return digits(t.radioDarkChoices()); }
                    int selected() {
                        int m = Prefs.radioScreenOff(c);
                        for (int i = 0; i < minutes.length; i++) if (minutes[i] == m) return i;
                        return 0;
                    }
                    void select(int i) { Prefs.setRadioScreenOff(c, minutes[i]); }
                });
            case DISPLAY: return Arrays.asList(
                // The language: the same list as Home's language button, then Keep or Go back (Screen.chooseLanguage).
                new Row(VALUE) {
                    final String[] values = Languages.values();
                    String label() { return t.languageBoth(); }
                    String help() { return t.languageHelp(); }
                    int selected() { return Math.max(0, Arrays.asList(values).indexOf(prefs().getString("locale", "system"))); }
                    String value() { return languageItems()[selected()]; }
                    void open() { openLanguages(i -> chooseLanguage(values[i])); }
                },
                new Row(SEG) {
                    String label() { return t.digits(); }
                    String[] segs() { return new String[] {"0123", Languages.easternDigits(t.lang)}; }
                    int selected() { return t.eastern ? 1 : 0; }
                    void select(int i) { prefs().edit().putString("digits", i == 1 ? "eastern" : "western").apply(); relaunch(); }
                },
                new Row(SEG) {
                    final String[] values = {"system", "12", "24"};
                    String label() { return t.clockRow(); }
                    String help() { return t.clockHelp(); }
                    String[] segs() { return digits(t.clocks()); }
                    int selected() { return Math.max(0, Arrays.asList(values).indexOf(Prefs.clock(c))); }
                    void select(int i) { prefs().edit().putString("clock", values[i]).apply(); readClockFormat(); }
                },
                // The colour theme (Tokens), a list of six.
                new Row(VALUE) {
                    String label() { return t.themeRow(); }
                    String help() { return t.themeHelp(); }
                    int selected() { return Prefs.theme(c); }
                    String value() { return t.themes()[selected()]; }
                    void open() {
                        openChooser(t.themeRow(), t.themes(), selected(), i -> { Prefs.setTheme(c, i); relaunch(); });
                    }
                },
                // Home's background, the pattern or the day's photo (Photos).
                new Row(SEG) {
                    String label() { return t.backgroundRow(); }
                    String help() { return t.backgroundHelp(); }
                    String[] segs() { return t.backgrounds(); }
                    int selected() { return Prefs.background(c); }
                    void select(int i) { Prefs.setBackground(c, i); }
                },
                // Photos behind the screensaver too (Photos), the dark ones at night. Here beside Home's
                // background, since the Awan TV section has no room for a ninth row.
                new Row(TOGGLE) {
                    String label() { return t.saverPhotosRow(); }
                    String help() { return t.saverPhotosHelp(); }
                    boolean on() { return Prefs.saverPhotos(c); }
                    void set(boolean on) { Prefs.setSaverPhotos(c, on); }
                },
                // How often the photo changes, on Home and in the screensaver.
                new Row(VALUE) {
                    String label() { return t.photoEveryRow(); }
                    String help() { return t.d(t.photoEveryHelp()); }
                    int selected() { return Prefs.photoEvery(c); }
                    String value() { return digits(t.photoEveryChoices())[selected()]; }
                    void open() {
                        openChooser(t.photoEveryRow(), digits(t.photoEveryChoices()), selected(), i -> Prefs.setPhotoEvery(c, i));
                    }
                },
                // The weather by the clock (Weather), off or in °C / °F.
                new Row(SEG) {
                    final String[] values = {"off", "c", "f"};
                    String label() { return t.weatherRow(); }
                    String help() { return t.weatherHelp(!"off".equals(Prefs.weather(c))); }
                    String[] segs() { return t.weatherChoices(); }
                    int selected() { return Math.max(0, Arrays.asList(values).indexOf(Prefs.weather(c))); }
                    void select(int i) { Prefs.setWeather(c, values[i]); }
                });
            case RUN: return runRows();
            case NETWORK: return Arrays.asList(
                new Row(VALUE) {
                    String label() { return t.networkRow(); }
                    String help() { return t.networkHelp(); }
                    String value() { String s = ssid(); return s == null ? "" : s; }
                    void open() { startSafely(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
                });
            case ABOUT: {
                if (statusOpen) return statusRows();
                if (creditsOpen) return creditRows();
                // Eight rows at most, so the last one stays above the key hints (the sources have a page of their own).
                return new ArrayList<>(Arrays.asList(
                new Row(VALUE) {
                    String label() { return t.statusRow(); }
                    String help() { return t.statusHelp(); }
                    void open() {
                        statusOpen = true;
                        showSection();
                        focusRow(0);
                    }
                },
                // The version is by the title; here, where Awan TV lives and a newer version when one is out (Updates).
                // A newer version: OK opens its window (a QR code for every TV); else the page where the TV has a
                // browser, the QR codes beside serving every other TV.
                new Row(canBrowse(Updates.PAGE) || Updates.newer(c, versionName()) != null ? VALUE : INFO) {
                    String label() { return t.sourceRow(); }
                    String help() {
                        String newer = Updates.newer(c, versionName());
                        String page = Updates.shown(Updates.PAGE);
                        return newer == null ? page : t.newVersion(newer) + t.sep() + page;
                    }
                    void open() {
                        String newer = Updates.newer(c, versionName());
                        if (newer != null) openUpdate(newer);
                        else if (canBrowse(Updates.PAGE)) startSafely(link(Updates.PAGE));
                    }
                },
                // The daily question to GitHub, at once, its answer beside it while Settings is open.
                new Row(VALUE) {
                    String label() { return t.checkNowRow(); }
                    String help() { return t.checkNowHelp(); }
                    String value() { return checkNowAnswer; }
                    void open() { checkNow(this); }
                },
                // What's new in every version written (NewsActivity), as after an update.
                new Row(VALUE) {
                    String label() { return t.newsRow(); }
                    String help() { return t.newsRowHelp(); }
                    void open() { startSafely(new Intent(c, NewsActivity.class).putExtra(NewsActivity.ALL, true)); }
                },
                new Row(canBrowse(Updates.SITE) ? VALUE : INFO) {
                    String label() { return t.byAwan(); }
                    String help() { return Updates.shown(Updates.SITE); }
                    void open() { startSafely(link(Updates.SITE)); }
                },
                new Row(VALUE) {
                    String label() { return t.creditsRow(); }
                    String help() { return t.creditsHelp(); }
                    void open() {
                        creditsOpen = true;
                        showSection();
                        focusRow(0);
                    }
                },
                // Every setting back to a new install's.
                new Row(VALUE) {
                    String label() { return t.resetRow(); }
                    String help() { return t.resetHelp(); }
                    void open() { openResetAsk(); }
                }));
            }
            default: {
                if (buttonsOpen) return buttonsRows();
                List<Row> device = new ArrayList<>();
                device.add(new Row(VALUE) {
                    String label() { return t.deviceRow(); }
                    String help() { return t.deviceHelp(); }
                    void open() { startSafely(new Intent(Settings.ACTION_SETTINGS)); }
                });
                // On an HDMI source box only (Google TV Streamer), Home's button taken by Awan TV.
                if (HomeGuard.hdmiSource()) {
                    device.add(new Row(TOGGLE) {
                        String label() { return t.homeButtonRow(); }
                        String help() { return t.homeButtonHelp(); }
                        boolean on() { return Prefs.homeButton(c); }
                        void set(boolean on) {
                            Prefs.setHomeButton(c, on);
                            if (RemoteKeys.guard != null) RemoteKeys.guard.refresh();
                        }
                    });
                }
                // The viewer's own buttons (Buttons), on every TV.
                device.add(new Row(VALUE) {
                    String label() { return t.buttonsRow(); }
                    String help() { return t.buttonsHelp(Buttons.all(c).size()); }
                    void open() {
                        buttonsOpen = true;
                        showSection();
                        focusRow(0);
                    }
                });
                return device;
            }
        }
    }

    /**
     * What runs (Home over the TV's own, the screensaver, both or neither), when Awan TV opens its own
     * screensaver, and the ways out: the TV's own home screen, the service, and removing the app cleanly.
     */
    private List<Row> runRows() {
        Context c = this;
        List<Row> out = new ArrayList<>();
        out.add(new Row(SEG) {
            String label() { return t.runRow(); }
            String help() { return t.runHelp(Prefs.run(c), RemoteKeys.guard != null); }
            String[] segs() { return t.runModes(); }
            int selected() { return Prefs.run(c); }
            void select(int i) {
                Prefs.setRun(c, i);
                if (RemoteKeys.guard != null) RemoteKeys.guard.refresh();
                Saver.apply(c);
                // The screensaver's row comes and goes with it.
                root.post(() -> {
                    showSection();
                    focusRow(0);
                });
            }
        });
        if (Prefs.saver(c)) {
            out.add(new Row(SEG) {
                final int[] minutes = {5, 10, 15, 30};
                String label() { return t.saverIdleRow(); }
                String help() { return t.saverIdleHelp(Saver.isOurs(c)); }
                String[] segs() { return digits(t.saverIdleChoices()); }
                int selected() {
                    int m = Prefs.saverIdle(c);
                    for (int i = 0; i < minutes.length; i++) if (minutes[i] == m) return i;
                    return 1;
                }
                void select(int i) { Prefs.setSaverIdle(c, minutes[i]); }
            });
            // The hadith in the screensaver's middle, and its night dimming.
            // What the middle shows: the hadith, the adhkar after the prayer, both or nothing (one row,
            // so the section still fits on the screen).
            out.add(new Row(SEG) {
                String label() { return t.middleRow(); }
                String help() { return t.middleHelp(); }
                String[] segs() { return t.middleChoices(); }
                int selected() {
                    boolean h = Prefs.hadith(c), a = Prefs.afterPrayer(c);
                    return h && a ? 0 : h ? 1 : a ? 2 : 3;
                }
                void select(int i) {
                    Prefs.setHadith(c, i == 0 || i == 1);
                    Prefs.setAfterPrayer(c, i == 0 || i == 2);
                }
            });
            out.add(new Row(TOGGLE) {
                String label() { return t.nightDimRow(); }
                String help() { return t.nightDimHelp(); }
                boolean on() { return Prefs.nightDim(c); }
                void set(boolean on) { Prefs.setNightDim(c, on); }
            });
        }
        out.add(new Row(VALUE) {
            String label() { return t.saverNowRow(); }
            String help() { return t.saverNowHelp(); }
            void open() { startSafely(SaverActivity.now(c)); }
        });
        out.add(new Row(VALUE) {
            String label() { return t.stockRow(); }
            String help() { return t.stockHelp(); }
            void open() { HomeGuard.openStock(c); }
        });
        out.add(new Row(VALUE) {
            String label() { return t.accessibilityRow(); }
            String help() { return t.accessibilityHelp(); }
            void open() { startSafely(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        });
        out.add(new Row(VALUE) {
            String label() { return t.removeRow(); }
            String help() { return t.removeHelp(); }
            void open() {
                // Focus starts on Cancel: removing takes a deliberate step.
                openChooser(t.removeRow(), t.removeChoices(), 1, i -> {
                    if (i != 0) return;
                    // Saved recitations stay in Music/Awan TV after the app goes, unless the viewer says otherwise.
                    if (Library.savedBytes(c, null) > 0) root.post(SettingsActivity.this::askRecitations);
                    else remove();
                });
            }
        });
        return out;
    }

    /** Removing Awan TV with saved recitations: keep them (focused) for a later install, or delete them too. */
    private void askRecitations() {
        openChooser(t.removeRecitationsTitle(), t.removeRecitationsSub(t.bytes(Library.savedBytes(this, null))),
            t.removeRecitationsChoices(), 0, i -> {
                if (i == 0) {
                    remove();
                    return;
                }
                java.util.List<java.io.File> kept = Library.deleteAll(this);
                // Some saved before a reinstall: Android asks first, and its answer (onActivityResult) goes on.
                if (!Library.askToDelete(this, kept, REMOVE_ASKED)) remove();
            }, null);
    }

    private static final int REMOVE_ASKED = 52;

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REMOVE_ASKED) remove();
    }

    /**
     * The screensaver Awan TV replaced back, the radio off, then Android's own uninstall. If the viewer
     * cancels it, the next return here puts Awan TV's screensaver back in step with the Run setting.
     */
    private void remove() {
        Saver.leave(this);
        // TCL's remapping outlives the app: the buttons back to the TV's own first.
        TclKeys.leave(this);
        Intent i = new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + getPackageName()));
        startSafely(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from Another place (PlaceActivity) with a place saved on the first run: Home.
        if (Prefs.citySet(this)) firstRunDone();
        // Keep the TV's screensaver to put back later, and in step with the Run setting.
        Saver.remember(this);
        Saver.apply(this);
        // The buttons back in TCL's remapping after a cancelled Remove Awan TV (no change: nothing written).
        RemoteKeys.buttonsChanged();
        if (rows != null && !rows.isEmpty() && chooser == null && day != null) {
            // App status read again (back from the TV's settings, say).
            if (statusOpen && !onNav) {
                showSection();
                focusRow(Math.min(rowIndex, rows.size() - 1));
            } else if (section == PRAYER && !adjustOpen && !calcOpen && habousShown != (habousTable() != null)) {
                // Back from Another place with a place in Morocco (or out of it): the Ministry table row comes or goes.
                showSection();
                focusRow(Math.min(rowIndex, rows.size() - 1));
            } else {
                refresh();
            }
        }
    }

    /**
     * The Iqamah section. The mode row, then one row per prayer: read-only for the country's official
     * wait, a minutes stepper, or a time stepper in five-minute steps. Changing the mode rebuilds the rows.
     */
    private List<Row> iqamaRows() {
        Context c = this;
        List<Row> out = new ArrayList<>();
        out.add(new Row(SEG) {
            String label() { return t.iqamaRow(); }
            String help() {
                Prefs.City city = Prefs.city(c);
                return t.iqamaModeHelp(Iqama.mode(c), Prefs.countryOf(city, t), Iqama.preset(c) != null);
            }
            String[] segs() { return t.iqamaModes(); }
            int selected() { return Iqama.mode(c); }
            void select(int i) {
                if (i == Iqama.FIXED && day != null) {
                    for (int p : Iqama.PRAYERS) if (Iqama.fixed(c, p) < 0) Iqama.setFixed(c, p, Iqama.suggestedFixed(c, day, p));
                }
                Iqama.setMode(c, i);
                root.post(() -> {
                    showSection();
                    focusRow(0);
                });
            }
        });
        int mode = Iqama.mode(c);
        if (mode == Iqama.OFF) return out;
        String[] names = t.prayerNames();
        // Which days the rows below edit: the other days, or Ramadan's own minutes.
        out.add(new Row(SEG) {
            String label() { return t.iqamaPeriodRow(); }
            String help() { return t.iqamaPeriodHelp(iqamaRamadan); }
            String[] segs() { return t.iqamaPeriods(); }
            int selected() { return iqamaRamadan ? 1 : 0; }
            void select(int i) {
                iqamaRamadan = i == 1;
                root.post(() -> {
                    showSection();
                    focusRow(1);
                });
            }
            boolean saves() { return false; }
        });
        if (iqamaRamadan) {
            for (int p : Iqama.PRAYERS) {
                out.add(new Row(STEP) {
                    String label() { return names[p]; }
                    String help() { return p == PrayerDay.DHUHR ? t.fridayNote() : ""; }
                    int amount() { return Iqama.ramadanMinutes(c, p); }
                    void setAmount(int n) { Iqama.setRamadanMinutes(c, p, n); }
                    int lowest() { return -1; }
                    int highest() { return 60; }
                    String valueOf(int n) { return n < 0 ? t.asUsual() : t.minutesAfter(n); }
                });
            }
            out.add(iqamaCounterRow());
            return out;
        }
        for (int p : Iqama.PRAYERS) {
            String friday = p == PrayerDay.DHUHR ? t.sep() + t.fridayNote() : "";
            if (mode == Iqama.FIXED) {
                out.add(new Row(STEP) {
                    String label() { return names[p]; }
                    String help() { return t.adhanToday(time(day.times[p], 1f)) + friday; }
                    int amount() { return Math.max(0, Iqama.fixed(c, p)) / 5; }
                    void setAmount(int n) { Iqama.setFixed(c, p, n * 5); }
                    int lowest() { return 0; }
                    int highest() { return 24 * 60 / 5 - 1; }
                    String valueOf(int n) { return clockOf(n * 5).toString(); }
                });
            } else if (mode == Iqama.MINUTES) {
                out.add(new Row(STEP) {
                    String label() { return names[p]; }
                    String help() { return iqamaToday(p) + friday; }
                    int amount() { return Iqama.minutes(c, p); }
                    void setAmount(int n) { Iqama.setMinutes(c, p, n); }
                    int lowest() { return 0; }
                    int highest() { return 60; }
                    String valueOf(int n) { return t.minutesAfter(n); }
                });
            } else {
                int[] preset = Iqama.preset(c);
                out.add(new Row(INFO) {
                    String label() { return names[p]; }
                    String help() { return t.minutesAfter(preset[p]) + t.sep() + iqamaToday(p) + friday; }
                });
            }
        }
        out.add(iqamaCounterRow());
        return out;
    }

    /** The iqamah countdown over other apps; the eighth row at most. */
    private Row iqamaCounterRow() {
        Context c = this;
        return new Row(TOGGLE) {
            String label() { return t.iqamaCounterRow(); }
            String help() { return t.iqamaCounterHelp(); }
            boolean on() { return Prefs.iqamaCounter(c); }
            void set(boolean on) {
                Prefs.setIqamaCounter(c, on);
                if (RemoteKeys.watch != null) RemoteKeys.watch.refreshCounter();
            }
        };
    }

    /**
     * Prayer times' own rows, then Friday's khutbah (by country, at Dhuhr or
     * a fixed time, whose time row shows only then) and Ramadan's imsak (none by default). Eight rows at most.
     */
    /** Whether Prayer times shows the Ministry table row (built with it), for a rebuild when that changes. */
    private boolean habousShown;

    /** The ministry's table the place takes when Morocco's method is chosen; null otherwise. */
    private Habous.Locality habousTable() {
        return Prefs.method(this) == Method.MOROCCO_HABOUS && !Prefs.calcOn(this) ? Habous.table(this) : null;
    }

    /** Prayer times' rows, with Morocco's Ministry table after the method when it applies, then Friday and imsak. */
    private List<Row> withHabous(List<Row> prayer) {
        Context c = this;
        List<Row> out = new ArrayList<>(prayer);
        habousShown = habousTable() != null;
        if (habousShown) out.add(2, new Row(VALUE) {
            String label() { return t.habousRow(); }
            String help() {
                Habous.Locality l = habousTable();
                Prefs.City city = Prefs.city(c);
                if (l == null) return "";
                return t.habousHelp((int) Math.round(Places.km(city.lat, city.lng, l.lat, l.lng)),
                    Prefs.of(c).getInt(Habous.PREF, 0) != 0);
            }
            String value() { Habous.Locality l = habousTable(); return l == null ? "" : l.name(t.ar); }
            void open() {
                Prefs.City city = Prefs.city(c);
                List<Habous.Locality> near = Habous.nearest(c, city.lat, city.lng);
                near = new ArrayList<>(near.subList(0, Math.min(HABOUS_CHOICES, near.size())));
                Habous.Locality now = habousTable();
                // The one in use first when it is not among the nearest (chosen before, far off).
                if (now != null && !near.contains(now)) near.add(0, now);
                String[] names = new String[near.size()];
                int sel = 0;
                for (int i = 0; i < names.length; i++) {
                    Habous.Locality l = near.get(i);
                    names[i] = t.habousItem(l.name(t.ar), (int) Math.round(Places.km(city.lat, city.lng, l.lat, l.lng)));
                    if (l == now) sel = i;
                }
                List<Habous.Locality> list = near;
                openChooser(t.habousRow(), t.habousChooser(), names, sel, i -> {
                    // The automatic one picked again stays automatic (it follows a new place by itself).
                    Habous.Locality pick = list.get(i);
                    Habous.choose(c, pick == Habous.automatic(c) ? 0 : pick.id);
                    showSaved();
                }, null);
            }
        });
        return withFridayAndImsak(out);
    }

    /** How many of the ministry's localities the Ministry table list offers, nearest first. */
    private static final int HABOUS_CHOICES = 10;

    private List<Row> withFridayAndImsak(List<Row> prayer) {
        Context c = this;
        List<Row> out = new ArrayList<>(prayer);
        out.add(new Row(SEG) {
            String label() { return t.fridayRow(); }
            String help() {
                int preset = Jumuah.presetMinutes(c);
                Prefs.City city = Prefs.city(c);
                return t.fridayHelp(Prefs.fridayMode(c), preset < 0 ? null : clockOf(preset), Prefs.countryOf(city, t));
            }
            String[] segs() { return t.fridayChoices(); }
            int selected() { return Prefs.fridayMode(c); }
            void select(int i) {
                Prefs.setFridayMode(c, i);
                // The time row comes and goes with "Fixed time".
                int at = rowIndex;
                root.post(() -> {
                    showSection();
                    focusRow(at);
                });
            }
        });
        if (Prefs.fridayMode(c) == Prefs.FRIDAY_FIXED) {
            out.add(new Row(STEP) {
                String label() { return t.fridayTimeRow(); }
                String help() { return t.fridayTimeHelp(); }
                int amount() { return Prefs.fridayFixed(c) / 5; }
                void setAmount(int n) { Prefs.setFridayFixed(c, n * 5); }
                int lowest() { return 11 * 12; }
                int highest() { return 14 * 12; }
                String valueOf(int n) { return clockOf(n * 5).toString(); }
            });
        }
        out.add(new Row(SEG) {
            final int[] minutes = {0, 5, 10, 15, 20};
            String label() { return t.imsakRow(); }
            String help() { return t.imsakHelp(); }
            String[] segs() { return digits(t.imsakChoices()); }
            int selected() {
                int m = Prefs.imsak(c);
                for (int i = 0; i < minutes.length; i++) if (minutes[i] == m) return i;
                return 0;
            }
            void select(int i) { Prefs.setImsak(c, minutes[i]); }
        });
        return out;
    }

    /**
     * What was checked over adb, on the screen: the service, the screensaver,
     * the last adhan the call screen played, the next one, the settings permission, the weather and the version.
     */
    private List<Row> statusRows() {
        Context c = this;
        if (day == null) day = new PrayerDay(this, new Date());
        List<Row> out = new ArrayList<>();
        boolean service = RemoteKeys.guard != null;
        out.add(status(t.serviceRow(), service ? t.serviceHelp() : t.serviceOffHelp(), t.running(service),
            service ? Tokens.STATUS_OK : Tokens.STATUS_BAD));

        int saver = !Prefs.saver(c) ? 2 : Saver.isOurs(c) ? 0 : 1;
        out.add(status(t.saverStatusRow(), t.saverStatusHelp(saver), saver == 0 ? "Awan TV" : saver == 1 ? t.fromHome() : t.off(),
            saver == 0 ? Tokens.STATUS_OK : saver == 1 ? Tokens.STATUS_WARN : 0));

        String[] names = t.prayerNames();
        android.content.SharedPreferences p = Prefs.of(c);
        String[] last = p.getString("last_adhan", "").split(":");
        if (last.length == 3) {
            boolean ok = p.getBoolean("last_adhan_ok", true);
            int prayer = Math.max(0, Math.min(names.length - 1, parse(last[0])));
            Date at = new Date(p.getLong("last_adhan_at", 0));
            String when = android.text.format.DateUtils.isToday(at.getTime()) ? t.todayWord() + " " + time(at, 1f)
                : gregorian(at) + " " + time(at, 1f);
            out.add(status(t.lastAdhanRow(), names[prayer] + " " + when + t.sep() + Adhans.name(t, last[2]), t.played(ok),
                ok ? Tokens.STATUS_OK : Tokens.STATUS_BAD));
        } else {
            out.add(status(t.lastAdhanRow(), t.noAdhanYet(), t.none(), 0));
        }

        int next = day.nextIndex == PrayerDay.SUNRISE ? PrayerDay.DHUHR : day.nextIndex;
        Date nextAt = day.times[next];
        if (nextAt.before(new Date())) nextAt = day.nextTime;
        boolean bell = Prefs.adhan(c) && Prefs.bell(c, next);
        String nextName = next == PrayerDay.DHUHR && day.friday ? t.jumuah() : names[next];
        out.add(status(t.nextAdhanRow(), nextName + " " + time(nextAt, 1f) + t.sep()
            + t.in(Screen.duration(Screen.secondsUntil(nextAt, new Date()), false)), t.adhanOn(bell), bell ? Tokens.STATUS_OK : 0));

        boolean granted = checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
        out.add(status(t.permissionRow(), t.permissionHelp(), t.granted(granted), granted ? Tokens.STATUS_OK : Tokens.STATUS_WARN));

        if ("off".equals(Prefs.weather(c))) {
            out.add(status(t.weatherRow(), t.weatherCredit(), t.off(), 0));
        } else {
            boolean fresh = Weather.now(c) != null;
            CharSequence at = time(new Date(p.getLong("wx_at", 0)), 1f);
            out.add(status(t.weatherRow(), fresh ? t.weatherStatusHelp(at) : t.weatherNone(), t.works(fresh),
                fresh ? Tokens.STATUS_OK : Tokens.STATUS_BAD));
        }

        // The safety net's last record (Crash): when, and the error's name.
        String[] crash = Crash.last(c);
        long crashAt = Crash.lastAt(c);
        if (crash != null && crashAt > 0) {
            String[] lines = crash[1].split("\n");
            String what = lines.length > 2 ? lines[2] : "";
            int colon = what.indexOf(':');
            if (colon > 0) what = what.substring(0, colon);
            what = what.substring(what.lastIndexOf('.') + 1);
            Date at = new Date(crashAt);
            String when = android.text.format.DateUtils.isToday(crashAt) ? t.todayWord() + " " + time(at, 1f)
                : gregorian(at) + " " + time(at, 1f);
            boolean recent = System.currentTimeMillis() - crashAt < 24 * 3600_000L;
            out.add(status(t.lastCrashRow(), t.lastCrashHelp(when, what), "", recent ? Tokens.STATUS_WARN : 0));
        } else {
            out.add(status(t.lastCrashRow(), t.noCrash(), t.none(), 0));
        }

        String device = Settings.Global.getString(getContentResolver(), "device_name");
        if (device == null || device.isEmpty()) device = android.os.Build.MODEL;
        String version = versionName() + t.sep() + "Android " + android.os.Build.VERSION.RELEASE + t.sep() + device;
        out.add(status(t.versionRow(), t.d(version), "", 0));
        return out;
    }

    /** One App status row: read-only, with its state and dot at the end. */
    private Row status(String name, String about, String state, int colour) {
        return new Row(INFO) {
            String label() { return name; }
            String help() { return about; }
            String value() { return state; }
            int dot() { return colour; }
        };
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** "Iqamah today 5:30 AM", or the khutbah on a Friday's Dhuhr. */
    private String iqamaToday(int prayer) {
        Date at = Iqama.at(this, day, prayer);
        if (at != null) return t.iqamaToday(time(at, 1f));
        // The khutbah's own time where it has one.
        Date k = Jumuah.at(this, day);
        return k == null ? t.khutbah() : t.khutbahAt(time(k, 1f));
    }

    /** A time of day, from minutes after midnight, in the screen's clock format. */
    private CharSequence clockOf(int minuteOfDay) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        cal.set(java.util.Calendar.MINUTE, minuteOfDay % 60);
        return time(cal.getTime(), 1f);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** The adhan recording for every prayer but Fajr, or for Fajr: a list that plays each voice as focus reaches it. */
    private Row voiceRow(boolean fajr) {
        return new Row(VALUE) {
            String label() { return fajr ? t.fajrVoiceRow() : t.voiceRow(); }
            String help() { return fajr ? t.fajrVoiceHelp() : t.voiceHelp(); }
            String value() {
                String name = Adhans.name(t, Prefs.voice(SettingsActivity.this, fajr));
                int level = Prefs.fajrLevel(SettingsActivity.this);
                return fajr && level < 100 ? name + t.sep() + t.percent(level) : name;
            }
            void open() {
                String[] names = new String[Adhans.VOICES.length];
                for (int i = 0; i < names.length; i++) names[i] = Adhans.name(t, Adhans.VOICES[i][0]);
                openChooser(label(), names, Adhans.index(Prefs.voice(SettingsActivity.this, fajr)),
                    i -> {
                        Prefs.setVoice(SettingsActivity.this, fajr, Adhans.VOICES[i][0]);
                        showSaved();
                        // Fajr's level next, once this list has closed.
                        if (fajr) root.post(SettingsActivity.this::openFajrLevel);
                    },
                    i -> preview(Adhans.VOICES[i][0]));
            }
        };
    }

    /** Fajr's adhan level, each heard in the chosen voice as focus reaches it; Back keeps it as it was. */
    private void openFajrLevel() {
        int[] levels = Prefs.FAJR_LEVELS;
        String[] names = new String[levels.length];
        int sel = 0;
        for (int i = 0; i < levels.length; i++) {
            names[i] = i == 0 ? t.fullLevel() : t.percent(levels[i]);
            if (levels[i] == Prefs.fajrLevel(this)) sel = i;
        }
        String voice = Prefs.voice(this, true);
        openChooser(t.fajrLevelTitle(), t.fajrLevelHelp(), names, sel, i -> { Prefs.setFajrLevel(this, levels[i]); showSaved(); },
            i -> { preview(voice); Adhans.level(previewPlayer, levels[i]); });
    }

    private void preview(String id) {
        Adhans.stop(previewPlayer);
        previewPlayer = Adhans.play(this, id);
    }

    private void stopPreview() {
        Adhans.stop(previewPlayer);
        previewPlayer = null;
    }

    @Override
    protected void onPause() {
        stopPreview();
        // The method row's word on a new location is for this visit only.
        Prefs.placeReset = false;
        // Never leave the remote waiting for a button (and keys filtered for it) behind another screen.
        if (resetStage == PRESS) closePress();
        super.onPause();
    }

    private String[] digits(String[] s) {
        String[] out = new String[s.length];
        for (int i = 0; i < s.length; i++) out[i] = t.d(s[i]);
        return out;
    }

    /** "TV (its language now)", then each language by its own name (Languages). */
    private String[] languageItems() {
        String[] items = new String[Languages.NAMES.length + 1];
        items[0] = t.languageTv(Languages.name(languageTag("system", tvLanguage())));
        System.arraycopy(Languages.NAMES, 0, items, 1, Languages.NAMES.length);
        return items;
    }

    /** Home's language list (Screen.languageList) as this screen's chooser: Back closes it as any other. */
    private void openLanguages(IntConsumer pick) {
        ListLayer l = languageList(i -> {
            closeChooser();
            root.post(() -> pick.accept(i));
        });
        chooser = l.layer;
        setRowsFocusable(false);
        root.addView(chooser, new FrameLayout.LayoutParams(-1, -1));
        hints.bringToFront();
        if (l.start != null) root.post(l.start::requestFocus);
        List<String> h = new ArrayList<>();
        h.add(t.hintMove());
        h.add(t.hintChoose());
        h.add(t.hintBackCancel());
        setHints(hints, h);
    }

    @Override
    boolean asksKeep() {
        return true;
    }

    @Override
    LinearLayout hintsView() {
        return hints;
    }

    /** Back on the language row (the screen was built again where it was, or the question just closed). */
    @Override
    void languageSettled() {
        if (chooser == null && !rows.isEmpty() && rowIndex < rows.size()) focusRow(rowIndex);
    }

    /** Language or digits changed: every string on the screen changes, so rebuild it where it was. */
    private void relaunch() {
        loadSettings(null);
        build();
    }

    // ---------------------------------------------------------------- rows

    private void addRow(Row r, int i) {
        r.tile = new FocusTile(this, 16);
        r.tile.setFocusScale(Tokens.FOCUS_SCALE_SETTINGS_ROW);
        r.tile.setRest(Tokens.LAPIS_800, 0, 0);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Ui.ipx(28), 0, Ui.ipx(28), 0);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        r.label = text(ui500, 26, Tokens.PLASTER_100);
        r.help = text(ui400, 20, Tokens.PLASTER_100);
        r.help.setSingleLine(true);
        // A line that starts with Latin letters ("Awan TV", a link) still reads from the start side.
        int dir = t.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR;
        r.label.setTextDirection(dir);
        r.help.setTextDirection(dir);
        words.addView(r.label);
        // The row's width, not the text's: a long help line grows smaller to fit it (Screen.fitWidth).
        words.addView(r.help, new LinearLayout.LayoutParams(-1, -2));
        lineHeight(r.label, 1.25f);
        lineHeight(r.help, 1.35f);
        fitWidth(r.help, 0.8f);
        line.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        r.control = new LinearLayout(this);
        r.control.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.setMarginStart(Ui.ipx(24));
        line.addView(r.control, clp);
        r.tile.addView(line, new FrameLayout.LayoutParams(-1, -1));
        r.tile.setLook(f -> render(r, f));
        placeTile(r.tile, 640, ROWS_TOP + i * (ROW_H + ROW_GAP), qrShown() ? ABOUT_ROW_W : Tokens.LAYOUT_SETTINGS_ROW_WIDTH, ROW_H);
    }

    /** Every row and the preview, from the saved settings. */
    private void refresh() {
        day = new PrayerDay(this, new Date());
        for (Row r : rows) render(r, r.tile.isFocused());
        // The custom calculation's note holds today's six times.
        if (calcOpen && pageNote != null) pageNote.setText(calcNote());
        previewValue.setText(time(day.times[PrayerDay.ASR], 1f));
    }

    private void render(Row r, boolean focused) {
        if (day == null) return;
        int ink = focused ? Tokens.LAPIS_900 : Tokens.PLASTER_100;
        r.label.setText(r.label());
        r.label.setTextColor(ink);
        String help = r.help();
        r.help.setText(help);
        r.help.setVisibility(help.isEmpty() ? View.GONE : View.VISIBLE);
        r.help.setTextColor(r.warn() && !focused ? Tokens.GOLD_400 : ink);
        r.help.setAlpha(r.warn() ? 1f : 0.75f);
        r.control.removeAllViews();
        boolean edit = r == editing;
        if (edit) {
            // Editing: a lapis ring around the control on the gold row.
            GradientDrawable ring = round(0, 34);
            ring.setStroke(Ui.ipx(3), Tokens.LAPIS_900);
            r.control.setBackground(ring);
            int pad = Ui.ipx(6);
            r.control.setPadding(pad, pad, pad, pad);
        } else {
            r.control.setBackground(null);
            r.control.setPadding(0, 0, 0, 0);
        }
        switch (r.kind) {
            case VALUE: {
                TextView v = text(ui400, 24, ink);
                v.setText(r.value());
                v.setSingleLine(true);
                r.control.addView(v);
                Chevron ch = new Chevron(this, ink);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.ipx(22), Ui.ipx(22));
                lp.setMarginStart(Ui.ipx(12));
                r.control.addView(ch, lp);
                break;
            }
            case SEG: {
                LinearLayout track = new LinearLayout(this);
                int pad = Ui.ipx(6);
                track.setPadding(pad, pad, pad, pad);
                track.setBackground(round(focused ? Tokens.SEG_TRACK_FOCUSED : Tokens.LAPIS_900, 30));
                String[] segs = r.segs();
                for (int i = 0; i < segs.length; i++) {
                    boolean chosen = i == (edit ? draft : r.selected());
                    TextView s = text(ui400, 22, chosen ? (focused ? Tokens.PLASTER_100 : Tokens.LAPIS_900) : (focused ? Tokens.LAPIS_900 : Tokens.MIST_400));
                    s.setText(segs[i]);
                    s.setGravity(Gravity.CENTER);
                    s.setPadding(Ui.ipx(24), 0, Ui.ipx(24), 0);
                    if (chosen) {
                        GradientDrawable bg = round(focused ? Tokens.LAPIS_900 : Tokens.PLASTER_100, 26);
                        if (focused) bg.setStroke(Ui.ipx(3), Tokens.PLASTER_100);
                        s.setBackground(bg);
                    }
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.ipx(52));
                    if (i > 0) lp.setMarginStart(Ui.ipx(6));
                    track.addView(s, lp);
                }
                r.control.addView(track);
                break;
            }
            case STEP: {
                r.control.addView(stepButton("−"), new LinearLayout.LayoutParams(Ui.ipx(48), Ui.ipx(48)));
                TextView v = text(ui400, 26, ink);
                v.setText(r.valueOf(edit ? draft : r.amount()));
                v.setGravity(Gravity.CENTER);
                v.setMinWidth(Ui.ipx(150));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.setMarginStart(Ui.ipx(18));
                lp.setMarginEnd(Ui.ipx(18));
                r.control.addView(v, lp);
                r.control.addView(stepButton("+"), new LinearLayout.LayoutParams(Ui.ipx(48), Ui.ipx(48)));
                break;
            }
            case INFO: {
                String value = r.value();
                if (value.isEmpty()) break;
                if (r.dot() != 0) {
                    GradientDrawable d = new GradientDrawable();
                    d.setShape(GradientDrawable.OVAL);
                    d.setColor(r.dot());
                    if (focused) d.setStroke(Ui.ipx(2), Tokens.LAPIS_900);
                    View dot = new View(this);
                    dot.setBackground(d);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.ipx(16), Ui.ipx(16));
                    lp.setMarginEnd(Ui.ipx(12));
                    r.control.addView(dot, lp);
                }
                TextView v = text(ui400, 24, ink);
                v.setText(value);
                v.setSingleLine(true);
                r.control.addView(v);
                break;
            }
            default:
                r.control.addView(new Switch(this, r.on(), focused), new LinearLayout.LayoutParams(Ui.ipx(88), Ui.ipx(48)));
        }
    }

    private TextView stepButton(String sign) {
        TextView b = text(ui400, 26, Tokens.PLASTER_100);
        b.setText(sign);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setBackground(round(Tokens.LAPIS_700, 24));
        return b;
    }

    private static GradientDrawable round(int color, float radiusDesignPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(Ui.px(radiusDesignPx));
        return d;
    }

    // ---------------------------------------------------------------- focus and keys

    private void focusNav() {
        onNav = true;
        nav[section].requestFocus();
        List<String> h = new ArrayList<>();
        h.add(t.hintMove());
        h.add(t.hintOpen());
        h.add(t.hintBackHome());
        setHints(hints, h);
    }

    private void focusRow(int i) {
        onNav = false;
        rowIndex = i;
        Row r = rows.get(i);
        r.tile.requestFocus();
        List<String> h = new ArrayList<>();
        h.add(t.hintMove());
        if (r.kind != INFO) h.add(r.kind == VALUE ? t.hintChoose() : r.kind == TOGGLE ? t.hintSwitch() : t.hintEdit());
        h.add(statusOpen || creditsOpen ? t.hintBackAbout() : buttonsOpen ? t.hintBackDevice()
            : adjustOpen || calcOpen ? t.hintBackPrayer() : t.hintBackMenu());
        setHints(hints, h);
        styleNav(section);
    }

    /** OK on a SEG or STEP row: ◀▶ now move an unsaved choice, OK saves it, Back cancels. */
    private void startEditing(Row r) {
        editing = r;
        draft = r.kind == SEG ? r.selected() : r.amount();
        render(r, true);
        List<String> h = new ArrayList<>();
        h.add(t.hintChange());
        h.add(t.hintSave());
        h.add(t.hintBackCancel());
        setHints(hints, h);
    }

    /** Leaves editing; save keeps the draft if it differs from what is saved. */
    private void stopEditing(boolean save) {
        Row r = editing;
        editing = null;
        if (save && draft != (r.kind == SEG ? r.selected() : r.amount())) {
            if (r.kind == SEG) r.select(draft);
            else r.setAmount(draft);
            if (r.saves()) showSaved();
        }
        // A language or digits change rebuilt the screen and its rows already.
        if (rows.contains(r)) {
            refresh();
            focusRow(rowIndex);
        }
    }

    /** ✓ Saved fades in at the end of the hint line, then out; a new save restarts it. */
    private void showSaved() {
        saved.removeCallbacks(hideSaved);
        saved.animate().alpha(1).setDuration(Tokens.MOTION_OVERLAY_FADE_MS);
        saved.postDelayed(hideSaved, Tokens.MOTION_SAVED_LIFETIME_MS);
    }

    private void navTo(int i) {
        if (i < 0 || i >= SECTIONS || i == section) return;
        section = i;
        showSection();
        focusNav();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (keepKey(e)) return true;
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (resetStage != 0) return resetKey(e);
        if (chooser != null) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (!down) {
                    Runnable back = chooserBack;
                    closeChooser();
                    if (back != null) back.run();
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            return super.dispatchKeyEvent(e);
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                if (editing != null) stopEditing(false);
                // Leaving the first run with no place set asks first: the times would follow a guessed city.
                else if (onNav) {
                    if (firstRunUnset()) openLocationAsk();
                    else finish();
                }
                else if (creditsOpen) {
                    // The sources back to About, on their row.
                    creditsOpen = false;
                    showSection();
                    focusRow(CREDITS_ROW);
                }
                else if (statusOpen) {
                    // App status back to About, on its row.
                    statusOpen = false;
                    showSection();
                    focusRow(0);
                }
                else if (adjustOpen) {
                    // The adjustments back to Prayer times, on their row.
                    adjustOpen = false;
                    showSection();
                    focusRow(ADJUST_ROW + (habousShown ? 1 : 0));
                }
                else if (calcOpen) {
                    // The custom calculation back to Prayer times, on the method's row.
                    calcOpen = false;
                    showSection();
                    focusRow(METHOD_ROW);
                }
                else if (buttonsOpen) {
                    // The remote buttons back to Device settings, on their row (the last).
                    buttonsOpen = false;
                    showSection();
                    focusRow(rows.size() - 1);
                }
                else focusNav();
            }
            return true;
        }
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean horizontal = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean vertical = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN;
        if (!ok && !horizontal && !vertical) return super.dispatchKeyEvent(e);
        if (!down || (ok && e.getRepeatCount() > 0)) return true;
        // "Toward the end" is Right in English and Left in Arabic; choices and steppers run the same way.
        boolean toEnd = (k == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl;
        int up = k == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;

        if (onNav) {
            if (vertical) navTo(section + up);
            // OK on Network opens the TV's Wi-Fi settings, unless the section has more than that row.
            else if (ok && section == NETWORK && rows.size() <= 1) startSafely(new Intent(Settings.ACTION_WIFI_SETTINGS));
            // OK on Device settings opens the TV's own, unless the section has more than that row.
            else if (ok && section == DEVICE && rows.size() <= 1) startSafely(new Intent(Settings.ACTION_SETTINGS));
            else if ((ok || toEnd) && !rows.isEmpty()) focusRow(0);
            return true;
        }

        if (editing != null) {
            if (ok) stopEditing(true);
            else if (horizontal) {
                Row r = editing;
                int lo = r.kind == SEG ? 0 : r.lowest();
                int hi = r.kind == SEG ? r.segs().length - 1 : r.highest();
                int by = r.kind == SEG ? 1 : r.step();
                int to = Math.max(lo, Math.min(hi, draft + (toEnd ? by : -by)));
                if (to != draft) {
                    draft = to;
                    render(r, true);
                }
            }
            // Up/Down stay on the row being edited.
            return true;
        }

        Row r = rows.get(rowIndex);
        if (vertical) {
            int to = rowIndex + up;
            if (to >= 0 && to < rows.size()) focusRow(to);
            return true;
        }
        if (horizontal) {
            if (!toEnd) focusNav();
            return true;
        }
        switch (r.kind) {
            case SEG:
            case STEP:
                startEditing(r);
                return true;
            case TOGGLE:
                r.set(!r.on());
                if (r.saves()) showSaved();
                refresh();
                return true;
            default:
                r.open();
                return true;
        }
    }

    // ---------------------------------------------------------------- list chooser

    /** Location's second step: one region's countries, A to Z in the app's language ("ال" not counted). Back runs back. */
    private void openCountries(int region, Runnable back) {
        List<String> countries = new ArrayList<>();
        for (String en : Prefs.REGIONS[region]) {
            for (Prefs.City city : Prefs.CITIES) {
                if (city.countryEn.equals(en)) { countries.add(Prefs.countryOf(city, t)); break; }
            }
        }
        java.text.Collator order = Apps.collator(t);
        Collections.sort(countries, (a, b) -> order.compare(a.replaceFirst("^ال", ""), b.replaceFirst("^ال", "")));
        Prefs.City now = Prefs.city(this);
        int sel = countries.indexOf(Prefs.countryOf(now, t));
        Runnable self = () -> openCountries(region, back);
        openChooser(t.regions()[region], countries.toArray(new String[0]), sel,
            i -> root.post(() -> openCities(countries.get(i), self)));
        chooserBack = back;
    }

    /** Location's third step: one country's cities. A country with a single city takes it at once. Back runs back. */
    private void openCities(String country, Runnable back) {
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < Prefs.CITIES.length; i++) {
            Prefs.City city = Prefs.CITIES[i];
            if (Prefs.countryOf(city, t).equals(country)) found.add(i);
        }
        if (found.size() == 1) {
            Prefs.setCity(this, found.get(0));
            refresh();
            focusRow(rowIndex);
            showSaved();
            return;
        }
        String[] names = new String[found.size()];
        int current = Prefs.cityIndex(this);
        // -1 when the saved city is in another country: nothing is marked, focus starts on the first city.
        int sel = -1;
        for (int k = 0; k < names.length; k++) {
            Prefs.City city = Prefs.CITIES[found.get(k)];
            names[k] = t.ar ? city.ar : city.en;
            if (found.get(k) == current) sel = k;
        }
        openChooser(country, names, sel, k -> {
            Prefs.setCity(this, found.get(k));
            showSaved();
            firstRunDone();
        });
        chooserBack = back;
    }

    /** Station's second step for the recordings saved on the TV. */
    private void openSaved(Runnable back) {
        List<String> keys = Library.savedKeys(this);
        String[] names = new String[keys.size()];
        String now = Radio.station(this).savedKey();
        int sel = -1;
        for (int k = 0; k < names.length; k++) {
            names[k] = t.d(Library.name(this, keys.get(k), t));
            if (keys.get(k).equals(now)) sel = k;
        }
        openChooser(t.stationGroups()[2], names, sel, k -> { Radio.switchToSaved(this, keys.get(k)); showSaved(); });
        chooserBack = back;
    }

    /** Station's second step: the broadcast stations or the reciters. Playing radio moves to the pick. Back runs back. */
    private void openStations(boolean reciters, Runnable back) {
        List<Radio.Station> found = new ArrayList<>();
        for (Radio.Station s : Radio.STATIONS) if (s.reciter == reciters) found.add(s);
        // After Afnon's ten reciters, every other reciter channel of mp3quran.net (Radios).
        if (reciters) found.addAll(Radios.extra(this));
        String[] names = new String[found.size()];
        String current = Radio.station(this).id;
        // -1 when the station is in another group: nothing is marked, focus starts on the first one.
        int sel = -1;
        for (int k = 0; k < names.length; k++) {
            names[k] = t.stationChoice(found.get(k));
            if (found.get(k).id.equals(current)) sel = k;
        }
        openChooser(t.stationGroups()[reciters ? 1 : 0], names, sel, k -> { Radio.switchToId(this, found.get(k).id); showSaved(); });
        chooserBack = back;
    }

    /** A list over the screen (cities, methods): OK picks, Back cancels. Up/Down are the system's own. */
    private void openChooser(String title, String[] items, int selected, IntConsumer pick) {
        openChooser(title, items, selected, pick, null);
    }

    /** onFocus, if given, hears each item as focus reaches it (the adhan list plays that voice). */
    private void openChooser(String title, String[] items, int selected, IntConsumer pick, IntConsumer onFocus) {
        openChooser(title, null, items, selected, pick, onFocus);
    }

    /** Sub, if given, is a line under the title (Fajr's level). */
    private void openChooser(String title, String sub, String[] items, int selected, IntConsumer pick, IntConsumer onFocus) {
        chooser = new FrameLayout(this);
        chooser.setBackgroundColor(Tokens.SCRIM_MENU);
        chooser.setClickable(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.ipx(48), Ui.ipx(40), Ui.ipx(48), Ui.ipx(32));
        GradientDrawable bg = round(Tokens.LAPIS_800, 24);
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
        card.setBackground(bg);
        card.setClipChildren(true);

        TextView head = text(displayBold, Tokens.FONT_SIZE_DISPLAY_S, Tokens.GOLD_400);
        head.setText(title);
        card.addView(head);
        lineHeight(head, 1.2f);
        if (sub != null) {
            TextView line = text(ui400, 20, Tokens.MIST_400);
            line.setText(sub);
            card.addView(line);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        // No fading edges: focus then scrolls by whole items, so none is ever cut at the top or bottom.
        scroll.setVerticalFadingEdgeEnabled(false);
        int reach = Ui.ipx(FocusTile.REACH);
        // Padding at the sides only: ScrollView's focus scrolling ignores its own padding, so a focused item could
        // stop under a bottom padding with its focus ring cut (as Afnon 0.8). Each item holds its ring inside.
        scroll.setPadding(reach, 0, reach, 0);
        scroll.setClipToPadding(true);
        scroll.setClipChildren(true);
        int itemH = Ui.ipx(68 + 2 * FocusTile.REACH), gap = Ui.ipx(6 - 2 * FocusTile.REACH);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        FocusTile first = null;
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            boolean chosen = i == selected;
            FocusTile item = new FocusTile(this, 14);
            item.setFocusScale(1f);   // the list scrolls, so a grown tile would be clipped at its edges
            item.setRest(chosen ? Tokens.LAPIS_700 : 0, 0, 0);
            TextView v = text(chosen ? ui500 : ui400, 26, Tokens.PLASTER_100);
            v.setText(items[i]);
            v.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            v.setPadding(Ui.ipx(24), 0, Ui.ipx(24), 0);
            item.addView(v, new FrameLayout.LayoutParams(-1, -1));
            item.setLook(f -> {
                v.setTextColor(f ? Tokens.LAPIS_900 : (chosen ? Tokens.GOLD_400 : Tokens.PLASTER_100));
                if (f && onFocus != null) onFocus.accept(index);
            });
            item.setOnClickListener(x -> {
                pick.accept(index);
                closeChooser();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, itemH);
            if (i > 0) lp.topMargin = gap;
            list.addView(item, lp);
            if (chosen || first == null) first = item;
        }
        scroll.addView(list);
        // Up to eight whole items, rings included, in the same screen px as the items (no rounding gap).
        int shown = Math.max(1, Math.min(items.length, 8));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, shown * itemH + (shown - 1) * gap);
        slp.topMargin = Ui.ipx(20);
        slp.setMarginStart(-reach);
        slp.setMarginEnd(-reach);
        card.addView(scroll, slp);

        chooser.addView(card, new FrameLayout.LayoutParams(Ui.ipx(900), -2, Gravity.CENTER));
        setRowsFocusable(false);
        root.addView(chooser, new FrameLayout.LayoutParams(-1, -1));
        // The key hints over the dimmed screen, not under it: they are the chooser's own ("Back  Later" on the first run).
        hints.bringToFront();
        FocusTile start = first;
        if (start != null) root.post(start::requestFocus);
        List<String> h = new ArrayList<>();
        h.add(t.hintMove());
        h.add(t.hintChoose());
        h.add(t.hintBackCancel());
        setHints(hints, h);
    }

    private void closeChooser() {
        stopPreview();
        root.removeView(chooser);
        chooser = null;
        chooserBack = null;
        setRowsFocusable(true);
        refresh();
        focusRow(rowIndex);
    }

    private void setRowsFocusable(boolean on) {
        for (FocusTile n : nav) n.setFocusable(on);
        for (Row r : rows) r.tile.setFocusable(on);
    }

    // ---------------------------------------------------------------- About > Sources and licences

    /** About's row of the sources (its place in the list), for Back from their page. */
    private static final int CREDITS_ROW = 6;

    /** Where Awan TV comes from and what it carries: Afnon, the weather, the cities, Morocco's tables, the recitations, the adhan. */
    private List<Row> creditRows() {
        Context c = this;
        List<Row> out = new ArrayList<>(Arrays.asList(
            new Row(INFO) {
                String label() { return t.basedOn(); }
                String help() { return t.mitLicence(); }
            },
            new Row(INFO) {
                String label() { return t.weatherRow(); }
                String help() { return t.weatherCredit(); }
            },
            new Row(INFO) {
                String label() { return t.location(); }
                String help() { return t.citiesCreditLong(); }
            },
            new Row(INFO) {
                String label() { return t.habousCreditRow(); }
                String help() { return t.habousCredit(); }
            },
            new Row(INFO) {
                String label() { return t.libraryRow(); }
                String help() { return t.recitationsCreditLong(); }
            }));
        // The adhan recordings' source, when this build carries them (tools/AwanTV-fetch-adhans before building).
        if (Adhans.present(c)) out.add(new Row(INFO) {
            String label() { return t.adhanRow(); }
            String help() { return t.adhanCredit(); }
        });
        return out;
    }

    // ---------------------------------------------------------------- adjust the times and the date

    /** Prayer times' row of the adjustments (its place in the list, one more with the Ministry table), for Back from their page. */
    private static final int ADJUST_ROW = 3;

    /**
     * Prayer times > Adjust the times and the date (Afnon 0.8's Adjust the times, as in meypod/al-azan): each of the
     * six times up to 30 minutes earlier or later, with what it is today under its name, then the Hijri date.
     */
    private List<Row> adjustRows() {
        Context c = this;
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final int prayer = i;
            out.add(new Row(STEP) {
                String label() { return t.prayerNames()[prayer]; }
                String help() {
                    // After Isha, PrayerDay holds tomorrow's Fajr.
                    boolean tomorrow = prayer == PrayerDay.FAJR && day.times[prayer].after(day.times[PrayerDay.ISHA]);
                    return t.adjustTimeHelp(time(day.times[prayer], 1f).toString(), tomorrow);
                }
                int amount() { return Prefs.adjustments(c)[prayer]; }
                void setAmount(int n) {
                    Prefs.setAdjustment(c, prayer, n);
                    // The reminders, the call and the iqamah countdown follow at once.
                    if (RemoteKeys.watch != null) RemoteKeys.watch.refreshCounter();
                }
                int lowest() { return -30; }
                int highest() { return 30; }
                String valueOf(int n) { return t.minutesSigned(n); }
            });
        }
        out.add(new Row(STEP) {
            String label() { return t.hijriAdjust(); }
            String help() { return t.hijriToday(hijri(day)); }
            int amount() { return Prefs.hijriOffset(c); }
            void setAmount(int n) { Prefs.setHijriOffset(c, n); }
            int lowest() { return -2; }
            int highest() { return 2; }
            String valueOf(int n) { return t.days(n); }
        });
        return out;
    }

    // ---------------------------------------------------------------- custom calculation (1.24.0)

    /** Prayer times' row of the method (Back from the custom calculation's page lands on it). */
    private static final int METHOD_ROW = 1;

    /**
     * Prayer times > Calculation method > Custom calculation (Prefs.Calc; Muslim Pro's Fixed calculation, Pillars'
     * Custom angles, PrayWatch's Custom): on or off, the method it starts from, Fajr's angle, Isha by an angle or by
     * minutes after Maghrib, the rule above 48° (HighLatitude), and whether a new location keeps it. Off, the switch
     * alone. Each time under its row as it comes out today; the six of them in the note.
     */
    private List<Row> calcRows() {
        Context c = this;
        List<Row> out = new ArrayList<>();
        out.add(new Row(TOGGLE) {
            String label() { return t.calcRow(); }
            String help() { return t.calcRowHelp(); }
            boolean on() { return Prefs.calcOn(c); }
            void set(boolean on) {
                Prefs.setCalcOn(c, on);
                // Its rows come or go.
                root.post(() -> { showSection(); focusRow(0); });
            }
        });
        if (!Prefs.calcOn(c)) return out;
        out.add(new Row(VALUE) {
            String label() { return t.calcBaseRow(); }
            String help() { return t.calcBaseHelp(); }
            String value() { return t.methodName(Prefs.calcSaved(c).base); }
            void open() {
                Method[] bases = Prefs.calcBases();
                String[] names = new String[bases.length];
                int sel = 0;
                for (int i = 0; i < bases.length; i++) {
                    names[i] = t.methodName(bases[i]);
                    if (bases[i] == Prefs.calcSaved(c).base) sel = i;
                }
                openChooser(t.calcBaseRow(), names, sel, i -> { Prefs.setCalcBase(c, bases[i]); showSaved(); });
            }
        });
        out.add(new Row(STEP) {
            String label() { return t.calcFajrRow(); }
            String help() {
                boolean tomorrow = day.times[PrayerDay.FAJR].after(day.times[PrayerDay.ISHA]);
                return t.calcAngleHelp(time(day.times[PrayerDay.FAJR], 1f).toString(), tomorrow);
            }
            int amount() { return Prefs.calcSaved(c).fajrTenths; }
            void setAmount(int n) { Prefs.setCalcFajr(c, n); calcChanged(); }
            int step() { return 5; }
            int lowest() { return Prefs.CALC_ANGLE_MIN; }
            int highest() { return Prefs.CALC_ANGLE_MAX; }
            String valueOf(int n) { return t.degrees(n); }
        });
        out.add(new Row(SEG) {
            String label() { return t.calcIshaRow(); }
            String help() { return t.calcIshaRowHelp(); }
            String[] segs() { return t.calcIshaModes(); }
            int selected() { return Prefs.calcSaved(c).ishaAfterMaghrib ? 1 : 0; }
            void select(int i) { Prefs.setCalcIshaAfterMaghrib(c, i == 1); calcChanged(); }
        });
        out.add(new Row(STEP) {
            boolean minutes() { return Prefs.calcSaved(c).ishaAfterMaghrib; }
            String label() { return minutes() ? t.calcIshaMinutesRow() : t.calcIshaAngleRow(); }
            String help() {
                String isha = time(day.times[PrayerDay.ISHA], 1f).toString();
                return minutes() ? t.calcMinutesHelp(isha, Prefs.calcSaved(c).base == Method.UMM_AL_QURA)
                                 : t.calcAngleHelp(isha, false);
            }
            int amount() { Prefs.Calc k = Prefs.calcSaved(c); return minutes() ? k.ishaMinutes : k.ishaTenths; }
            void setAmount(int n) {
                if (minutes()) Prefs.setCalcIshaMinutes(c, n);
                else Prefs.setCalcIshaAngle(c, n);
                calcChanged();
            }
            int step() { return 5; }
            int lowest() { return minutes() ? Prefs.CALC_MINUTES_MIN : Prefs.CALC_ANGLE_MIN; }
            int highest() { return minutes() ? Prefs.CALC_MINUTES_MAX : Prefs.CALC_ANGLE_MAX; }
            String valueOf(int n) { return minutes() ? t.calcMinutes(n) : t.degrees(n); }
        });
        out.add(new Row(VALUE) {
            int index() { return java.util.Arrays.asList(Prefs.HIGH_RULES).indexOf(Prefs.calcSaved(c).high); }
            String label() { return t.calcHighRow(); }
            String help() {
                if (Math.abs(Prefs.where(c).latitude) <= HighLatitude.FROM) return t.calcHighHere();
                if (Prefs.calcSaved(c).base == Method.MOON_SIGHTING_COMMITTEE) return t.calcHighMsc();
                return t.calcHighAbout()[index()];
            }
            String value() { return t.calcHighNames()[index()]; }
            void open() {
                openChooser(t.calcHighRow(), t.calcHighNames(), index(), i -> {
                    Prefs.setCalcHigh(c, Prefs.HIGH_RULES[i]);
                    calcChanged();
                    showSaved();
                });
            }
        });
        out.add(new Row(TOGGLE) {
            String label() { return t.calcKeepRow(); }
            String help() { return t.calcKeepHelp(); }
            boolean on() { return Prefs.calcSaved(c).keep; }
            void set(boolean on) { Prefs.setCalcKeep(c, on); }
        });
        return out;
    }

    /** The custom calculation's note: today's six times as they come out, or what turning it on starts from. */
    private CharSequence calcNote() {
        if (!Prefs.calcOn(this)) {
            Method m = Prefs.calcSaved(this).base;
            return t.calcOffNote(t.methodName(m));
        }
        String[] names = t.prayerNames();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 6; i++) parts.add(names[i] + " " + time(day.times[i], 1f));
        return t.calcTimesNote(parts);
    }

    /** A changed angle, mode or rule: the call and the iqamah countdown follow at once (refresh redraws the note). */
    private void calcChanged() {
        if (RemoteKeys.watch != null) RemoteKeys.watch.refreshCounter();
    }

    /** A note under a sub-page's rows, lined up with their words. */
    private TextView note(CharSequence s) {
        TextView v = text(ui400, 20, Tokens.MIST_400);
        v.setTextDirection(t.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        v.setText(s);
        v.setLineSpacing(0, 1.3f);
        place(v, 640 + 28, ROWS_TOP + rows.size() * (ROW_H + ROW_GAP) + 14, Tokens.LAYOUT_SETTINGS_ROW_WIDTH - 56,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        return v;
    }

    // ---------------------------------------------------------------- remote buttons

    /** Device settings > Remote buttons: one row per custom button, then Add a button (until MAX_BUTTONS). */
    private List<Row> buttonsRows() {
        Context c = this;
        List<Row> out = new ArrayList<>();
        for (Buttons.Entry b : Buttons.all(c)) {
            out.add(new Row(VALUE) {
                String label() { return b.name; }
                String help() {
                    String from = t.buttonFrom(b.from);
                    if (Buttons.SHOW_CODES) {
                        String id = b.id.startsWith("s") ? "scan " + b.id.substring(1) : android.view.KeyEvent.keyCodeToString(Integer.parseInt(b.id));
                        from = from.isEmpty() ? id : from + t.sep() + id;
                    }
                    if (b.longAction == null) return from;
                    String held = t.buttonLongShort(Buttons.label(c, t, b.longAction));
                    return from.isEmpty() ? held : from + t.sep() + held;
                }
                String value() { return Buttons.label(c, t, b.action); }
                void open() { openButtonKind(b, null); }
            });
        }
        if (out.size() < MAX_BUTTONS) {
            out.add(new Row(VALUE) {
                String label() { return t.addButtonRow(); }
                String help() { return t.addButtonHelp(); }
                void open() {
                    // The first time only: what customising costs, before anything is taken. Not on a TCL TV,
                    // whose own remapping answers the buttons at no cost.
                    if (TclKeys.available(c) || Prefs.of(c).getBoolean(Buttons.WARNED, false)) openPress();
                    else openAsk(t.warnTitle(), new String[] {t.warnBody(), t.warnVolume(), t.warnUndo()},
                        t.resetContinue(), t.cancel(), () -> {
                            Prefs.of(c).edit().putBoolean(Buttons.WARNED, true).apply();
                            openPress();
                        });
                }
            });
        }
        return out;
    }

    /** Under the rows: what customising costs and which keys never are; the service's state when it is off. */
    private TextView buttonsNote() {
        android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder();
        if (RemoteKeys.guard == null) {
            s.append(t.buttonsServiceOff());
            s.setSpan(new android.text.style.ForegroundColorSpan(Tokens.STATUS_BAD), 0, s.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.append("\n");
        }
        int head = s.length();
        s.append(t.buttonsInfoRow());
        s.setSpan(new android.text.style.ForegroundColorSpan(Tokens.PLASTER_100), head, s.length(),
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append("\n").append(TclKeys.available(this) ? t.buttonsInfoHelpTcl() : t.buttonsInfoHelp());
        return note(s);
    }

    /**
     * What a button does: what it always did (no longer customised), nothing, one of Awan TV's screens, or an app
     * (Home's order). heard is the press that brought it here (test builds show its codes), null from the list.
     */
    private void openButtonChooser(Buttons.Entry b, android.view.KeyEvent heard) {
        openButtonChooser(b, heard, null);
    }

    /** back, if given, is where Back goes (the press-or-hold list of a button already customised). */
    private void openButtonChooser(Buttons.Entry b, android.view.KeyEvent heard, Runnable back) {
        Context c = this;
        List<String> actions = new ArrayList<>();
        List<String> names = new ArrayList<>();
        actions.add(null);
        names.add(t.buttonOriginal());
        actions.add(Buttons.NONE);
        names.add(t.buttonNone());
        for (String a : new String[] {Buttons.SAVER, Buttons.TODAY, Buttons.RADIO}) {
            actions.add(a);
            names.add(Buttons.label(c, t, a));
        }
        for (String pkg : Apps.sorted(c, t)) {
            actions.add(Buttons.app(pkg));
            names.add(Apps.name(c, t, pkg));
        }
        // A new button starts on the first of Awan TV's screens; one already set, on its choice.
        int sel = b.action == null ? -1 : actions.indexOf(b.action);
        String sub = buttonSub(b, heard);
        openChooser(t.buttonOpens(b.name), sub.isEmpty() ? null : sub, names.toArray(new String[0]), sel, i -> {
            String action = actions.get(i);
            if (action == null && b.action == null) return;   // a new button left as it was: nothing to save
            Buttons.save(c, b, action);
            RemoteKeys.buttonsChanged();
            showSaved();
            root.post(() -> {
                showSection();
                int at = 0;
                List<Buttons.Entry> now = Buttons.all(c);
                for (int k = 0; k < now.size(); k++) if (now.get(k).id.equals(b.id)) at = k;
                if (action == null) at = Math.max(0, rows.size() - 1);
                focusRow(Math.min(at, rows.size() - 1));
            });
        }, null);
        chooserBack = back;
        // A new button's list opens on Awan TV's first screen, not on "what it always did".
        if (sel < 0) focusChooserItem(2);
    }

    /** The subtitle of a button's lists: its remote, and the codes heard (test builds only). */
    private String buttonSub(Buttons.Entry b, android.view.KeyEvent heard) {
        String sub = t.buttonFrom(b.from);
        if (Buttons.SHOW_CODES && heard != null) {
            String codes = Buttons.codes(heard.getKeyCode(), heard.getScanCode());
            sub = sub.isEmpty() ? codes : sub + t.sep() + codes;
        }
        return sub;
    }

    /**
     * A button already customised: its press or its hold. A hold is only for a button whose press is customised
     * (with the press taken, what the button did before cannot be given back on a short press).
     */
    private void openButtonKind(Buttons.Entry b, android.view.KeyEvent heard) {
        Context c = this;
        String hold = b.longAction == null ? t.buttonLongNone() : Buttons.label(c, t, b.longAction);
        String[] items = {t.buttonPressIs(Buttons.label(c, t, b.action)), t.buttonLongIs(hold)};
        String sub = buttonSub(b, heard);
        sub = sub.isEmpty() ? t.buttonLongNote() : sub + t.sep() + t.buttonLongNote();
        Runnable again = () -> root.post(() -> openButtonKind(b, heard));
        // The next list opens once this chooser has closed (closeChooser runs after the pick).
        openChooser(b.name, sub, items, -1, i -> root.post(() -> {
            if (i == 0) openButtonChooser(b, heard, again);
            else openLongChooser(b, again);
        }), null);
    }

    /** What holding the button does: no hold (a press), nothing, one of Awan TV's screens, or an app. */
    private void openLongChooser(Buttons.Entry b, Runnable back) {
        Context c = this;
        List<String> actions = new ArrayList<>();
        List<String> names = new ArrayList<>();
        actions.add(null);
        names.add(t.buttonLongNone());
        actions.add(Buttons.NONE);
        names.add(t.buttonNone());
        for (String a : new String[] {Buttons.SAVER, Buttons.TODAY, Buttons.RADIO}) {
            actions.add(a);
            names.add(Buttons.label(c, t, a));
        }
        for (String pkg : Apps.sorted(c, t)) {
            actions.add(Buttons.app(pkg));
            names.add(Apps.name(c, t, pkg));
        }
        int sel = actions.indexOf(b.longAction);
        openChooser(t.buttonLongTitle(b.name), t.buttonLongNote(), names.toArray(new String[0]), sel, i -> {
            String action = actions.get(i);
            if (java.util.Objects.equals(action, b.longAction)) return;
            b.longAction = action;
            Buttons.save(c, b, b.action);
            RemoteKeys.buttonsChanged();
            showSaved();
            root.post(() -> {
                showSection();
                List<Buttons.Entry> now = Buttons.all(c);
                for (int k = 0; k < now.size(); k++) if (now.get(k).id.equals(b.id)) focusRow(k);
            });
        }, null);
        chooserBack = back;
    }

    /** Focus on one item of the open chooser (its list is the card's last child, in a ScrollView). */
    private void focusChooserItem(int index) {
        if (chooser == null) return;
        LinearLayout card = (LinearLayout) chooser.getChildAt(0);
        ScrollView scroll = (ScrollView) card.getChildAt(card.getChildCount() - 1);
        LinearLayout list = (LinearLayout) scroll.getChildAt(0);
        if (index < list.getChildCount()) {
            View item = list.getChildAt(index);
            root.post(item::requestFocus);
        }
    }

    // A wait for a button (PRESS) and a two-button question (ASK) share the reset dialog's frame.
    private static final int PRESS = 4, ASK = 5;
    private static final int PRESS_SECONDS = 10;
    private Ring pressRing;
    private TextView pressWaiting;
    private android.animation.ValueAnimator pressTimer;
    private FocusTile askFirst, askSecond;
    private Runnable askOnFirst;

    /**
     * "Press the button": the next key that can be customised, from the service (which filters keys meanwhile)
     * or, with the service off, from what reaches this screen. Ten seconds, then "No button came through".
     */
    private void openPress() {
        LinearLayout card = resetCard(900);
        card.addView(resetTitle(t.pressTitle(), Gravity.START));
        TextView help = resetLine(t.pressHelp(), Tokens.PLASTER_100, 24, null);
        card.addView(help);
        LinearLayout wait = new LinearLayout(this);
        wait.setGravity(Gravity.CENTER_VERTICAL);
        pressRing = new Ring(this, ui500);
        pressRing.textScale = 0.42f;
        wait.addView(pressRing, new LinearLayout.LayoutParams(Ui.ipx(96), Ui.ipx(96)));
        pressWaiting = text(ui400, 24, Tokens.MIST_400);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
        wlp.setMarginStart(Ui.ipx(24));
        wait.addView(pressWaiting, wlp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.ipx(28);
        card.addView(wait, lp);
        showReset(card, 900, PRESS);
        showSecondsLeft(1f);
        pressTimer = android.animation.ValueAnimator.ofFloat(1f, 0f).setDuration(PRESS_SECONDS * 1000L);
        pressTimer.setInterpolator(null);
        pressTimer.addUpdateListener(a -> showSecondsLeft((Float) a.getAnimatedValue()));
        pressTimer.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }

            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (cancelled) return;
                closePress();
                openAsk(t.noneTitle(), new String[] {t.noneBody(), t.noneWhy()}, t.tryAgain(), t.close(),
                    SettingsActivity.this::openPress);
            }
        });
        pressTimer.start();
        RemoteKeys.startCapture(this::heardButton);
        List<String> h = new ArrayList<>();
        h.add(t.hintBackCancel());
        setHints(hints, h);
    }

    private void showSecondsLeft(float left) {
        if (pressRing == null) return;
        int seconds = Math.max(1, (int) Math.ceil(left * PRESS_SECONDS - 0.001));
        pressRing.label = t.d(String.valueOf(seconds));
        pressRing.set(left);
        pressWaiting.setText(t.pressWaiting(seconds));
    }

    /** Keys while waiting: Back cancels; a key that can be customised and reached this screen is the button. */
    private boolean pressKey(android.view.KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == android.view.KeyEvent.ACTION_DOWN;
        if (k == android.view.KeyEvent.KEYCODE_BACK) {
            if (!down) closePress();
            return true;
        }
        if (down && e.getRepeatCount() == 0 && !Buttons.excluded(k) && Buttons.id(e) != null) heardButton(e);
        return true;
    }

    /** The button pressed: its list, as saved if it is customised already. */
    private void heardButton(android.view.KeyEvent e) {
        if (resetStage != PRESS || isFinishing()) return;
        closePress();
        String id = Buttons.id(e);
        Buttons.Entry b = Buttons.find(this, id);
        if (b == null) openButtonChooser(new Buttons.Entry(id, Buttons.name(t, e.getKeyCode(), e.getScanCode()), Buttons.remote(e), null), e);
        else openButtonKind(b, e);
    }

    private void stopPress() {
        if (pressTimer != null) pressTimer.cancel();
        pressTimer = null;
        pressRing = null;
        RemoteKeys.stopCapture();
    }

    private void closePress() {
        stopPress();
        closeReset();
    }

    /** A question with two buttons, the first focused: OK on it closes the question and runs onFirst; the second or Back only closes. */
    private void openAsk(String title, String[] lines, String first, String second, Runnable onFirst) {
        LinearLayout card = resetCard(900);
        card.addView(resetTitle(title, Gravity.START));
        for (int i = 0; i < lines.length; i++) {
            TextView v = resetLine(lines[i], i == 0 ? Tokens.PLASTER_100 : Tokens.MIST_400, 24, null);
            v.setLineSpacing(0, 1.3f);
            if (i > 0) ((LinearLayout.LayoutParams) v.getLayoutParams()).topMargin = Ui.ipx(10);
            card.addView(v);
        }
        LinearLayout buttons = new LinearLayout(this);
        float r = FocusTile.REACH;
        askFirst = resetButton(first);
        askSecond = resetButton(second);
        askOnFirst = onFirst;
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
        b.setMarginStart(Ui.ipx(24));
        buttons.addView(askFirst, a);
        buttons.addView(askSecond, b);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.ipx(30 - r);
        blp.setMarginStart(-Ui.ipx(r));
        blp.setMarginEnd(-Ui.ipx(r));
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        card.setClipToPadding(false);
        card.addView(buttons, blp);
        showReset(card, 900, ASK);
        root.post(askFirst::requestFocus);
        List<String> h = new ArrayList<>();
        h.add(t.hintChoose());
        h.add(t.hintBackCancel());
        setHints(hints, h);
    }

    // ---------------------------------------------------------------- reset

    /** 0: no reset dialog; 1: the first confirmation; 2: hold OK. */
    private int resetStage;
    private FrameLayout resetView;
    /** The first confirmation's buttons; focus starts on Cancel. */
    private FocusTile resetCancel, resetGo;
    private Ring resetRing;
    private android.animation.ValueAnimator resetHold;
    /** A fresh OK press seen in this dialog: the release of the press that opened it is not an answer. */
    private boolean resetPressed;
    private static final long RESET_HOLD_MS = 3000;

    /** The first confirmation: what goes back to a new install's and what stays; Cancel focused. */
    private void openResetAsk() {
        LinearLayout card = resetCard(820);
        card.addView(resetTitle(t.resetTitle(), Gravity.START));
        card.addView(resetLine(t.resetGoesHead(), Tokens.MIST_400, 22, null));
        for (String x : t.resetGoes()) card.addView(resetMarked(x, "•  ", Tokens.STATUS_BAD));
        TextView stays = resetLine(t.resetStaysHead(), Tokens.MIST_400, 22, null);
        ((LinearLayout.LayoutParams) stays.getLayoutParams()).topMargin = Ui.ipx(16);
        card.addView(stays);
        for (String x : t.resetStays()) card.addView(resetMarked(x, "✓  ", Tokens.STATUS_OK));

        LinearLayout buttons = new LinearLayout(this);
        float r = FocusTile.REACH;
        resetCancel = resetButton(t.cancel());
        resetGo = resetButton(t.resetContinue());
        resetCancel.setOnClickListener(v -> closeReset());
        resetGo.setOnClickListener(v -> openResetHold());
        // The tiles reach past their buttons by the focus ring (REACH) and grow when focused: a 24px gap between the
        // buttons (16 less the reach let the focused ring touch the other), the row widened by the reach so
        // the buttons line up with the text, and nothing clipped at the card's padding.
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, Ui.ipx(68 + 2 * r), 1);
        b.setMarginStart(Ui.ipx(24));
        buttons.addView(resetCancel, a);
        buttons.addView(resetGo, b);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.ipx(30 - r);
        blp.setMarginStart(-Ui.ipx(r));
        blp.setMarginEnd(-Ui.ipx(r));
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        card.setClipToPadding(false);
        card.addView(buttons, blp);

        showReset(card, 820, 1);
        root.post(resetCancel::requestFocus);
        List<String> h = new ArrayList<>();
        h.add(t.hintChoose());
        h.add(t.hintBackCancel());
        setHints(hints, h);
    }

    /** The second confirmation: OK held for three seconds fills the ring; letting go early starts it again. */
    private void openResetHold() {
        LinearLayout card = resetCard(760);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(resetTitle(t.resetHoldTitle(), Gravity.CENTER));
        TextView sub = resetLine(t.resetHoldHelp(), Tokens.MIST_400, 22, null);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub);
        resetRing = new Ring(this, ui400);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Ui.ipx(220), Ui.ipx(220));
        rlp.topMargin = Ui.ipx(18);
        rlp.bottomMargin = Ui.ipx(18);
        card.addView(resetRing, rlp);
        TextView note = resetLine(t.resetBackupNote(), Tokens.PLASTER_100, 22, null);
        note.setGravity(Gravity.CENTER);
        // Two lines: lineHeight spaces one line only, so the lines themselves get room (else they touch).
        note.setLineSpacing(0, 1.35f);
        card.addView(note);
        TextView cmd = resetLine("AwanTV-settings.ps1 -Restore", Tokens.GOLD_400, 22, null);
        cmd.setGravity(Gravity.CENTER);
        cmd.setTextDirection(View.TEXT_DIRECTION_LTR);
        card.addView(cmd);

        showReset(card, 760, 2);
        List<String> h = new ArrayList<>();
        h.add(t.hintHoldOk());
        h.add(t.hintBackCancel());
        setHints(hints, h);
    }

    private void showReset(LinearLayout card, int width, int stage) {
        if (resetView != null) root.removeView(resetView);
        resetView = new FrameLayout(this);
        resetView.setBackgroundColor(Tokens.SCRIM_MENU);
        resetView.setClickable(true);
        resetView.setClipChildren(false);
        resetView.addView(card, new FrameLayout.LayoutParams(Ui.ipx(width), -2, Gravity.CENTER));
        setRowsFocusable(false);
        root.addView(resetView, new FrameLayout.LayoutParams(-1, -1));
        hints.bringToFront();
        resetStage = stage;
        resetPressed = false;
    }

    private void closeReset() {
        stopHold();
        if (resetView != null) root.removeView(resetView);
        resetView = null;
        resetRing = null;
        resetStage = 0;
        setRowsFocusable(true);
        refresh();
        focusRow(rowIndex);
    }

    private boolean resetKey(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (resetStage == PRESS) return pressKey(e);
        if (resetStage == ASK) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (!down) closeReset();
            } else if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down && askSecond != null) (askFirst.isFocused() ? askSecond : askFirst).requestFocus();
            } else if (ok) {
                if (down && e.getRepeatCount() == 0) resetPressed = true;
                else if (!down && resetPressed && !e.isCanceled()) {
                    Runnable then = askFirst.isFocused() ? askOnFirst : null;
                    closeReset();
                    if (then != null) then.run();
                }
            }
            return true;
        }
        if (resetStage == 3) {
            // The first run's location question: Back, like Set location, goes back to choosing.
            if (k == KeyEvent.KEYCODE_BACK) {
                if (!down) locationAnswer(true);
            } else if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down) (locSet.isFocused() ? locLater : locSet).requestFocus();
            } else if (ok) {
                if (down && e.getRepeatCount() == 0) resetPressed = true;
                else if (!down && resetPressed && !e.isCanceled()) locationAnswer(locSet.isFocused());
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) closeReset();
            return true;
        }
        if (resetStage == 1) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down) (resetCancel.isFocused() ? resetGo : resetCancel).requestFocus();
                return true;
            }
            if (ok) {
                // Act on the release of a press made here: not the one that opened this, and not a press that
                // goes on into the next step's hold.
                if (down && e.getRepeatCount() == 0) resetPressed = true;
                else if (!down && resetPressed && !e.isCanceled()) {
                    if (resetGo.isFocused()) openResetHold();
                    else closeReset();
                }
                return true;
            }
            return true;
        }
        // Stage 2: hold OK; any other key closes.
        if (ok) {
            if (down && e.getRepeatCount() == 0) startHold();
            else if (!down) stopHold();
            return true;
        }
        if (!down) closeReset();
        return true;
    }

    private void startHold() {
        stopHold();
        resetHold = android.animation.ValueAnimator.ofFloat(0f, 1f).setDuration(RESET_HOLD_MS);
        resetHold.setInterpolator(null);
        resetHold.addUpdateListener(a -> { if (resetRing != null) resetRing.set((Float) a.getAnimatedValue()); });
        resetHold.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }

            @Override
            public void onAnimationEnd(android.animation.Animator a) { if (!cancelled) resetNow(); }
        });
        resetHold.start();
    }

    /** Let go before the ring is full: it empties, nothing happens. */
    private void stopHold() {
        if (resetHold != null) resetHold.cancel();
        resetHold = null;
        if (resetRing != null) resetRing.set(0f);
    }

    /**
     * The settings kept on the TV (for AwanTV-settings.ps1 -Restore), every setting removed, the TV put in step,
     * and Home started anew, which opens the city list as on a new install. What belongs to the TV stays
     * (SettingsTransfer.LOCAL: the screensaver it replaced, saved recitations, the radio's sleep timer).
     */
    private void resetNow() {
        resetStage = 0;
        SettingsTransfer.saveBeforeReset(this);
        android.content.SharedPreferences p = Prefs.of(this);
        android.content.SharedPreferences.Editor ed = p.edit();
        for (String key : p.getAll().keySet()) {
            if (!SettingsTransfer.local(key) || key.startsWith("wx_")) ed.remove(key);
        }
        ed.commit();
        Saver.apply(this);
        if (RemoteKeys.guard != null) RemoteKeys.guard.refresh();
        RemoteKeys.buttonsChanged();
        if (RemoteKeys.watch != null) RemoteKeys.watch.refreshCounter();
        startSafely(new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private LinearLayout resetCard(int width) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.ipx(48), Ui.ipx(40), Ui.ipx(48), Ui.ipx(36));
        GradientDrawable bg = round(Tokens.LAPIS_800, 24);
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
        card.setBackground(bg);
        card.setClipChildren(false);
        return card;
    }

    private TextView resetTitle(String s, int gravity) {
        TextView v = text(displayBold, Tokens.FONT_SIZE_DISPLAY_S, Tokens.GOLD_400);
        v.setText(s);
        v.setGravity(gravity);
        // Its layout params before lineHeight, which adjusts their margins (else it crashes on a null).
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        lineHeight(v, 1.2f);
        return v;
    }

    private TextView resetLine(String s, int color, float size, String mark) {
        TextView v = text(ui400, size, color);
        v.setText(mark == null ? s : mark + s);
        // A line that starts with "Awan TV" still reads from the screen's start (Urdu: its ✓ went to the end).
        v.setTextDirection(t.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        lineHeight(v, 1.55f);
        return v;
    }

    /** A list line whose mark is coloured: red for what goes, green for what stays. */
    private TextView resetMarked(String s, String mark, int markColor) {
        TextView v = resetLine(s, Tokens.PLASTER_100, 22, null);
        android.text.SpannableString text = new android.text.SpannableString(mark + s);
        text.setSpan(new android.text.style.ForegroundColorSpan(markColor), 0, mark.length(),
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.setText(text);
        return v;
    }

    private FocusTile resetButton(String label) {
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

    /** The hold ring: a lapis track, the gold arc filling clockwise from the top, OK in the middle. */
    private static final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Typeface face;
        private float done;
        /** What it says in the middle, and how large for its width: OK on the reset's, the seconds left on a countdown. */
        String label = "OK";
        float textScale = 52f / 220;

        Ring(Context c, android.graphics.Typeface face) {
            super(c);
            this.face = face;
        }

        void set(float f) {
            done = f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), stroke = w * 14 / 220, r = w / 2 - stroke;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Tokens.LAPIS_700);
            c.drawCircle(w / 2, w / 2, r, p);
            p.setColor(Tokens.GOLD_400);
            if (done > 0) c.drawArc(w / 2 - r, w / 2 - r, w / 2 + r, w / 2 + r, -90, 360 * done, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Tokens.PLASTER_100);
            p.setTypeface(face);
            p.setTextSize(w * textScale);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(label, w / 2, w / 2 - (p.ascent() + p.descent()) / 2, p);
        }
    }

    // ---------------------------------------------------------------- small views

    /** The value row's chevron toward the end side (design: M9 5l7 7-7 7); mirrored in Arabic. */
    private static final class Chevron extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Chevron(Context c, int color) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(color);
        }

        @Override
        protected void onDraw(Canvas c) {
            float k = getWidth() / 24f;
            p.setStrokeWidth(2.2f * k);
            if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) c.scale(-1, 1, getWidth() / 2f, 0);
            path.reset();
            path.moveTo(9 * k, 5 * k);
            path.lineTo(16 * k, 12 * k);
            path.lineTo(9 * k, 19 * k);
            c.drawPath(path, p);
        }
    }

    /** On: gold track, lapis knob at the end. Off: lapis-700 track, mist knob at the start. On a gold row the on track turns lapis so it still shows. */
    private static final class Switch extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean on, focused;

        Switch(Context c, boolean on, boolean focused) {
            super(c);
            this.on = on;
            this.focused = focused;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            p.setColor(on ? (focused ? Tokens.LAPIS_900 : Tokens.GOLD_400) : Tokens.LAPIS_700);
            c.drawRoundRect(0, 0, w, h, r, r, p);
            float knob = h * 18 / 48, inset = h * 6 / 48;
            boolean atEnd = on;
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float cx = (atEnd != rtl) ? w - inset - knob : inset + knob;
            p.setColor(on ? (focused ? Tokens.GOLD_400 : Tokens.LAPIS_900) : Tokens.MIST_400);
            c.drawCircle(cx, r, knob, p);
        }
    }
}
