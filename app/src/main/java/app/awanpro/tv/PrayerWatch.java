package app.awanpro.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

/**
 * Watches the clock from the remote buttons service, which the system keeps running whatever app is in front:
 * the reminder before each prayer (Home's own card, or a banner over other apps) and the call screen when the
 * time begins. The banner is an accessibility overlay, so it needs no overlay permission; it never takes focus
 * or touches and hides after 12 s (design/afnon-mihrab/design/ApproachVideo.dc.html).
 */
final class PrayerWatch {
    private static final long CHECK_MS = 15_000;
    private static final int BANNER_SECONDS = 12;

    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /**
     * "prayer index:time" of the last reminder, call and spoken announcement, so each happens once. Saved, because
     * the system restarts this service (low memory, TCL's background manager) and a fresh one would repeat them.
     */
    private String reminded, called, announced;
    private View banner;

    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            check();
            handler.postDelayed(this, CHECK_MS);
        }
    };

    PrayerWatch(AccessibilityService service) {
        this.service = service;
        reminded = Prefs.of(service).getString("reminded", "");
        called = Prefs.of(service).getString("called", "");
        announced = Prefs.of(service).getString("announced", "");
    }

    void start() {
        handler.removeCallbacks(check);
        handler.post(check);
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
        hideBanner();
        hideCounter();
        hideIdentify();
    }

    private void check() {
        PowerManager pm = service.getSystemService(PowerManager.class);
        Date now = new Date();
        boolean asleep = pm != null && !pm.isInteractive();
        // Asleep: nothing to show, unless the radio plays with the screen off (TCL Audio Only); then the call
        // still comes, waking the screen.
        if (asleep && !Radio.on()) {
            hideCounter();
            return;
        }
        PrayerDay day = new PrayerDay(service, now);
        checkTimes(day, now, asleep);
        updateCounter(day, now, asleep);
    }

    private void checkTimes(PrayerDay day, Date now, boolean asleep) {

        int call = Reminders.dueCall(service, day, now);
        if (call >= 0) {
            String key = call + ":" + day.times[call].getTime();
            // While Afnon's screensaver runs it wakes itself for the call; open the screen once it has gone.
            if (!key.equals(called) && !AwanDream.running) {
                called = key;
                Prefs.of(service).edit().putString("called", key).apply();
                openCall(call, day.times[call]);
            }
            return;
        }
        // No call for a prayer whose adhan is off: just say that its time has begun (also with the screen off).
        int quiet = Reminders.dueAnnounce(service, day, now);
        if (quiet >= 0) {
            String key = quiet + ":" + day.times[quiet].getTime();
            if (!key.equals(announced)) {
                announced = key;
                Prefs.of(service).edit().putString("announced", key).apply();
                Announce.now(service, quiet, day.friday);
            }
        }
        if (asleep) return;
        if (Reminders.dueReminder(service, day, now)) {
            String key = day.nextIndex + ":" + day.nextTime.getTime();
            if (key.equals(reminded) || AwanDream.running) return;
            reminded = key;
            Prefs.of(service).edit().putString("reminded", key).apply();
            if (Reminders.home != null) Reminders.home.showReminder();
            else showBanner(day, now);
            if (Prefs.remindVoice(service)) Announce.before(service, day.nextIndex, day.friday);
        }
    }

    void openCall(int prayer, Date time) {
        hideBanner();
        wakeScreen();
        Intent i = new Intent(service, NowActivity.class)
            .putExtra("prayer", prayer)
            .putExtra("time", time.getTime())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            service.startActivity(i);
        } catch (RuntimeException ignored) {
            // Blocked on this firmware; the reminder and Home still show the time.
        }
    }

    /** Turns the screen back on (from TCL Audio Only or sleep) so the call can be seen; a no-op when it is on. */
    private void wakeScreen() {
        PowerManager pm = service.getSystemService(PowerManager.class);
        if (pm == null || pm.isInteractive()) return;
        try {
            @SuppressWarnings("deprecation")
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "afnon:call");
            wl.acquire(5000);
        } catch (RuntimeException ignored) {
            // No wake lock; the call opens when the screen next comes on.
        }
    }

    // ---------------------------------------------------------------- banner

    void showBanner(PrayerDay day, Date now) {
        hideBanner();
        hideCounter();  // The reminder takes the corner; the countdown comes back after it
        Ui.init(service);
        Text t = Screen.loadText(service);
        Tokens.apply(service);  // The banner in the chosen theme too
        boolean h24 = Prefs.clock24(service);
        Typeface ui = service.getResources().getFont(R.font.alexandria_regular);
        Typeface display = service.getResources().getFont(t.arabicScript() ? R.font.amiri_bold : R.font.amiri_regular);

        LinearLayout card = new LinearLayout(service);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(t.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        card.setPadding(Ui.ipx(28), Ui.ipx(24), Ui.ipx(28), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tokens.LAPIS_900);
        bg.setCornerRadius(Ui.px(24));
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setClipToPadding(false);

        LinearLayout row = new LinearLayout(service);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Icon mihrab = new Icon(service, Icon.MIHRAB, 1.2f);
        mihrab.setColor(Tokens.GOLD_400);
        row.addView(mihrab, new LinearLayout.LayoutParams(Ui.ipx(56), Ui.ipx(72)));
        LinearLayout words = new LinearLayout(service);
        words.setOrientation(LinearLayout.VERTICAL);
        String[] names = t.prayerNames();
        if (day.friday) names[PrayerDay.DHUHR] = t.jumuah();
        long minutes = Reminders.minutesLeft(day, now);
        TextView label = text(ui, 20, Tokens.MIST_400, t.apLabel());
        if (!t.arabicScript()) label.setLetterSpacing(0.2f);
        TextView title = text(display, 44, Tokens.PLASTER_100,
            t.apTitle(names[day.nextIndex], minutes, Screen.duration(Screen.secondsUntil(day.nextTime, now), false)));
        TextView sub = text(ui, 22, Tokens.MIST_400, t.bannerSub(Screen.clock(t, day.nextTime, h24, 1f), BANNER_SECONDS));
        words.addView(label);
        words.addView(title);
        words.addView(sub);
        Screen.lineHeight(title, 1.1f);
        ((LinearLayout.LayoutParams) sub.getLayoutParams()).topMargin = Ui.ipx(4);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -2, 1);
        wlp.setMarginStart(Ui.ipx(20));
        row.addView(words, wlp);
        card.addView(row);

        // The time left before it hides, emptying toward the start side.
        FrameLayout track = new FrameLayout(service);
        track.setBackgroundColor(Tokens.BANNER_TRACK);
        View fill = new View(service);
        fill.setBackgroundColor(Tokens.GOLD_400);
        track.addView(fill, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, Ui.ipx(6));
        tlp.topMargin = Ui.ipx(18);
        tlp.setMarginStart(-Ui.ipx(28));
        tlp.setMarginEnd(-Ui.ipx(28));
        card.addView(track, tlp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            Ui.ipx(640), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        // Top end: the left in Arabic, the right in English.
        lp.gravity = Gravity.TOP | (t.rtl() ? Gravity.LEFT : Gravity.RIGHT);
        lp.x = Ui.ipx(96);
        lp.y = Ui.ipx(56);
        WindowManager wm = service.getSystemService(WindowManager.class);
        try {
            wm.addView(card, lp);
        } catch (RuntimeException e) {
            return;
        }
        banner = card;
        fill.post(() -> {
            fill.setPivotX(t.rtl() ? fill.getWidth() : 0);
            fill.animate().scaleX(0f).setDuration(BANNER_SECONDS * 1000L).setInterpolator(null).start();
        });
        handler.postDelayed(this::hideBanner, BANNER_SECONDS * 1000L);
    }

    void hideBanner() {
        if (banner == null) return;
        try {
            service.getSystemService(WindowManager.class).removeView(banner);
        } catch (RuntimeException ignored) {
            // Already gone with the service.
        }
        banner = null;
    }

    // ---------------------------------------------------------------- iqamah countdown

    /**
     * From the call until the iqamah, a small card in the banner's corner over other apps
     * (Settings > Iqamah > Iqamah countdown over apps, on by default since 1.14.0). Not over Awan TV's own screens, which show
     * the iqamah themselves, the call screen or the reminder. Updated with the 15 s check; no timer of its own.
     */
    private View counter;
    private TextView counterTitle;
    /** "prayer:iqamah time" the shown card is for. */
    private String counterKey;
    /**
     * The card's first times (a prayer each, however often it hides and comes back for it) carry where it is turned
     * off: five, a whole day, so a few missed by chance still leave some seen. Counted in "counter_hints" (LOCAL), the
     * last prayer counted in "counter_hint_at".
     */
    static final int HINT_TIMES = 5;

    /** Whether this prayer's card says where it is turned off; counts the prayer the first time it is shown. */
    private boolean hintFor(String key) {
        android.content.SharedPreferences p = Prefs.of(service);
        int seen = p.getInt("counter_hints", 0);
        if (!key.equals(p.getString("counter_hint_at", ""))) {
            if (seen >= HINT_TIMES) return false;
            seen++;
            p.edit().putInt("counter_hints", seen).putString("counter_hint_at", key).apply();
        }
        return seen <= HINT_TIMES;
    }

    /** Settings, Home, the call or the screensaver came or went: show or hide the countdown now. */
    void refreshCounter() {
        handler.post(() -> {
            PowerManager pm = service.getSystemService(PowerManager.class);
            Date now = new Date();
            updateCounter(new PrayerDay(service, now), now, pm != null && !pm.isInteractive());
        });
    }

    private void updateCounter(PrayerDay day, Date now, boolean asleep) {
        boolean wanted = !asleep && banner == null && Prefs.iqamaCounter(service) && Iqama.mode(service) != Iqama.OFF
            && !NowActivity.showing && !SaverActivity.showing && !AwanDream.running && Reminders.home == null
            // The call screen is opening: wait for it, then for it to go.
            && Reminders.dueCall(service, day, now) < 0;
        int prayer = wanted ? waitingFor(day, now) : -1;
        if (prayer < 0) {
            hideCounter();
            return;
        }
        Date at = Iqama.at(service, day, prayer);
        String key = prayer + ":" + at.getTime();
        Text t = Screen.loadText(service);
        if (counter == null || !key.equals(counterKey)) showCounter(t, day, prayer, at);
        if (counterTitle != null) counterTitle.setText(t.iqamaInMinutes((Screen.secondsUntil(at, now) + 59) / 60));
    }

    /** The prayer whose adhan has passed and whose iqamah has not come yet; -1 if none. */
    private int waitingFor(PrayerDay day, Date now) {
        for (int p : Iqama.PRAYERS) {
            if (day.times[p].after(now)) continue;
            Date at = Iqama.at(service, day, p);
            if (at != null && at.after(now)) return p;
        }
        return -1;
    }

    private void showCounter(Text t, PrayerDay day, int prayer, Date at) {
        hideCounter();
        Ui.init(service);
        Tokens.apply(service);
        Typeface ui = service.getResources().getFont(R.font.alexandria_regular);

        LinearLayout card = new LinearLayout(service);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutDirection(t.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        card.setPadding(Ui.ipx(26), Ui.ipx(20), Ui.ipx(26), Ui.ipx(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tokens.LAPIS_900);
        bg.setCornerRadius(Ui.px(22));
        bg.setStroke(Ui.ipx(2), Tokens.GOLD_400);
        card.setBackground(bg);

        Icon mihrab = new Icon(service, Icon.MIHRAB, 1.2f);
        mihrab.setColor(Tokens.GOLD_400);
        card.addView(mihrab, new LinearLayout.LayoutParams(Ui.ipx(40), Ui.ipx(52)));
        LinearLayout words = new LinearLayout(service);
        words.setOrientation(LinearLayout.VERTICAL);
        String[] names = t.prayerNames();
        String place = t.cityName == null || t.cityName.isEmpty() ? "" : t.sep() + t.cityName;
        words.addView(text(ui, 20, Tokens.MIST_400, names[prayer] + place));
        counterTitle = text(ui, 38, Tokens.PLASTER_100, "");
        counterTitle.setSingleLine(true);
        words.addView(counterTitle);
        Screen.lineHeight(counterTitle, 1.2f);
        if (hintFor(prayer + ":" + at.getTime())) {
            TextView off = text(ui, 18, Tokens.MIST_400, t.iqamaCounterOff());
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
            olp.topMargin = Ui.ipx(6);
            words.addView(off, olp);
        }
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
        wlp.setMarginStart(Ui.ipx(18));
        card.addView(words, wlp);
        TextView when = text(ui, 24, Tokens.GOLD_400, Screen.clock(t, at, Prefs.clock24(service), 1f));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.setMarginStart(Ui.ipx(36));
        card.addView(when, tlp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | (t.rtl() ? Gravity.LEFT : Gravity.RIGHT);
        lp.x = Ui.ipx(96);
        lp.y = Ui.ipx(56);
        try {
            service.getSystemService(WindowManager.class).addView(card, lp);
        } catch (RuntimeException e) {
            counterTitle = null;
            return;
        }
        counter = card;
        counterKey = prayer + ":" + at.getTime();
    }

    void hideCounter() {
        counterKey = null;
        counterTitle = null;
        if (counter == null) return;
        try {
            service.getSystemService(WindowManager.class).removeView(counter);
        } catch (RuntimeException ignored) {
            // Already gone with the service.
        }
        counter = null;
    }

    // ---------------------------------------------------------------- identify

    private static final int IDENTIFY_SECONDS = 10;
    private View identify;
    /** One object, so a second Identify within ten seconds cancels the first one's hiding. */
    private final Runnable hideIdentifyLater = this::hideIdentify;

    /**
     * The name the computer gives this TV (else its device name, else its model), with its device
     * name, the model, Awan TV's version and the serial, in the middle of the screen over any app for ten seconds. Asked for by the
     * editor over adb (SettingsTransfer.IDENTIFY); shown, never stored.
     */
    void showIdentify(String name, String id, String version) {
        handler.post(() -> {
            hideIdentify();
            Ui.init(service);
            Text t = Screen.loadText(service);
            Tokens.apply(service);
            Typeface ui = service.getResources().getFont(R.font.alexandria_regular);
            // The computer's name and the TV's own device name both (the computer's large); without a
            // computer's name, the device name large and a line saying none is given yet.
            String pcName = name == null ? "" : name.trim();
            String device = android.provider.Settings.Global.getString(service.getContentResolver(), "device_name");
            if (device == null) device = "";
            String shown = !pcName.isEmpty() ? pcName : !device.isEmpty() ? device : android.os.Build.MODEL;
            String second = pcName.isEmpty() ? t.identifyNoName() : !device.isEmpty() && !device.equals(pcName) ? t.identifyDevice(device) : null;
            LinearLayout card = new LinearLayout(service);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setLayoutDirection(t.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
            card.setPadding(Ui.ipx(64), Ui.ipx(56), Ui.ipx(64), Ui.ipx(56));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Tokens.LAPIS_900);
            bg.setCornerRadius(Ui.px(32));
            bg.setStroke(Ui.ipx(3), Tokens.GOLD_400);
            card.setBackground(bg);
            card.addView(text(ui, 30, Tokens.MIST_400, t.identifyLabel()));
            TextView big = text(ui, 120, Tokens.GOLD_400, shown);
            big.setGravity(Gravity.CENTER);
            card.addView(big);
            if (second != null) {
                TextView l2 = text(ui, 34, Tokens.PLASTER_100, second);
                LinearLayout.LayoutParams l2p = new LinearLayout.LayoutParams(-1, -2);
                l2p.bottomMargin = Ui.ipx(10);
                card.addView(l2, l2p);
            }
            TextView model = text(ui, 34, Tokens.PLASTER_100, android.os.Build.MODEL + " · Awan TV v" + version);
            model.setTextDirection(View.TEXT_DIRECTION_LTR);
            card.addView(model);
            if (id != null && !id.isEmpty()) {
                TextView serial = text(ui, 34, Tokens.PLASTER_100, id);
                serial.setLetterSpacing(0.06f);
                serial.setTextDirection(View.TEXT_DIRECTION_LTR);
                card.addView(serial);
            }
            TextView foot = text(ui, 24, Tokens.MIST_400, t.identifyFoot(IDENTIFY_SECONDS));
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-2, -2);
            flp.topMargin = Ui.ipx(28);
            card.addView(foot, flp);
            FrameLayout track = new FrameLayout(service);
            track.setBackgroundColor(Tokens.BANNER_TRACK);
            View fill = new View(service);
            fill.setBackgroundColor(Tokens.GOLD_400);
            track.addView(fill, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, Ui.ipx(8));
            tlp.topMargin = Ui.ipx(22);
            card.addView(track, tlp);
            // Every line in the middle, as designed (a line as wide as the card would sit at its start or end).
            for (int i = 0; i < card.getChildCount(); i++) {
                View v = card.getChildAt(i);
                if (v instanceof TextView) {
                    ((TextView) v).setGravity(Gravity.CENTER);
                    ((TextView) v).setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                }
            }

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Ui.ipx(980), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;
            try {
                service.getSystemService(WindowManager.class).addView(card, lp);
            } catch (RuntimeException e) {
                return;
            }
            identify = card;
            fill.post(() -> {
                fill.setPivotX(t.rtl() ? fill.getWidth() : 0);
                fill.animate().scaleX(0f).setDuration(IDENTIFY_SECONDS * 1000L).setInterpolator(null).start();
            });
            handler.postDelayed(hideIdentifyLater, IDENTIFY_SECONDS * 1000L);
        });
    }

    private void hideIdentify() {
        handler.removeCallbacks(hideIdentifyLater);
        if (identify == null) return;
        try {
            service.getSystemService(WindowManager.class).removeView(identify);
        } catch (RuntimeException ignored) {
            // Already gone with the service.
        }
        identify = null;
    }

    private TextView text(Typeface tf, float size, int color, CharSequence s) {
        TextView v = new TextView(service);
        v.setTypeface(tf);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(size));
        v.setTextColor(color);
        v.setText(s);
        return v;
    }

    // ---------------------------------------------------------------- testing

    /**
     * For checking on the TV without waiting for a prayer: the banner (with the spoken reminder, if on), the call
     * screen, or the spoken «حان الآن موعد صلاة …» for the next prayer, after a delay to switch to another app
     * first. From Home: `am start -n app.awanpro.tv/.MainActivity --es test banner|call|announce`.
     */
    void test(String kind, long delayMs) {
        handler.postDelayed(() -> {
            Date now = new Date();
            PrayerDay day = new PrayerDay(service, now);
            if ("call".equals(kind)) {
                openCall(day.nextIndex, day.nextTime);
            } else if ("announce".equals(kind)) {
                Announce.now(service, day.nextIndex, day.friday);
            } else {
                showBanner(day, now);
                if (Prefs.remindVoice(service)) Announce.before(service, day.nextIndex, day.friday);
            }
        }, delayMs);
    }
}
