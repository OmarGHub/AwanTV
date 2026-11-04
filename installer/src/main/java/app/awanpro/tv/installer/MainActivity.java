package app.awanpro.tv.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LocaleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.pm.ApplicationInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AdbPairingRequiredException;

/**
 * Awan TV Installer, one screen at a time in three stages: Prepare (turning on debugging on the TV), Connect
 * (finding the TV, then "Allow debugging?" or a pairing code) and Install (the Awan TV it carries, then the setup of
 * tools/AwanTV-install.ps1). Every screen is the same frame: stage bar, arch, title, lead, content, and the actions
 * pinned to the bottom. From Afnon's installer (MIT), without its personalising stage: Awan TV asks for the city
 * itself on its first start.
 */
public final class MainActivity extends Activity {
    static final int PREPARE = 0, CONNECT = 1, INSTALL = 2, DONE = 3;

    final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Finder finder, pairFinder;
    private Adb adb;

    private LinearLayout stages, footer;
    LinearLayout content;
    private ScrollView scroll;
    Hero hero;
    private int stage;
    /** What Back does on this screen; null while it must not be interrupted. */
    Runnable back;
    /**
     * Bumped whenever the user moves to another screen, so a worker that finishes afterwards (a connect that
     * succeeds after the user went Back) leaves the screen alone.
     */
    int gen;
    /** The TV list on the find screen, refreshed as the finder hears from TVs. */
    private LinearLayout tvList;
    private View help;

    /** The answer to "Update the installer" (SelfUpdate.status): Android's question, or a failure, while open. */
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        SelfUpdate.status(this, i);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // After the installer updated itself, Android opens the new one with its answer (SUCCESS): read and dropped.
        SelfUpdate.status(this, getIntent());
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Ui.LAPIS_900);
        getWindow().setNavigationBarColor(Ui.LAPIS_900);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.LAPIS_900);
        root.addView(languageBar(), Ui.wide(this, 0));
        stages = Ui.row(this);
        stages.setPadding(dp(20), dp(6), dp(20), dp(6));
        root.addView(stages, Ui.wide(this, 0));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = Ui.column(this);
        content.setPadding(dp(20), dp(4), dp(20), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        footer = Ui.column(this);
        footer.setPadding(dp(20), dp(8), dp(20), dp(20));
        root.addView(footer, Ui.wide(this, 0));
        setContentView(root);

        // A newer installer than this one? Asked once, shown on the guide (where everyone starts) and in About.
        checkLatest();
        SelfUpdate.listen(this::fillUpdate);
        hero = new Hero(this);
        finder = new Finder(this, tvs -> runOnUiThread(() -> showTvs(tvs)));

        String demo = getIntent().getStringExtra("screen");
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (demo != null && debuggable) showDemo(demo);
        else showGuide();
    }

    // ---------------------------------------------------------------- language

    private static final String PREFS = "installer", LANG = "lang";
    private TextView langButton;
    /** "Awan TV 1.10.2" at the top on every screen; on the guide it opens About (the "i" beside it). */
    private TextView versionView;

    private void versionOpensAbout(boolean on) {
        versionView.setClickable(on);
        versionView.setCompoundDrawablesRelativeWithIntrinsicBounds(on ? R.drawable.ic_info : 0, 0, 0, 0);
    }

    /**
     * Arabic or English whatever the phone's language: the button names the other one. Android 13+ keeps the choice
     * as this app's own language (Settings > Apps > Language shows it too); before that it is kept here and applied
     * in attachBaseContext. Either way the screen starts again in the new language, at the guide.
     */
    private View languageBar() {
        LinearLayout bar = Ui.row(this);
        bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(12), dp(20), 0);
        langButton = Ui.text(this, Ui.arabic(this) ? "English" : "عربي", Ui.PLASTER, 13, Ui.medium());
        GradientDrawable pill = Ui.round(Ui.LAPIS_900, 99, this);
        pill.setStroke(dp(1), 0x88d8b46a);
        langButton.setBackground(pill);
        langButton.setPadding(dp(14), dp(6), dp(14), dp(6));
        langButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_language, 0, 0, 0);
        langButton.setCompoundDrawablePadding(dp(7));
        langButton.setGravity(Gravity.CENTER_VERTICAL);
        langButton.setOnClickListener(v -> switchLanguage());
        // Which Awan TV this installer carries (its own version is the same number), on every screen at the other
        // end from the language button, which hides once connected.
        String own = ownVersion();
        android.text.SpannableStringBuilder name = new android.text.SpannableStringBuilder("Awan TV ");
        int at = name.length();
        name.append(own);
        name.setSpan(new android.text.style.ForegroundColorSpan(Ui.GOLD_400), at, name.length(), 0);
        versionView = Ui.text(this, own.isEmpty() ? "" : name, Ui.MIST, 12, Ui.medium());
        versionView.setTextDirection(View.TEXT_DIRECTION_LTR);
        versionView.setCompoundDrawablePadding(dp(6));
        versionView.setGravity(Gravity.CENTER_VERTICAL);
        versionView.setPadding(0, dp(6), dp(6), dp(6));
        versionView.setOnClickListener(v -> {
            if (versionView.isClickable()) go(this::showAbout);
        });
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bar.addView(versionView, vp);
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(langButton);
        return bar;
    }

    private void switchLanguage() {
        String to = Ui.arabic(this) ? "en" : "ar";
        stopFinders();
        if (Build.VERSION.SDK_INT >= 33) {
            // Android starts the screen again by itself.
            getSystemService(LocaleManager.class).setApplicationLocales(new LocaleList(Locale.forLanguageTag(to)));
        } else {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(LANG, to).commit();
            recreate();
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        String lang = Build.VERSION.SDK_INT >= 33 ? null : base.getSharedPreferences(PREFS, MODE_PRIVATE).getString(LANG, null);
        if (lang != null) {
            Locale l = Locale.forLanguageTag(lang);
            Configuration conf = new Configuration(base.getResources().getConfiguration());
            conf.setLocale(l);
            conf.setLayoutDirection(l);
            base = base.createConfigurationContext(conf);
        }
        super.attachBaseContext(base);
    }

    @Override
    public void onBackPressed() {
        if (back != null) back.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from Android's "Install unknown apps" for this app: the update goes on if it was allowed.
        if (updateAfterPermission) {
            updateAfterPermission = false;
            if (SelfUpdate.allowed(this) && newerInstaller != null) {
                askingPermission = false;
                SelfUpdate.start(this, newerInstaller);
            }
            fillUpdate();
        }
    }

    @Override
    protected void onDestroy() {
        SelfUpdate.listen(null);
        stopFinders();
        work.shutdownNow();
        if (adb != null) {
            Adb a = adb;
            // Anything at all: the library may throw more than IOException here, and an uncaught one in this thread
            // brought the whole app down as it closed (Done at the end).
            new Thread(() -> { try { a.disconnect(); } catch (Throwable ignored) { } }).start();
        }
        super.onDestroy();
    }

    // The installer's own update

    /** A newer installer than this one on GitHub ("1.3.0"), once the question at start-up has an answer; else null. */
    private String newerInstaller;
    /** GitHub's answer about the newest release: 0 asking, 1 answered, -1 no answer (offline). */
    private int latestState;
    /** The place for the installer's update on the screen shown (the guide's notice, About's card), or null. */
    private LinearLayout updateSlot;
    /** About's card says "you have the newest" too; the guide's shows only a newer one. */
    private boolean updateInAbout;
    /** Update pressed without "Install unknown apps": the card explains it, then Android's setting. */
    private boolean askingPermission, updateAfterPermission;
    /** The guide's "Find my TV", off while the installer is being replaced. */
    private TextView findButton;

    /** Asks GitHub for the newest release (once at start, again from About's Check now). */
    private void checkLatest() {
        latestState = 0;
        fillUpdate();
        work.execute(() -> {
            String latest = Setup.latest();
            String own = ownVersion();
            runOnUiThread(() -> {
                latestState = latest == null ? -1 : 1;
                if (latest != null) {
                    newerInstaller = !own.isEmpty() && Setup.compare(latest, own) > 0 ? latest : null;
                }
                fillUpdate();
            });
        });
    }

    /** "1.10.2", kept left to right inside Arabic. */
    private static String iso(String v) {
        return "\u2066" + v + "\u2069";
    }

    /** The installer's update, as it stands: newer one out, permission, downloading, installing, failed, or (About) up to date. */
    private void fillUpdate() {
        if (findButton != null) Ui.enabled(findButton, !SelfUpdate.busy());
        LinearLayout slot = updateSlot;
        if (slot == null) return;
        slot.removeAllViews();
        int st = SelfUpdate.state();
        boolean newer = newerInstaller != null;
        if (!newer && st == SelfUpdate.IDLE && !updateInAbout) return;
        LinearLayout card = Ui.card(this);
        GradientDrawable bg = Ui.round(Ui.LAPIS_800, 18, this);
        bg.setStroke(dp(1), newer || st != SelfUpdate.IDLE ? 0x99d8b46a : Ui.LINE_SOFT);
        card.setBackground(bg);
        LinearLayout buttons = Ui.row(this);
        if (st == SelfUpdate.DOWNLOADING) {
            card.addView(gold(getString(R.string.upd_downloading, iso(SelfUpdate.version()))));
            card.addView(bar(SelfUpdate.percent()), Ui.wide(this, 12));
            String of = SelfUpdate.total() > 0 ? getString(R.string.upd_progress, number(SelfUpdate.percent()),
                    mb(SelfUpdate.done()), mb(SelfUpdate.total())) : number(SelfUpdate.percent()) + "%";
            card.addView(Ui.latin(Ui.small(this, of)), Ui.wide(this, 8));
            buttons.addView(Ui.pill(this, getString(R.string.cancel), false, v -> SelfUpdate.cancel()));
        } else if (st == SelfUpdate.INSTALLING) {
            card.addView(gold(getString(R.string.upd_installing_title)));
            card.addView(Ui.small(this, getString(R.string.upd_installing)), Ui.wide(this, 6));
            if (SelfUpdate.confirmWaiting()) {
                buttons.addView(Ui.pill(this, getString(R.string.upd_continue), true, v -> SelfUpdate.confirm(this)));
            }
        } else if (st == SelfUpdate.FAILED) {
            card.addView(gold(getString(R.string.upd_failed_title)));
            TextView body = Ui.small(this, getString(R.string.upd_failed, Setup.LATEST_INSTALLER));
            Ui.links(body);
            card.addView(body, Ui.wide(this, 6));
            buttons.addView(Ui.pill(this, getString(R.string.upd_retry), true, v -> {
                SelfUpdate.reset();
                pressUpdate();
            }));
        } else if (askingPermission && newer) {
            card.addView(gold(getString(R.string.upd_perm_title)));
            card.addView(Ui.small(this, getString(R.string.upd_perm_body)), Ui.wide(this, 6));
            buttons.addView(Ui.pill(this, getString(R.string.upd_perm_open), true, v -> openInstallPermission()));
            buttons.addView(Ui.pill(this, getString(R.string.upd_later), false, v -> {
                askingPermission = false;
                fillUpdate();
            }));
        } else if (newer) {
            card.addView(gold(getString(R.string.newer_installer_title, iso(newerInstaller))));
            card.addView(Ui.small(this, getString(R.string.newer_installer_body, iso(ownVersion()))), Ui.wide(this, 6));
            buttons.addView(Ui.pill(this, getString(R.string.upd_button), true, v -> pressUpdate()));
        } else {
            // About, nothing newer: what GitHub said, and Check now.
            LinearLayout row = Ui.row(this);
            LinearLayout words = Ui.column(this);
            words.addView(Ui.label(this, getString(R.string.about_updates)));
            int said = latestState == 0 ? R.string.about_checking : latestState > 0 ? R.string.about_latest : R.string.about_offline;
            words.addView(Ui.small(this, getString(said)), Ui.wide(this, 3));
            row.addView(words, Ui.fill(this));
            TextView check = Ui.pill(this, getString(R.string.about_check), false, v -> checkLatest());
            Ui.enabled(check, latestState != 0);
            row.addView(check);
            card.addView(row);
        }
        if (buttons.getChildCount() > 0) card.addView(buttons, Ui.wide(this, 12));
        slot.addView(card, Ui.wide(this, 0));
    }

    private TextView gold(String s) {
        TextView t = Ui.label(this, s);
        t.setTextColor(Ui.GOLD_400);
        return t;
    }

    /** The download's bar: gold over lapis-700, its share of the width. */
    private View bar(int percent) {
        LinearLayout track = Ui.row(this);
        track.setBackground(Ui.round(Ui.LAPIS_700, 4, this));
        track.setClipToOutline(true);
        View fill = new View(this);
        fill.setBackground(Ui.round(Ui.GOLD_400, 4, this));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(8), Math.max(percent, 0)));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(8), Math.max(100 - percent, 0)));
        return track;
    }

    private void pressUpdate() {
        if (newerInstaller == null || SelfUpdate.busy()) return;
        if (!SelfUpdate.allowed(this)) {
            askingPermission = true;
            fillUpdate();
            return;
        }
        askingPermission = false;
        SelfUpdate.start(this, newerInstaller);
    }

    /** Android's "Install unknown apps" for this app; onResume goes on when the user comes back. */
    private void openInstallPermission() {
        updateAfterPermission = true;
        try {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            updateAfterPermission = false;
        }
    }

    // About

    /** About (the "i" by the version at the top, on the guide): the version, its update, who made it, links, licences. */
    private void showAbout() {
        String own = ownVersion();
        frame(PREPARE, Hero.Mode.TV, "Awan TV", getString(R.string.about_lead, iso(own), iso(own)));
        aboutChrome();
        updateSlot = Ui.column(this);
        updateInAbout = true;
        add(updateSlot, 16);
        if (latestState < 0) checkLatest(); else fillUpdate();
        LinearLayout links = Ui.card(this);
        aboutRow(links, getString(R.string.about_by), "awanpro.app", null);
        aboutRow(links, getString(R.string.about_project), "github.com/OmarGHub/AwanTV", null);
        aboutRow(links, getString(R.string.about_releases), getString(R.string.about_releases_detail), null);
        aboutRow(links, getString(R.string.about_based), getString(R.string.about_based_detail), null);
        aboutRow(links, getString(R.string.about_licences), getString(R.string.about_licences_detail), v -> go(this::showLicences));
        add(links, 12);
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showGuide)));
        back = () -> go(this::showGuide);
    }

    /** The screens off the steps (About, licences): no stage bar, no language button, the version not a button. */
    private void aboutChrome() {
        stages.setVisibility(View.GONE);
        langButton.setVisibility(View.INVISIBLE);
        versionOpensAbout(false);
    }

    private void aboutRow(LinearLayout card, String title, String detail, View.OnClickListener click) {
        if (card.getChildCount() > 0) {
            View line = new View(this);
            line.setBackgroundColor(Ui.LINE_SOFT);
            card.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        LinearLayout row = Ui.column(this);
        row.setPadding(0, dp(10), 0, dp(10));
        row.addView(Ui.label(this, title));
        TextView d = Ui.small(this, click != null ? detail + "  ›" : detail);
        row.addView(d, Ui.wide(this, 3));
        if (click != null) {
            Ui.pressable(row, Ui.round(0x00000000, 8, this), 0x33efe7d6);
            row.setOnClickListener(click);
        }
        card.addView(row, Ui.wide(this, 0));
    }

    /** The licence texts carried in assets/licenses: README.txt first, then each file by name. */
    private void showLicences() {
        frame(PREPARE, Hero.Mode.TV, getString(R.string.about_licences), getString(R.string.licences_lead));
        aboutChrome();
        hero.setVisibility(View.GONE);
        String[] names;
        try {
            names = getAssets().list("licenses");
        } catch (IOException e) {
            names = new String[0];
        }
        if (names == null) names = new String[0];
        Arrays.sort(names);
        add(licenceText(asset("licenses/README.txt")), 16);
        LinearLayout files = Ui.card(this);
        for (String n : names) {
            if (n.equals("README.txt")) continue;
            aboutRow(files, n, getString(R.string.licence_open), v -> go(() -> showLicence(n)));
        }
        add(files, 12);
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showAbout)));
        back = () -> go(this::showAbout);
    }

    private void showLicence(String name) {
        frame(PREPARE, Hero.Mode.TV, name, "");
        aboutChrome();
        hero.setVisibility(View.GONE);
        add(licenceText(asset("licenses/" + name)), 12);
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showLicences)));
        back = () -> go(this::showLicences);
    }

    /** A licence as written (English, left to right), small. */
    private View licenceText(String text) {
        LinearLayout card = Ui.card(this);
        TextView t = Ui.text(this, text, Ui.PLASTER, 12, Ui.medium());
        t.setTextDirection(View.TEXT_DIRECTION_LTR);
        t.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        t.setTextIsSelectable(true);
        card.addView(t);
        return card;
    }

    private String asset(String path) {
        try (java.io.InputStream in = getAssets().open(path)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    // My TVs

    /** The saved TVs on the screen now, with what was read from each; null off My TVs. */
    private List<MyTvs.Tv> myTvs;
    private LinearLayout myList;
    private TextView myUpdate;
    /** Wireless debugging's ports heard by mDNS while My TVs is open: host -> port. */
    private final java.util.Map<String, Integer> tlsHeard = new java.util.concurrent.ConcurrentHashMap<>();
    private Finder myFinder;

    /** My TVs: each saved TV's Awan TV version, read without a prompt on any screen; tick and update. */
    private void showMyTvs() {
        stopFinders();
        frame(PREPARE, Hero.Mode.TV, getString(R.string.mytvs_title), getString(R.string.mytvs_lead, iso(ownVersion())));
        aboutChrome();
        hero.setVisibility(View.GONE);
        myTvs = MyTvs.load(this);
        TextView pick = Ui.text(this, getString(R.string.mytvs_pick), Ui.GOLD_200, 13, Ui.medium());
        pick.setPaintFlags(pick.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        pick.setOnClickListener(v -> {
            for (MyTvs.Tv t : myTvs) t.chosen = t.state == MyTvs.State.OLDER;
            fillMyTvs();
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.topMargin = dp(12);
        pp.gravity = Gravity.END;
        if (!myTvs.isEmpty()) content.addView(pick, pp);
        myList = Ui.column(this);
        add(myList, 4);
        if (myTvs.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.small(this, getString(R.string.mytvs_empty)));
            add(card, 16);
        }
        myUpdate = Ui.primary(this, getString(R.string.mytvs_update, number(0)), v -> go(this::updateChosen));
        foot(myUpdate);
        foot(Ui.secondary(this, getString(R.string.mytvs_add), v -> go(this::showFind)));
        back = () -> go(this::showGuide);
        fillMyTvs();
        if (!myTvs.isEmpty()) readMyTvs();
    }

    /** Reads each saved TV in turn on the worker: reached, the same TV (its serial), and its Awan TV version. */
    private void readMyTvs() {
        listenForTls();
        int g = gen;
        List<MyTvs.Tv> tvs = myTvs;
        String carried = ownVersion();
        work.execute(() -> {
            for (MyTvs.Tv t : tvs) {
                if (g != gen) return;
                MyTvs.State st;
                try {
                    Adb a = adb();
                    if (!reach(a, t)) {
                        st = MyTvs.State.NO_ANSWER;
                    } else if (!t.id.equals(a.shell("getprop ro.serialno").out.trim())) {
                        st = MyTvs.State.MOVED;
                    } else {
                        String v = versionOnTv(a);
                        t.version = v == null ? "" : v;
                        MyTvs.version(this, t.id, t.version);
                        int c = t.version.isEmpty() ? 0 : Setup.compare(t.version, carried);
                        st = t.version.isEmpty() ? MyTvs.State.NONE : c < 0 ? MyTvs.State.OLDER
                                : c == 0 ? MyTvs.State.SAME : MyTvs.State.NEWER;
                    }
                } catch (AdbPairingRequiredException e) {
                    st = MyTvs.State.PAIR;
                } catch (Exception e) {
                    st = MyTvs.State.NO_ANSWER;
                }
                MyTvs.State shown = st;
                post(g, () -> {
                    t.state = shown;
                    fillMyTvs();
                });
            }
            disconnectQuietly();
        });
    }

    /** Wireless debugging's port changes: heard by mDNS while My TVs is open. */
    private void listenForTls() {
        if (myFinder != null) myFinder.stop();
        myFinder = new Finder(this, tvs -> {
            for (Finder.Tv t : tvs) if (t.tls > 0) tlsHeard.put(t.host, t.tls);
        });
        myFinder.start();
    }

    /**
     * On the worker: connects to a saved TV with no prompt (it trusts this phone): its network port first, then
     * Wireless debugging at the port heard now (waiting a few seconds for it), else the one saved. False if neither
     * answered; AdbPairingRequiredException when the TV forgot this phone's pairing.
     */
    private boolean reach(Adb a, MyTvs.Tv t) throws Exception {
        try { a.disconnect(); } catch (Exception ignored) { }
        a.setTimeout(10, TimeUnit.SECONDS);
        try {
            if (t.classic > 0) {
                try {
                    if (a.connect(t.host, t.classic)) return true;
                } catch (AdbPairingRequiredException e) {
                    throw e;
                } catch (Exception ignored) {
                    // Not on that port now: Wireless debugging next.
                }
            }
            Integer tls = tlsHeard.get(t.host);
            for (int i = 0; tls == null && i < 20; i++) {
                Thread.sleep(300);
                tls = tlsHeard.get(t.host);
            }
            int port = tls != null ? tls : t.tls;
            if (port <= 0) return false;
            try { a.disconnect(); } catch (Exception ignored) { }
            return a.connect(t.host, port);
        } finally {
            a.setTimeout(90, TimeUnit.SECONDS);
        }
    }

    private void disconnectQuietly() {
        Adb a = adb;
        if (a != null) {
            try { a.disconnect(); } catch (Throwable ignored) { }
        }
    }

    /** The list as it stands: a box on each TV that needs an update, its state under its name. */
    private void fillMyTvs() {
        if (myList == null || myTvs == null) return;
        myList.removeAllViews();
        String carried = ownVersion();
        int chosen = 0;
        boolean reading = false, fixing = false;
        for (MyTvs.Tv t : myTvs) {
            if (t.state == MyTvs.State.CHECKING) reading = true;
            if (!tickable(t)) t.chosen = false;
            if (t.chosen) {
                chosen++;
                if (t.state == MyTvs.State.SAME) fixing = true;
            }
            String line;
            int color = Ui.MIST;
            switch (t.state) {
                case OLDER: line = getString(R.string.mytvs_older, iso(t.version), iso(carried)); color = Ui.GOLD_400; break;
                case SAME: line = getString(R.string.mytvs_same, iso(t.version)); color = 0xff7fc79a; break;
                case NEWER: line = getString(R.string.mytvs_newer, iso(t.version)); break;
                case NONE: line = getString(R.string.mytvs_none); break;
                case NO_ANSWER: line = getString(R.string.mytvs_no_answer); break;
                case PAIR: line = getString(R.string.mytvs_pair); color = Ui.CORAL; break;
                case MOVED: line = getString(R.string.mytvs_moved); color = Ui.CORAL; break;
                default: line = getString(R.string.mytvs_checking); break;
            }
            View card = myTvCard(t, line, color, tickable(t));
            myList.addView(card, Ui.wide(this, 10));
        }
        if (myUpdate != null) {
            // An up-to-date TV among the ticked: "Run on the ticked" (it is set up again, or installed again).
            myUpdate.setText(getString(fixing ? R.string.mytvs_run : R.string.mytvs_update, number(chosen)));
            Ui.enabled(myUpdate, chosen > 0 && !reading);
        }
    }

    /**
     * Ticked: one needing an update, or (1.13.0) an up-to-date one to put right, as the editor's Update TVs: with this
     * very APK on it nothing is sent, it is only set up again. A newer one never: going back needs Awan TV removed.
     */
    private static boolean tickable(MyTvs.Tv t) {
        return t.state == MyTvs.State.OLDER || t.state == MyTvs.State.SAME;
    }

    /** One TV: its box (or the same room empty), the TV glyph, name, model and a state line. */
    private View myTvCard(MyTvs.Tv t, String line, int color, boolean tickable) {
        LinearLayout card = Ui.row(this);
        GradientDrawable bg = Ui.round(Ui.LAPIS_800, 16, this);
        bg.setStroke(dp(1), t.chosen ? 0x99d8b46a : Ui.LINE_SOFT);
        card.setBackground(bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView box = Ui.text(this, t.chosen ? "✓" : "", Ui.LAPIS_900, 14, Ui.semibold());
        box.setGravity(Gravity.CENTER);
        GradientDrawable bb = Ui.round(t.chosen ? Ui.GOLD_400 : 0x00000000, 6, this);
        if (!t.chosen) bb.setStroke(dp(1.5f), Ui.MIST);
        box.setBackground(bb);
        box.setVisibility(tickable ? View.VISIBLE : View.INVISIBLE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(22), dp(22));
        blp.setMarginEnd(dp(12));
        card.addView(box, blp);
        card.addView(new TvGlyph(this), new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout words = Ui.column(this);
        words.setPaddingRelative(dp(12), 0, 0, 0);
        TextView name = Ui.text(this, t.shown(), Ui.PLASTER, 16, Ui.semibold());
        name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        words.addView(name);
        String sub = t.model != null && !t.model.isEmpty() && !t.model.equals(t.shown()) ? "⁨" + t.model + "⁩" : "";
        if (!sub.isEmpty()) {
            TextView m = Ui.small(this, sub);
            m.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            words.addView(m, Ui.wide(this, 2));
        }
        TextView state = Ui.text(this, line, color, 13, Ui.medium());
        state.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        words.addView(state, Ui.wide(this, 4));
        if (tickable && t.state == MyTvs.State.SAME) {
            TextView hint = Ui.small(this, getString(R.string.mytvs_same_hint));
            hint.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            words.addView(hint, Ui.wide(this, 3));
        }
        if (t.percent >= 0) words.addView(bar(t.percent), Ui.wide(this, 6));
        card.addView(words, Ui.fill(this));
        if (tickable) {
            card.setOnClickListener(v -> {
                t.chosen = !t.chosen;
                fillMyTvs();
            });
        }
        // Held: out of My TVs (nothing changes on the TV).
        card.setOnLongClickListener(v -> {
            if (back == null) return false;
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage(getString(R.string.mytvs_remove_q, t.shown()))
                    .setPositiveButton(R.string.mytvs_remove, (d, w) -> {
                        MyTvs.forget(this, t.id);
                        myTvs.remove(t);
                        fillMyTvs();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        return card;
    }

    /** Updates the ticked TVs one after another with the Awan TV this installer carries; nothing is asked on them. */
    private void updateChosen() {
        List<MyTvs.Tv> chosen = new ArrayList<>();
        if (myTvs != null) for (MyTvs.Tv t : myTvs) if (t.chosen) chosen.add(t);
        if (chosen.isEmpty()) {
            go(this::showMyTvs);
            return;
        }
        List<MyTvs.Tv> all = myTvs;
        boolean fixing = false;
        for (MyTvs.Tv t : chosen) if (t.state == MyTvs.State.SAME) fixing = true;
        boolean fix = fixing;
        frame(INSTALL, Hero.Mode.PROGRESS, getString(fix ? R.string.upd_tvs_title_do : R.string.upd_tvs_title),
                getString(R.string.upd_tvs_lead));
        aboutChrome();
        hero.setVisibility(View.GONE);
        back = null;
        myTvs = chosen;
        for (MyTvs.Tv t : chosen) {
            t.chosen = false;
            t.result = getString(R.string.upd_tv_wait);
            t.percent = -1;
        }
        myList = Ui.column(this);
        add(myList, 8);
        myUpdate = null;
        showUpdating();
        int g = gen;
        work.execute(() -> {
            File apk;
            String carried, hash;
            try {
                apk = Setup.bundled(this);
                carried = Setup.version(this, apk);
                hash = sha256(apk);
            } catch (Exception e) {
                for (MyTvs.Tv t : chosen) t.result = "✗ " + message(e);
                post(g, () -> updatesDone(all, chosen, fix));
                return;
            }
            for (MyTvs.Tv t : chosen) {
                String end = updateOne(g, t, apk, carried, hash);
                post(g, () -> {
                    t.result = end;
                    t.percent = -1;
                    showUpdating();
                });
            }
            disconnectQuietly();
            post(g, () -> updatesDone(all, chosen, fix));
        });
    }

    /** The APK's SHA-256 in hex, as the TV's sha256sum prints it. */
    private static String sha256(File f) throws IOException, java.security.NoSuchAlgorithmException {
        java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) d.update(buf, 0, n);
        }
        StringBuilder b = new StringBuilder();
        for (byte x : d.digest()) b.append(String.format(java.util.Locale.ROOT, "%02x", x));
        return b.toString();
    }

    /** On the worker: the TV's Awan TV APK's SHA-256 (pm path, then sha256sum), or "" when it can't be read. */
    private static String hashOnTv(Adb a) throws IOException, InterruptedException {
        String path = "";
        for (String l : a.shell("pm path " + Setup.PACKAGE).out.split("\n")) {
            l = l.trim();
            if (l.startsWith("package:")) { path = l.substring(8); break; }
        }
        if (path.isEmpty()) return "";
        Adb.Result r = a.shell("sha256sum " + path);
        return r.ok() ? r.out.trim().split("\\s+")[0].toLowerCase(java.util.Locale.ROOT) : "";
    }

    /** On the worker: one TV, as Find my TV's update does (install over it, wait, the setup steps); its line at the end. */
    private String updateOne(int g, MyTvs.Tv t, File apk, String carried, String hash) {
        try {
            progressLine(g, t, getString(R.string.upd_tv_connecting), -1);
            Adb a = adb();
            if (!reach(a, t)) return "✗ " + getString(R.string.mytvs_no_answer);
            if (!t.id.equals(a.shell("getprop ro.serialno").out.trim())) return "✗ " + getString(R.string.mytvs_moved);
            // The same version there: this very APK is not sent again (as AwanTV-install), only the steps run;
            // another file with the same number (a build of one's own) is installed again.
            boolean same = carried.equals(versionOnTv(a));
            if (same && !hash.isEmpty() && hash.equals(hashOnTv(a))) {
                progressLine(g, t, getString(R.string.upd_tv_same_setting), -1);
                boolean all = quietSteps(a, false);
                t.state = MyTvs.State.SAME;
                return all ? getString(R.string.upd_tv_setup_done, iso(carried)) : getString(R.string.upd_tv_fix_partly, iso(carried));
            }
            // The install stops Awan TV: its Home (or screensaver) in front now opens again at the end; another app the
            // viewer is in is left alone.
            String front = a.shell("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'").out;
            boolean reopen = front.contains(Setup.PACKAGE + "/") || front.contains("DreamActivity");
            progressLine(g, t, getString(R.string.upd_tv_sending, number(0)), 0);
            int[] last = {0};
            String result = a.install(apk, f -> {
                int pct = (int) (f * 100);
                if (pct == last[0]) return;
                last[0] = pct;
                progressLine(g, t, getString(R.string.upd_tv_sending, number(pct)), pct);
            });
            if (result.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE")) return "✗ " + getString(R.string.upd_tv_other_key);
            if (result.contains("INSTALL_FAILED_VERSION_DOWNGRADE")) return "✗ " + getString(R.string.newer_on_tv);
            if (!result.startsWith("Success")) return "✗ " + getString(R.string.install_failed, result);
            progressLine(g, t, getString(R.string.upd_tv_setting), -1);
            // As after Find my TV's install: Android may handle the update's force-stop a moment later.
            Thread.sleep(5000);
            boolean all = quietSteps(a, reopen);
            String now = versionOnTv(a);
            if (now != null) {
                t.version = now;
                MyTvs.version(this, t.id, now);
            }
            if (!carried.equals(now)) return "✗ " + getString(R.string.install_failed, String.valueOf(now));
            t.state = MyTvs.State.SAME;
            if (same) return all ? getString(R.string.upd_tv_again_done, iso(now)) : getString(R.string.upd_tv_fix_partly, iso(now));
            return all ? getString(R.string.upd_tv_done, iso(now)) : getString(R.string.upd_tv_partly, iso(now));
        } catch (AdbPairingRequiredException e) {
            return "✗ " + getString(R.string.mytvs_pair);
        } catch (Exception e) {
            return "✗ " + connectError(e);
        }
    }

    private void progressLine(int g, MyTvs.Tv t, String line, int percent) {
        post(g, () -> {
            t.result = line;
            t.percent = percent;
            showUpdating();
        });
    }

    /** The update's list: each TV with its line (a bar while its APK goes). */
    private void showUpdating() {
        if (myList == null || myTvs == null) return;
        myList.removeAllViews();
        for (MyTvs.Tv t : myTvs) {
            String r = t.result == null ? "" : t.result;
            int color = r.startsWith("✗") ? Ui.CORAL : r.startsWith("✓") ? 0xff7fc79a : Ui.GOLD_200;
            myList.addView(myTvCard(t, r, color, false), Ui.wide(this, 10));
        }
    }

    /**
     * On the worker: Setup.STEPS without the timeline (as runSteps); true when every required step is checked. Awan TV
     * is opened at the end only when open: it was in front before (1.13.0), never over another app.
     */
    private boolean quietSteps(Adb a, boolean open) throws IOException, InterruptedException {
        boolean all = true;
        for (Setup.Step s : Setup.STEPS) {
            if (!open && s.command.contains("am start")) continue;
            if (s.check != null && a.shell(s.check).ok()) continue;
            boolean ran = a.shell(s.command).ok();
            boolean ok = s.check == null ? ran : a.shell(s.check).ok();
            if (!ok && s.required) all = false;
        }
        return all;
    }

    /** The end: how many went, each TV's line, Try again for those that failed. */
    private void updatesDone(List<MyTvs.Tv> all, List<MyTvs.Tv> chosen, boolean fix) {
        int ok = 0;
        List<MyTvs.Tv> failed = new ArrayList<>();
        for (MyTvs.Tv t : chosen) {
            if (t.result != null && t.result.startsWith("✗")) failed.add(t); else ok++;
        }
        frame(DONE, failed.isEmpty() ? Hero.Mode.DONE : Hero.Mode.PROBLEM,
                getString(fix ? R.string.upd_done_title_do : R.string.upd_done_title),
                getString(fix ? R.string.upd_done_lead_do : R.string.upd_done_lead, number(ok), number(chosen.size())));
        aboutChrome();
        hero.setVisibility(View.GONE);
        myTvs = chosen;
        myList = Ui.column(this);
        add(myList, 8);
        showUpdating();
        if (!failed.isEmpty()) {
            foot(Ui.primary(this, getString(R.string.upd_retry_failed), v -> {
                myTvs = all;
                for (MyTvs.Tv t : all) t.chosen = failed.contains(t);
                go(this::updateChosen);
            }));
        }
        foot(Ui.secondary(this, getString(R.string.finish), v -> go(this::showMyTvs)));
        back = () -> go(this::showMyTvs);
    }

    // Prepare

    private void showGuide() {
        stopFinders();
        frame(PREPARE, Hero.Mode.TV, getString(R.string.guide_title), getString(R.string.guide_lead));
        versionOpensAbout(true);
        updateSlot = Ui.column(this);
        updateInAbout = false;
        add(updateSlot, 12);
        TextView both = Ui.chip(this, getString(R.string.guide_both), false);
        // Whole, on two lines if it must: cut short it said nothing.
        both.setSingleLine(false);
        both.setMaxLines(3);
        both.setEllipsize(null);
        both.setGravity(Gravity.CENTER);
        both.setBackground(Ui.round(Ui.LAPIS_700, 14, this));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        bp.topMargin = dp(12);
        content.addView(both, bp);
        add(step(1, R.string.g1_title, R.string.g1_body, Ui.tvText(this, R.string.tv_about_en, R.string.tv_about_ar)), 18);
        add(step(2, R.string.g2_title, R.string.g2_body, Ui.tvText(this, R.string.tv_build_en, R.string.tv_build_ar)), 10);
        add(step(3, R.string.g3_title, R.string.g3_body, Ui.tvText(this, R.string.tv_dev_en, R.string.tv_dev_ar),
                Ui.tvText(this, R.string.tv_usb_en, R.string.tv_usb_ar),
                Ui.small(this, getString(R.string.g3_wireless)),
                Ui.tvText(this, R.string.tv_wireless_en, R.string.tv_wireless_ar)), 10);
        add(step(4, R.string.g4_title, R.string.g4_body), 10);
        findButton = Ui.primary(this, getString(R.string.guide_ready), v -> {
            if (!SelfUpdate.busy()) go(this::showFind);
        });
        foot(findButton);
        int mine = MyTvs.count(this);
        foot(Ui.secondary(this, getString(R.string.mytvs_button) + (mine > 0 ? " (" + number(mine) + ")" : ""),
                v -> {
                    if (!SelfUpdate.busy()) go(this::showMyTvs);
                }));
        fillUpdate();
        back = this::finish;
    }

    /** A numbered card: its title, then what to find on the TV (in both languages), with the detail after the first. */
    private View step(int n, int title, int body, View... tv) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.addView(Ui.badge(this, number(n)));
        LinearLayout words = Ui.column(this);
        words.setPaddingRelative(dp(14), dp(3), 0, 0);
        words.addView(Ui.label(this, getString(title)));
        for (int i = 0; i < tv.length; i++) {
            words.addView(tv[i], Ui.wide(this, 8));
            if (i == 0 && body != 0) words.addView(Ui.small(this, getString(body)), Ui.wide(this, 8));
        }
        if (tv.length == 0 && body != 0) words.addView(Ui.small(this, getString(body)), Ui.wide(this, 8));
        row.addView(words, Ui.fill(this));
        card.addView(row);
        return card;
    }

    // Connect: finding the TV

    private void showFind() {
        showFind(true);
    }

    private void showFind(boolean search) {
        stopFinders();
        back = () -> go(this::showGuide);
        if (Finder.phoneAddress(this) == null) {
            frame(CONNECT, Hero.Mode.PROBLEM, getString(R.string.not_wifi_title), getString(R.string.not_wifi));
            foot(Ui.primary(this, getString(R.string.try_again), v -> go(this::showFind)));
            foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showGuide)));
            return;
        }
        frame(CONNECT, Hero.Mode.SEARCH, getString(R.string.find_title), getString(R.string.find_lead));
        langButton.setVisibility(View.VISIBLE);
        LinearLayout heading = Ui.row(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(14), dp(14));
        sp.setMarginEnd(dp(8));
        heading.addView(Timeline.spinner(this), sp);
        heading.addView(Ui.text(this, getString(R.string.found_heading), Ui.MIST, 13, Ui.medium()));
        add(heading, 22);
        tvList = Ui.column(this);
        add(tvList, 0);
        LinearLayout h = Ui.card(this);
        h.addView(Ui.label(this, getString(R.string.help_title)));
        h.addView(Ui.small(this, getString(R.string.help_body)), Ui.wide(this, 8));
        h.setVisibility(View.GONE);
        help = h;
        add(h, 8);
        add(addressCard(), 20);
        int g = gen;
        // Help only once a search has had time to turn something up.
        main.postDelayed(() -> {
            if (g == gen && help != null && tvList != null && tvList.getChildCount() == 0) {
                help.setAlpha(0f);
                help.setVisibility(View.VISIBLE);
                help.animate().alpha(1f).setDuration(300).start();
            }
        }, 8000);
        if (search) finder.start();
    }

    private void showTvs(List<Finder.Tv> tvs) {
        if (tvList == null || tvList.getParent() == null) return;
        tvList.removeAllViews();
        for (Finder.Tv t : tvs) {
            if (t.classic == 0 && t.tls == 0 && t.pairing == 0) continue;
            tvList.addView(tvCard(t), Ui.wide(this, tvList.getChildCount() == 0 ? 8 : 10));
        }
        if (help != null && tvList.getChildCount() > 0) help.setVisibility(View.GONE);
    }

    private View tvCard(Finder.Tv t) {
        LinearLayout card = Ui.row(this);
        GradientDrawable bg = Ui.round(Ui.LAPIS_800, 18, this);
        bg.setStroke(dp(1), 0x66d8b46a);
        Ui.pressable(card, bg, 0x33d8b46a);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.addView(new TvGlyph(this), new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout words = Ui.column(this);
        words.setPaddingRelative(dp(14), 0, dp(8), 0);
        // The TV's own name (the one shown when casting) over its model and address; without one, "Android TV".
        TextView name = Ui.text(this, t.name != null ? t.name : getString(R.string.tv_found), Ui.PLASTER, 18,
                Ui.semibold());
        name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        words.addView(name);
        String where = t.model != null ? "\u2068" + t.model + "\u2069 · \u2066" + t.host + "\u2069" : t.host;
        TextView host = Ui.small(this, where);
        host.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        words.addView(host, Ui.wide(this, 2));
        if (t.tls > 0) words.addView(door(R.string.badge_wireless, t.tls, true), Ui.wide(this, 6));
        if (t.classic > 0) words.addView(door(R.string.badge_network, t.classic, false), Ui.wide(this, 4));
        if (t.pairing > 0) words.addView(door(R.string.badge_pair, t.pairing, true), Ui.wide(this, 4));
        card.addView(words, Ui.fill(this));

        TextView chevron = Ui.text(this, "›", Ui.GOLD_400, 26, Ui.medium());
        card.addView(chevron);
        card.setOnClickListener(v -> go(() -> connect(t.host, t.tls, t.classic)));
        return card;
    }

    /** "● Wireless debugging · 41234": a way into the TV and its port. */
    private View door(int name, int port, boolean accent) {
        LinearLayout row = Ui.row(this);
        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(accent ? Ui.GOLD_400 : Ui.MIST);
        dot.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
        lp.setMarginEnd(dp(8));
        row.addView(dot, lp);
        // One text, so a narrow card wraps the line whole instead of the port a digit a line.
        row.addView(Ui.text(this, getString(name) + " · \u2066" + number(port) + "\u2069",
                accent ? Ui.GOLD_200 : Ui.MIST, 13, Ui.medium()), Ui.fill(this));
        return row;
    }

    /**
     * The TV's address, typed: "192.168.1.20" checks every port, "192.168.1.20:41234" goes straight there. The
     * last address that worked is filled in, so a second run is one tap.
     */
    private View addressCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.label(this, getString(R.string.manual_title)));
        card.addView(Ui.small(this, getString(R.string.manual_body)), Ui.wide(this, 6));
        LinearLayout row = Ui.row(this);
        EditText field = new EditText(this);
        field.setHint(R.string.manual_hint);
        field.setHintTextColor(0x809fb0cc);
        field.setTextColor(Ui.PLASTER);
        field.setTextSize(16);
        field.setTypeface(Ui.medium());
        field.setSingleLine();
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        field.setBackground(Ui.round(Ui.LAPIS_900, 12, this));
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        Ui.latin(field);
        field.setText(getPreferences(MODE_PRIVATE).getString("last_tv", ""));
        row.addView(field, Ui.fill(this));
        TextView go = Ui.primary(this, getString(R.string.connect), v -> typed(field.getText().toString()));
        go.setMinHeight(dp(46));
        go.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.setMarginStart(dp(10));
        row.addView(go, gp);
        field.setOnEditorActionListener((v, action, e) -> {
            typed(field.getText().toString());
            return true;
        });
        card.addView(row, Ui.wide(this, 12));
        return card;
    }

    private void typed(String text) {
        String s = text.trim().replace(" ", "");
        if (s.isEmpty()) return;
        int colon = s.lastIndexOf(':');
        if (colon > 0) {
            int port;
            try {
                port = Integer.parseInt(s.substring(colon + 1));
            } catch (NumberFormatException e) {
                return;
            }
            String host = s.substring(0, colon);
            if (port == Finder.CLASSIC_PORT) go(() -> connect(host, 0, port));
            else go(() -> connect(host, port, 0));
        } else {
            go(() -> scan(s));
        }
    }

    /** No port given: try 5555 and every port Wireless debugging may use. */
    private void scan(String host) {
        this.host = host;
        stopFinders();
        frame(CONNECT, Hero.Mode.SEARCH, getString(R.string.scanning_title), getString(R.string.scanning, host));
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showFind)));
        back = () -> go(this::showFind);
        int g = gen;
        work.execute(() -> {
            try {
                List<Integer> open = Finder.scanPorts(host);
                if (g != gen) return;
                Adb a = adb();
                for (int port : open) {
                    if (port == Finder.CLASSIC_PORT) continue;
                    // Wireless debugging answers a stranger with "pairing required"; anything else isn't ADB.
                    a.setTimeout(5, TimeUnit.SECONDS);
                    try {
                        a.disconnect();
                        if (a.connect(host, port)) {
                            post(g, this::confirm);
                            return;
                        }
                    } catch (AdbPairingRequiredException e) {
                        post(g, () -> showPair(host, port));
                        return;
                    } catch (Exception notAdb) {
                        // Next port.
                    } finally {
                        a.setTimeout(90, TimeUnit.SECONDS);
                    }
                }
                if (open.contains(Finder.CLASSIC_PORT)) {
                    post(g, () -> connect(host, 0, Finder.CLASSIC_PORT));
                    return;
                }
                fail(g, getString(R.string.scan_none, host));
            } catch (Exception e) {
                fail(g, connectError(e));
            }
        });
    }

    /** Wireless debugging first (no prompt on the TV once paired), then classic with "Allow debugging?". */
    private void connect(String host, int tls, int classic) {
        this.host = host;
        connTls = tls;
        connClassic = classic;
        stopFinders();
        frame(CONNECT, Hero.Mode.SEARCH, getString(R.string.connecting_title), getString(R.string.connecting, host));
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showFind)));
        back = () -> go(this::showFind);
        int g = gen;
        work.execute(() -> {
            Adb a;
            try {
                a = adb();
                a.disconnect();
            } catch (Exception e) {
                fail(g, connectError(e));
                return;
            }
            if (tls > 0) {
                try {
                    if (a.connect(host, tls)) {
                        post(g, this::confirm);
                        return;
                    }
                } catch (AdbPairingRequiredException e) {
                    post(g, () -> showPair(host, tls));
                    return;
                } catch (Exception e) {
                    if (classic == 0) {
                        fail(g, connectError(e));
                        return;
                    }
                }
            }
            if (classic > 0) {
                post(g, this::showAllow);
                try {
                    if (a.connect(host, classic)) {
                        post(g, this::confirm);
                        return;
                    }
                    fail(g, getString(R.string.not_accepted));
                } catch (Exception e) {
                    fail(g, connectError(e));
                }
                return;
            }
            fail(g, getString(R.string.error_connect, host));
        });
    }

    /** Classic network debugging: the TV asks to allow this phone. */
    private void showAllow() {
        frame(CONNECT, Hero.Mode.ALLOW, getString(R.string.allow_title), getString(R.string.allow_lead));
        add(Ui.tvText(this, R.string.tv_allow_q_en, R.string.tv_allow_q_ar), 14);
        LinearLayout card = Ui.card(this);
        card.addView(numbered(1, getString(R.string.allow_1), Ui.tvText(this, R.string.tv_always_en, R.string.tv_always_ar)));
        card.addView(numbered(2, getString(R.string.allow_2), Ui.tvText(this, R.string.tv_allow_en, R.string.tv_allow_ar)),
                Ui.wide(this, 14));
        add(card, 14);
        add(waiting(getString(R.string.waiting)), 18);
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showFind)));
    }

    // Connect: pairing

    /** Pairing for Wireless debugging: the TV shows a six-digit code and its own pairing port. */
    private void showPair(String host, int tlsPort) {
        stopFinders();
        frame(CONNECT, Hero.Mode.PAIR, getString(R.string.pair_title), getString(R.string.pair_lead));
        LinearLayout card = Ui.card(this);
        card.addView(numbered(1, getString(R.string.pair_1),
                Ui.tvText(this, R.string.tv_wireless_path_en, R.string.tv_wireless_path_ar)));
        card.addView(numbered(2, getString(R.string.pair_2), Ui.tvText(this, R.string.tv_pair_en, R.string.tv_pair_ar)),
                Ui.wide(this, 14));
        card.addView(numbered(3, getString(R.string.pair_3), null), Ui.wide(this, 14));
        add(card, 20);

        TextView pairButton = Ui.primary(this, getString(R.string.pair_button), null);
        EditText port = new EditText(this);
        CodeInput code = new CodeInput(this, c -> {
            Ui.enabled(pairButton, c.length() == 6 && port.length() > 0);
            if (c.length() == 6 && port.length() > 0) pairButton.performClick();
        });
        add(code, 20);

        // The pairing port: found by mDNS while the TV's dialog is open, or typed.
        TextView found = Ui.latin(Ui.chip(this, "", true));
        found.setVisibility(View.GONE);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.gravity = Gravity.CENTER_HORIZONTAL;
        fp.topMargin = dp(16);
        content.addView(found, fp);
        View wait = waiting(getString(R.string.pair_port_waiting));
        add(wait, 14);
        port.setHint(R.string.pair_port_hint);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setTextColor(Ui.PLASTER);
        port.setHintTextColor(Ui.MIST);
        port.setTextSize(15);
        port.setTypeface(Ui.medium());
        port.setGravity(Gravity.CENTER);
        port.setBackground(Ui.round(Ui.LAPIS_800, 12, this));
        port.setPadding(dp(12), dp(12), dp(12), dp(12));
        Ui.latin(port);
        add(port, 10);
        port.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) {
                Ui.enabled(pairButton, code.code().length() == 6 && e.length() > 0);
            }
        });
        pairFinder = new Finder(this, tvs -> runOnUiThread(() -> {
            for (Finder.Tv t : tvs) {
                if (t.host.equals(host) && t.pairing > 0) {
                    port.setText(number(t.pairing));
                    port.setVisibility(View.GONE);
                    wait.setVisibility(View.GONE);
                    found.setText("✓  " + getString(R.string.pair_port_found, number(t.pairing)));
                    found.setVisibility(View.VISIBLE);
                }
            }
        }));
        pairFinder.start();

        Ui.enabled(pairButton, false);
        pairButton.setOnClickListener(v -> {
            String c = code.code();
            String p = port.getText().toString().trim();
            if (c.length() != 6 || p.isEmpty()) return;
            int pp = Integer.parseInt(p);
            go(() -> pair(host, pp, c, tlsPort));
        });
        foot(pairButton);
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showFind)));
        back = () -> go(this::showFind);
        code.post(code::focus);
    }

    private void pair(String host, int pairPort, String code, int tlsPort) {
        connTls = tlsPort;
        stopFinders();
        frame(CONNECT, Hero.Mode.PAIR, getString(R.string.pair_title), getString(R.string.pairing));
        back = null;
        int g = gen;
        work.execute(() -> {
            try {
                Adb a = adb();
                a.pair(host, pairPort, code);
                a.disconnect();
                if (a.connect(host, tlsPort)) {
                    post(g, this::confirm);
                } else {
                    fail(g, getString(R.string.error_connect, host));
                }
            } catch (Exception e) {
                fail(g, getString(R.string.pair_failed, message(e)));
            }
        });
    }

    // Install

    private Timeline timeline;
    TextView titleView, leadView;
    /** The carried Awan TV's version ("1.0.0"), and whether every required step went through. */
    String version = "";
    boolean installedOk;
    /** The run only sets up an Awan TV already on the TV (the same version as carried): nothing is sent. */
    private boolean setUpOnly;
    /** What the confirmation's main button does once the TV's version is known: install, or set up only. */
    private Runnable confirmGo;
    /** The TV has the very version carried (showVersions): Install it again is offered beside Set up again. */
    private boolean sameVersion;
    /** The address of the TV this run is working on, remembered for next time. */
    private String host;

    /** The TV's own name (Settings' device name), else its model, else its address: for the confirmation and the end. */
    private String tvName;
    /** The TV's serial (ro.serialno) and the ports this run reached it on, for My TVs. */
    private String tvSerial;
    private int connTls, connClassic;

    /**
     * Connected: which TV this is, from the TV itself, and a choice before anything is sent, so a TV tapped by mistake
     * (scrolling the list) is never installed on. Cancel goes back to the list.
     */
    private void confirm() {
        stopFinders();
        tvName = host;
        frame(CONNECT, Hero.Mode.TV, getString(R.string.confirm_title), getString(R.string.confirm_lead));
        LinearLayout card = Ui.row(this);
        GradientDrawable bg = Ui.round(Ui.LAPIS_800, 18, this);
        bg.setStroke(dp(1), 0x66d8b46a);
        card.setBackground(bg);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.addView(new TvGlyph(this), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout words = Ui.column(this);
        words.setPaddingRelative(dp(14), 0, 0, 0);
        TextView name = Ui.text(this, host, Ui.PLASTER, 18, Ui.semibold());
        name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        TextView model = Ui.small(this, "");
        model.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        model.setVisibility(View.GONE);
        TextView where = Ui.small(this, "\u2066" + host + "\u2069");
        where.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        // Awan TV on the TV, read with the rest: none, older, the same, or newer than the one carried.
        TextView state = Ui.chip(this, getString(R.string.confirm_reading), false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        words.addView(name);
        words.addView(model, Ui.wide(this, 3));
        words.addView(where, Ui.wide(this, 2));
        words.addView(state, slp);
        card.addView(words, Ui.fill(this));
        add(card, 18);
        confirmGo = this::install;
        TextView main = Ui.primary(this, getString(R.string.confirm_install), v -> go(confirmGo));
        TextView other = Ui.secondary(this, getString(R.string.cancel), v -> go(this::showFind));
        // The same version on the TV: set up again (main), or send it all the same (a damaged install, a test build).
        TextView again = Ui.secondary(this, getString(R.string.reinstall_button), v -> go(this::install));
        again.setVisibility(View.GONE);
        // Nothing to press until the version is known (a second or two): an install must not race the answer.
        Ui.enabled(main, false);
        foot(main);
        foot(again);
        foot(other);
        back = () -> go(this::showFind);
        int g = gen;
        work.execute(() -> {
            String carried = "";
            String onTv = null;
            try {
                carried = Setup.version(this, Setup.bundled(this));
            } catch (Exception e) {
                // Read again at the install, which says what went wrong.
            }
            try {
                String[] lines = adb().shell("settings get global device_name; echo; getprop ro.product.model; echo; "
                        + "getprop ro.build.version.release; echo; dumpsys package " + Setup.PACKAGE
                        + " 2>/dev/null | grep -m1 versionName; echo; getprop ro.serialno").out.split("\n", -1);
                String device = clean(lines, 0), made = clean(lines, 2), android = clean(lines, 4);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("versionName=(\\S+)").matcher(clean(lines, 6));
                onTv = m.find() ? m.group(1) : "";
                // Connected, so this TV trusts this phone: one of My TVs from now on (kept on the phone only).
                // The last line (the version's line is missing on a TV without Awan TV, so not by its index).
                String serial = "";
                for (int i = lines.length - 1; i >= 0 && serial.isEmpty(); i--) serial = clean(lines, i);
                if (serial.contains("versionName")) serial = "";
                tvSerial = serial.isEmpty() ? null : serial;
                MyTvs.remember(this, serial, device, made, host, connClassic, connTls, onTv);
                post(g, () -> {
                    if (!device.isEmpty()) tvName = device;
                    else if (!made.isEmpty()) tvName = made;
                    name.setText(tvName);
                    String about = made.isEmpty() || made.equals(tvName) ? "" : "\u2068" + made + "\u2069";
                    if (!android.isEmpty()) about += (about.isEmpty() ? "" : " · ") + "Android " + android;
                    model.setText(about);
                    model.setVisibility(about.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                // The address alone still says which TV it is; the install itself finds out the rest.
            }
            String have = onTv, ours = carried;
            post(g, () -> {
                showVersions(have, ours, state, main, other);
                if (sameVersion) again.setVisibility(View.VISIBLE);
            });
        });
    }

    /**
     * The confirmation once the TV answered: no Awan TV (install), an older one (update), the same (set up only: the
     * service, permissions and screensaver put right, nothing sent) or a newer one (nothing to do here but get a newer
     * installer). Unknown, it offers the install as before and the install handles what it finds.
     */
    private void showVersions(String onTv, String carried, TextView state, TextView main, TextView other) {
        Ui.enabled(main, true);
        sameVersion = false;
        if (onTv == null || carried.isEmpty()) {
            state.setVisibility(View.GONE);
            return;
        }
        if (onTv.isEmpty()) {
            state.setText(getString(R.string.confirm_none));
            return;
        }
        int c = Setup.compare(onTv, carried);
        String tv = "\u2066" + onTv + "\u2069", ours = "\u2066" + carried + "\u2069";
        if (c < 0) {
            titleView.setText(R.string.update_title);
            leadView.setText(getString(R.string.update_lead, tv, ours));
            state.setText(getString(R.string.update_chip, tv, ours));
            state.setTextColor(Ui.GOLD_400);
            state.setBackground(Ui.round(Ui.GOLD_SOFT, 99, this));
            main.setText(R.string.update_button);
        } else if (c == 0) {
            titleView.setText(R.string.same_title);
            leadView.setText(getString(R.string.same_lead, tv));
            state.setText(getString(R.string.same_chip, tv));
            main.setText(R.string.setup_button);
            confirmGo = this::setUpOnly;
            sameVersion = true;
        } else {
            titleView.setText(R.string.newer_title);
            leadView.setText(getString(R.string.newer_lead, tv, ours));
            Ui.links(leadView);
            state.setText(getString(R.string.newer_chip, tv));
            state.setTextColor(Ui.CORAL);
            state.setBackground(Ui.round(Ui.CORAL_SOFT, 99, this));
            main.setVisibility(View.GONE);
            other.setText(R.string.back);
        }
    }

    private static String clean(String[] lines, int i) {
        String s = i < lines.length ? lines[i].trim() : "";
        return s.equals("null") ? "" : s;
    }

    private void install() {
        setUpOnly = false;
        stopFinders();
        if (host != null) getPreferences(MODE_PRIVATE).edit().putString("last_tv", host).apply();
        frame(INSTALL, Hero.Mode.PROGRESS, getString(R.string.install_title), getString(R.string.install_lead));
        back = null;
        int g = gen;
        work.execute(() -> {
            try {
                Adb a = adb();
                Adb.Result sdk = a.shell("getprop ro.build.version.sdk");
                int api = 0;
                try { api = Integer.parseInt(sdk.out.trim()); } catch (NumberFormatException ignored) { }
                if (api > 0 && api < 26) {
                    fail(g, getString(R.string.tv_too_old));
                    return;
                }
                File apk = Setup.bundled(this);
                version = Setup.version(this, apk);
                post(g, () -> {
                    if (!version.isEmpty()) leadView.setText(getString(R.string.install_lead_version, version));
                    timeline = timeline();
                    add(timeline, 20);
                    add(Timeline.legend(this), 12);
                });
                // Sent every time the install is chosen; the same version is set up only (setUpOnly) from the
                // confirmation, where its version was read.
                send(g, a, apk, false);
            } catch (Exception e) {
                fail(g, getString(R.string.error_install, message(e)));
            }
        });
    }

    /** The same version is on the TV: its setup steps only, as after an install, with nothing sent. */
    private void setUpOnly() {
        setUpOnly = true;
        stopFinders();
        if (host != null) getPreferences(MODE_PRIVATE).edit().putString("last_tv", host).apply();
        frame(INSTALL, Hero.Mode.PROGRESS, getString(R.string.setup_title), getString(R.string.setup_lead));
        back = null;
        timeline = timeline();
        add(timeline, 20);
        add(Timeline.legend(this), 12);
        timeline.set(0, Timeline.State.CHECKED);
        timeline.note(0, getString(R.string.state_on_tv));
        int g = gen;
        work.execute(() -> {
            try {
                runSteps(g, adb());
            } catch (Exception e) {
                fail(g, getString(R.string.error_install, message(e)));
            }
        });
    }

    private Timeline timeline() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.t_send));
        boolean ar = Ui.arabic(this);
        for (Setup.Step s : Setup.STEPS) labels.add(s.label(ar));
        return new Timeline(this, labels.toArray(new String[0]));
    }

    /** On the worker thread: the APK into the TV, then the setup steps. */
    private void send(int g, Adb a, File apk, boolean replacing) throws IOException, InterruptedException {
        post(g, () -> timeline.set(0, Timeline.State.WORKING));
        if (replacing) a.shell("pm uninstall " + Setup.PACKAGE);
        long size = apk.length();
        String result = a.install(apk, f -> post(g, () -> {
            hero.progress(0.8f * f);
            timeline.note(0, getString(R.string.mb_of, mb((long) (f * size)), mb(size)));
        }));
        if (result.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") && !replacing) {
            // Awan TV signed with another key is on the TV (a self-built or older copy); only removing it lets this in.
            post(g, () -> askReplace(g, a, apk));
            return;
        }
        if (result.contains("INSTALL_FAILED_VERSION_DOWNGRADE")) {
            fail(g, getString(R.string.newer_on_tv));
            return;
        }
        if (!result.startsWith("Success")) {
            fail(g, getString(R.string.install_failed, result));
            return;
        }
        // An update force-stops Awan TV, and Android may handle that a moment after the install answered, taking its
        // service out of the list (a Google TV Streamer did, after the setup had listed it): the setup waits first.
        Thread.sleep(5000);
        // Checked: the TV now reports the version just sent.
        String now = versionOnTv(a);
        post(g, () -> timeline.set(0, !version.isEmpty() && version.equals(now) ? Timeline.State.CHECKED : Timeline.State.DONE));
        runSteps(g, a);
    }

    /** Awan TV's version name on the TV, or null. */
    private static String versionOnTv(Adb a) throws IOException, InterruptedException {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("versionName=(\\S+)")
                .matcher(a.shell("dumpsys package " + Setup.PACKAGE + " 2>/dev/null | grep -m1 versionName").out);
        return m.find() ? m.group(1) : null;
    }

    /**
     * On the worker thread: each setup step from init to checked. A step whose check already passes is checked
     * as it stands; otherwise its command runs (done) and the check runs again (checked, or failed / not on this TV).
     */
    private void runSteps(int g, Adb a) throws IOException, InterruptedException {
        boolean allRequired = true;
        List<Setup.Step> steps = Setup.STEPS;
        for (int i = 0; i < steps.size(); i++) {
            Setup.Step s = steps.get(i);
            int row = i + 1;
            float at = 0.8f + 0.2f * (i + 1) / steps.size();
            post(g, () -> timeline.set(row, Timeline.State.WORKING));
            Timeline.State end;
            if (s.check != null && a.shell(s.check).ok()) {
                end = Timeline.State.CHECKED;
                post(g, () -> timeline.note(row, getString(R.string.state_was_set)));
            } else {
                boolean ran = a.shell(s.command).ok();
                if (s.check == null) {
                    end = ran ? Timeline.State.DONE : s.required ? Timeline.State.FAILED : Timeline.State.SKIPPED;
                } else {
                    post(g, () -> timeline.set(row, Timeline.State.DONE));
                    end = a.shell(s.check).ok() ? Timeline.State.CHECKED
                            : s.required ? Timeline.State.FAILED : Timeline.State.SKIPPED;
                }
            }
            if (end == Timeline.State.FAILED) allRequired = false;
            Timeline.State shown = end;
            post(g, () -> {
                boolean keepNote = shown == Timeline.State.CHECKED && timeline.hasNote(row);
                timeline.set(row, shown);
                if (keepNote) timeline.note(row, getString(R.string.state_was_set));
                if (shown == Timeline.State.SKIPPED) timeline.note(row, getString(R.string.state_not_here));
                if (shown == Timeline.State.FAILED) timeline.note(row, getString(R.string.state_failed));
                hero.progress(at);
            });
        }
        installedOk = allRequired;
        if (!allRequired) {
            // A required step is red: stay on the list until the user retries it or goes on anyway.
            post(g, () -> {
                hero.set(Hero.Mode.PROBLEM);
                titleView.setText(R.string.done_partly_title);
                leadView.setText(R.string.steps_failed_lead);
                foot(Ui.primary(this, getString(R.string.try_again), v -> go(setUpOnly ? this::setUpOnly : this::install)));
                foot(Ui.secondary(this, getString(R.string.continue_), v -> go(this::showDone)));
            });
            return;
        }
        // A moment on the finished list, then the end.
        Thread.sleep(900);
        post(g, this::showDone);
    }

    private void askReplace(int g, Adb a, File apk) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.old_signature_title)
                .setMessage(R.string.old_signature_body)
                .setPositiveButton(R.string.remove_and_install, (d, w) -> work.execute(() -> {
                    try {
                        send(g, a, apk, true);
                    } catch (Exception e) {
                        fail(g, getString(R.string.error_install, message(e)));
                    }
                }))
                .setNegativeButton(R.string.cancel, (d, w) -> go(this::showGuide))
                .setCancelable(false)
                .show();
    }

    /** The end: what to do on the TV now, and the tip about debugging. */
    void showDone() {
        if (installedOk && tvSerial != null && !version.isEmpty()) MyTvs.version(this, tvSerial, version);
        Timeline steps = timeline;
        if (steps != null && steps.getParent() != null) ((ViewGroup) steps.getParent()).removeView(steps);
        boolean ok = installedOk;
        String on = tvName != null ? tvName : host != null ? host : "";
        String lead = !ok ? getString(R.string.done_partly_lead)
                : setUpOnly && !version.isEmpty() ? getString(R.string.done_lead_setup, version, on)
                : version.isEmpty() ? getString(R.string.done_lead_plain, on) : getString(R.string.done_lead, version, on);
        frame(DONE, ok ? Hero.Mode.DONE : Hero.Mode.PROBLEM,
                getString(ok ? R.string.done_title : R.string.done_partly_title), lead);
        if (!ok && steps != null) add(steps, 20);
        // What to do next on the TV: for a first install or an update; after setting up again it is already done.
        if (!setUpOnly) {
            LinearLayout next = Ui.card(this);
            next.addView(numbered(1, getString(R.string.next_home), null));
            next.addView(numbered(2, getString(R.string.next_city), null), Ui.wide(this, 14));
            add(next, 20);
        }
        LinearLayout tip = Ui.card(this);
        tip.addView(Ui.label(this, getString(R.string.tip_title)));
        tip.addView(Ui.small(this, getString(R.string.tip_body)), Ui.wide(this, 8));
        add(tip, setUpOnly ? 20 : 12);
        TextView site = Ui.small(this, getString(R.string.site_line));
        site.setGravity(Gravity.CENTER);
        add(site, 16);
        // Done, or Back: the start again, with the TV let go, ready for another TV or to leave like any app.
        foot(Ui.primary(this, getString(R.string.finish), v -> go(this::startAgain)));
        back = () -> go(this::startAgain);
    }

    private void startAgain() {
        Adb a = adb;
        if (a != null) new Thread(() -> { try { a.disconnect(); } catch (Throwable ignored) { } }).start();
        tvName = null;
        showGuide();
    }

    /** The connection, for a last command after the work is done; null if there is none. */
    Adb adbOrNull() {
        return adb;
    }

    // Problems

    private void fail(int g, String message) {
        post(g, () -> {
            stopFinders();
            frame(stage, Hero.Mode.PROBLEM, getString(R.string.problem_title), message);
            LinearLayout card = Ui.card(this);
            card.addView(Ui.label(this, getString(R.string.check_title)));
            card.addView(Ui.small(this, getString(R.string.check_body)), Ui.wide(this, 8));
            add(card, 20);
            foot(Ui.primary(this, getString(R.string.try_again), v -> go(this::showFind)));
            foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showGuide)));
            back = () -> go(this::showGuide);
        });
    }

    /**
     * Why the TV could not be reached, with what to do: no answer from its debugging port (a TV that stopped
     * listening until unplugged, as a TCL on Android 11 did), refused (Wireless debugging's port changed), or no way to
     * it on the network; else the plain reason with the same first advice.
     */
    private String connectError(Exception e) {
        for (Throwable x = e; x != null; x = x.getCause()) {
            String m = String.valueOf(x.getMessage());
            if (x instanceof java.net.SocketTimeoutException || m.contains("ETIMEDOUT") || m.contains("timed out")) {
                return getString(R.string.error_timeout);
            }
            if (x instanceof java.net.NoRouteToHostException || m.contains("EHOSTUNREACH") || m.contains("ENETUNREACH")) {
                return getString(R.string.error_unreachable);
            }
            if (x instanceof java.net.ConnectException || m.contains("ECONNREFUSED")) {
                return getString(R.string.error_refused);
            }
        }
        return getString(R.string.error_connect, message(e));
    }

    private static String message(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    // Debug builds only: one screen with sample data, for screenshots and checking layouts without a TV.
    // adb shell am start -n app.awanpro.tv.installer/.MainActivity --es screen find|about|licences|mytvs|allow|pair|confirm|install|done|partly|problem

    /** For the sample screens: the installer's own version, which is always the Awan TV version it carries. */
    private String ownVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private void showDemo(String screen) {
        switch (screen) {
            case "find": {
                showFind(false);
                Finder.Tv wireless = new Finder.Tv("192.168.1.20");
                wireless.name = "Living Room TV";
                wireless.model = "Google TV Streamer";
                wireless.tls = 41234;
                wireless.pairing = 37019;
                Finder.Tv classic = new Finder.Tv("192.168.1.30");
                classic.classic = Finder.CLASSIC_PORT;
                if (tvList == null) {
                    tvList = Ui.column(this);
                    content.addView(tvList);
                }
                showTvs(Arrays.asList(wireless, classic));
                break;
            }
            case "about":
                showAbout();
                break;
            case "mytvs":
                showMyTvs();
                break;
            case "licences":
                showLicences();
                break;
            case "allow":
                showAllow();
                back = () -> go(this::showFind);
                break;
            case "pair":
                showPair("192.168.1.20", 41234);
                break;
            case "install": {
                frame(INSTALL, Hero.Mode.PROGRESS, getString(R.string.install_title),
                        getString(R.string.install_lead_version, ownVersion()));
                timeline = timeline();
                add(timeline, 20);
                add(Timeline.legend(this), 12);
                timeline.set(0, Timeline.State.CHECKED);
                timeline.set(1, Timeline.State.DONE);
                timeline.set(2, Timeline.State.CHECKED);
                timeline.note(2, getString(R.string.state_was_set));
                timeline.set(3, Timeline.State.SKIPPED);
                timeline.note(3, getString(R.string.state_not_here));
                timeline.set(4, Timeline.State.WORKING);
                hero.progress(0.9f);
                break;
            }
            case "confirm":
                host = "192.168.1.20";
                confirm();
                break;
            case "done":
            case "partly": {
                installedOk = screen.equals("done");
                version = ownVersion();
                tvName = "Living Room TV";
                timeline = timeline();
                for (int i = 0; i < Setup.STEPS.size() + 1; i++) timeline.set(i, Timeline.State.CHECKED);
                if (!installedOk) timeline.set(5, Timeline.State.FAILED);
                showDone();
                break;
            }
            case "problem":
                fail(gen, getString(R.string.error_connect, "192.168.1.20"));
                break;
            default:
                showGuide();
        }
    }

    // The frame

    /** Moves to another screen on the user's word: workers still running for the old one are ignored. */
    void go(Runnable screen) {
        gen++;
        screen.run();
    }

    /** Runs on the main thread if the user is still on the screen that started the work. */
    void post(int g, Runnable r) {
        runOnUiThread(() -> {
            if (g == gen) r.run();
        });
    }

    void frame(int stageNow, Hero.Mode mode, String title, String lead) {
        stage = stageNow;
        // Before connecting only: changing the language starts the screen again, which would cut a connection or
        // an install in the middle. showFind turns it on again for its own screen.
        langButton.setVisibility(stageNow == PREPARE ? View.VISIBLE : View.INVISIBLE);
        tvList = null;
        help = null;
        content.removeAllViews();
        footer.removeAllViews();
        footer.setVisibility(View.GONE);
        stages(stageNow);
        stages.setVisibility(View.VISIBLE);
        hero.setVisibility(View.VISIBLE);
        hero.set(mode);
        // Only the guide makes the version a button to About; each screen sets its own slot and button.
        versionOpensAbout(false);
        updateSlot = null;
        findButton = null;
        myList = null;
        content.addView(hero, Ui.wide(this, 8));
        titleView = Ui.title(this, title);
        content.addView(titleView, Ui.wide(this, 18));
        leadView = Ui.lead(this, lead);
        content.addView(leadView, Ui.wide(this, 10));
        scroll.scrollTo(0, 0);
        content.setAlpha(0f);
        content.setTranslationY(dp(8));
        content.animate().alpha(1f).translationY(0).setDuration(220).start();
    }

    void add(View v, int topDp) {
        content.addView(v, Ui.wide(this, topDp));
    }

    void foot(View v) {
        footer.setVisibility(View.VISIBLE);
        footer.addView(v, Ui.wide(this, footer.getChildCount() == 0 ? 0 : 10));
    }

    /** Prepare · Connect · Install: done in plaster, now in gold, to come in lapis. */
    private void stages(int now) {
        stages.removeAllViews();
        int[] names = {R.string.stage_prepare, R.string.stage_connect, R.string.stage_install};
        for (int i = 0; i < names.length; i++) {
            LinearLayout s = Ui.column(this);
            View bar = new View(this);
            bar.setBackground(Ui.round(i < now ? Ui.PLASTER : i == now ? Ui.GOLD_400 : Ui.LAPIS_700, 2, this));
            s.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
            TextView t = Ui.text(this, number(i + 1) + "  " + getString(names[i]),
                    i < now ? Ui.PLASTER : i == now ? Ui.GOLD_400 : Ui.MIST, 12, i == now ? Ui.semibold() : Ui.medium());
            s.addView(t, Ui.wide(this, 8));
            LinearLayout.LayoutParams lp = Ui.fill(this);
            if (i > 0) lp.setMarginStart(dp(8));
            stages.addView(s, lp);
        }
    }

    /** A numbered line, with what to find on the TV under it (in both languages) if there is one. */
    private View numbered(int n, String text, View tv) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        TextView b = Ui.badge(this, number(n));
        row.addView(b);
        LinearLayout words = Ui.column(this);
        words.setPaddingRelative(dp(12), dp(4), 0, 0);
        words.addView(Ui.text(this, text, Ui.PLASTER, 15, Ui.medium()));
        if (tv != null) words.addView(tv, Ui.wide(this, 8));
        row.addView(words, Ui.fill(this));
        return row;
    }

    /** A small spinning mark and a line of status, centred. */
    View waiting(String text) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), dp(18));
        lp.setMarginEnd(dp(10));
        row.addView(Timeline.spinner(this), lp);
        row.addView(Ui.small(this, text));
        return row;
    }

    private void stopFinders() {
        if (myFinder != null) myFinder.stop();
        myFinder = null;
        finder.stop();
        if (pairFinder != null) pairFinder.stop();
        pairFinder = null;
    }

    private synchronized Adb adb() throws Exception {
        if (adb == null) adb = new Adb(this);
        return adb;
    }

    /** Latin digits whatever the language, to match what the TV shows. */
    private static String number(long n) {
        return String.format(Locale.US, "%d", n);
    }

    private static String mb(long bytes) {
        return String.format(Locale.US, "%.1f", bytes / 1_048_576f);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    /** A TV outline with the small arch on it, for the found-TV cards. */
    private static final class TvGlyph extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        TvGlyph(Context c) {
            super(c);
            setBackground(Ui.round(Ui.LAPIS_700, 12, c));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStrokeWidth(w * 0.05f);
            p.setColor(Ui.PLASTER);
            r.set(w * 0.2f, h * 0.24f, w * 0.8f, h * 0.66f);
            c.drawRoundRect(r, w * 0.05f, w * 0.05f, p);
            c.drawLine(w * 0.38f, h * 0.78f, w * 0.62f, h * 0.78f, p);
            p.setColor(Ui.GOLD_400);
            android.graphics.Path a = new android.graphics.Path();
            a.moveTo(w * 0.44f, h * 0.58f);
            a.lineTo(w * 0.44f, h * 0.42f);
            a.quadTo(w * 0.44f, h * 0.33f, w * 0.5f, h * 0.31f);
            a.quadTo(w * 0.56f, h * 0.33f, w * 0.56f, h * 0.42f);
            a.lineTo(w * 0.56f, h * 0.58f);
            c.drawPath(a, p);
        }
    }
}
