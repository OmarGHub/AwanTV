package app.awanpro.tv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Remote buttons that only the stock launcher answered, from any app: the app buttons, Settings, Input and All
 * apps. Every other key passes through untouched. Taken over from the cleanyourtv launcher's InputKeyService and
 * HomeKeyService so that package can be disabled. Keys injected over ADB never reach this; test with the remote.
 * Being always on, it also hosts {@link PrayerWatch}: the reminders and the call to prayer.
 *
 * On TCL firmwares the buttons are handed to TCL's own key remapping ({@link TclKeys}) and this service stops
 * filtering keys: filtered keys lose their auto-repeat, so holding Volume moved it only one step. Firmwares that
 * answer the buttons themselves (CVTE's hotkey service, see {@link FirmwareKeys}) are left alone too. Elsewhere it
 * filters keys and opens the targets itself, as below; a button whose target is missing passes through.
 *
 * <p>Beside a stock launcher on an HDMI source box (Google TV Streamer) it filters keys for Home's
 * button alone ({@link HomeGuard#onHomeKey}); every other key passes straight through.
 *
 * <p>The viewer's own buttons ({@link Buttons}, Settings > Device settings > Remote buttons) come before all of
 * that, beside a stock launcher or not. On a TCL TV they go to TCL's remapping ({@link #applyKeys}), so nothing is
 * filtered for them; elsewhere keys are filtered while one is set and a customised button does what the viewer
 * chose. Settings' "press the button" asks for the next key through {@link #capture}, filtering meanwhile.
 *
 * <p>While filtering, a held Volume key moves only one step (re-injected keys never repeat). Left so on purpose:
 * the first customisation says so, and an app cannot send the volume on (AudioManager's adjustments never reached a
 * Google TV Streamer's soundbar over HDMI-CEC); a TCL TV pays nothing.
 */
public class RemoteKeys extends AccessibilityService {

    /** Key name (TCL names resolve on this firmware only) or keycode -> the app it opens. */
    private static final String[][] APP_KEYS = {
        {"KEYCODE_BUTTON_3", "com.google.android.youtube.tv"},
        {"KEYCODE_TCL_YOUTUBE", "com.google.android.youtube.tv"},
        // The Web button: TCL's own keycode with no Android name, sent over IR (scancode 651).
        {"4159", "com.tcl.browser"},
    };
    /** TCL remotes send Settings as 83 (KEYCODE_NOTIFICATION), which the stock home turned into its panel. */
    private static final int SETTINGS_KEY = KeyEvent.KEYCODE_NOTIFICATION;
    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private final Map<Integer, String> appKeys = new HashMap<>();
    /** Whether key filtering is on now; null before the first setting. */
    private Boolean filtering;
    /** The running watch, for Home's test hook; null while the service is off. */
    static PrayerWatch watch;
    /** Home over the TV's own home screen; null while the service is off. */
    static HomeGuard guard;
    /** The running service, for Settings' capture; null while it is off. */
    private static RemoteKeys running;
    /** Afnon's own reason to filter, with no stock launcher and no TCL remapping (FirmwareKeys). */
    private boolean afnonFilter;
    /** The viewer's buttons need filtering: a button is set and TCL's remapping cannot answer every one. */
    private boolean customFilter;

    /** Settings' "press the button": hears the next key that can be customised, on the main thread. */
    interface Capture {
        void heard(KeyEvent e);
    }

    /** Waiting for a button to customise (keys are filtered meanwhile); null otherwise. */
    private static Capture capture;
    /** The key whose press went to the capture: its release (and repeats) are swallowed too. */
    private static int captured = -1;
    /** A held custom button: its hold waiting to fire; heldFired once it has (its release then does nothing). */
    private final android.os.Handler keys = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable held;
    private int heldCode = -1;
    private boolean heldFired;

    /** Starts listening for a button; false when the service is off (Settings then hears what reaches it). */
    static boolean startCapture(Capture c) {
        capture = c;
        captured = -1;
        if (running == null) return false;
        running.keysChanged();
        return true;
    }

    static void stopCapture() {
        capture = null;
        if (running != null) running.keysChanged();
    }

    /** The customised buttons changed in Settings: TCL's remapping and filtering follow. */
    static void buttonsChanged() {
        if (running == null) return;
        running.applyKeys();
        running.keysChanged();
    }

    /**
     * TCL's remapping: Afnon's table (no stock launcher only) with the viewer's buttons over it. A button known only
     * by its scancode, or a TV without TCL's remapping, leaves the viewer's buttons to the key filter. Returns
     * whether TCL's remapping took them.
     */
    private boolean applyKeys() {
        boolean stock = guard != null && guard.hasStock();
        Map<Integer, Intent> to = stock ? new HashMap<>() : targets();
        Map<Integer, Intent> holds = new HashMap<>();
        boolean byScan = false;
        for (Buttons.Entry b : Buttons.all(this)) {
            if (b.id.startsWith("s")) {
                byScan = true;
                continue;
            }
            Intent i = Buttons.intent(this, b.action);
            // Its app gone: the button does what it did before.
            if (i == null) continue;
            int code = Integer.parseInt(b.id);
            to.put(code, i);
            Intent h = b.longAction == null ? null : Buttons.intent(this, b.longAction);
            if (h != null) holds.put(code, h);
        }
        boolean tcl = TclKeys.install(this, to, holds);
        customFilter = Buttons.any(this) && (!tcl || byScan);
        return tcl;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        appKeys.clear();
        for (String[] k : APP_KEYS) {
            int code = KeyEvent.keyCodeFromString(k[0]);
            if (code > 0) appKeys.put(code, k[1]);
        }
        // On again (by the install, the viewer or ServiceCheck): ServiceCheck may put it right after a force-stop.
        Prefs.of(this).edit().putBoolean(ServiceCheck.ON, true).apply();
        running = this;
        guard = new HomeGuard(this);
        // Beside the TV's own home screen (shown over it or not) that launcher still answers the remote's buttons,
        // so Afnon's button table is for a TV where Awan TV itself is Home and the stock launcher is gone; beside it
        // only the viewer's own buttons are answered, and Home's on an HDMI source box (HomeGuard.takesHome).
        boolean tcl = applyKeys();
        afnonFilter = !guard.hasStock() && !tcl && FirmwareKeys.shouldFilter(this);
        keysChanged();
        // The radio may have left the TV's sleep timer off (a crash, a reboot mid-listen): put it back.
        Radio.restoreSleep(this);
        // Keep the TV's own screensaver to put back later, and the TV's in step with Settings > Awan TV.
        Saver.remember(this);
        Saver.apply(this);
        // Saved recitations queued before a restart carry on.
        Library.pump(this);
        if (watch != null) watch.stop();
        watch = new PrayerWatch(this);
        watch.start();
    }

    /**
     * Unbound while no longer listed: the viewer turned it off in the TV's settings (or AwanTV-restore did), so
     * ServiceCheck leaves it off. A force-stop unbinds nothing, which is how the two are told apart. Except in the
     * first minute after an update: Android may handle the update's force-stop late, taking the service out of the
     * list after it was bound again (a Google TV Streamer did), and ServiceCheck's update check must put that right.
     */
    @Override
    public boolean onUnbind(Intent intent) {
        if (!ServiceCheck.listed(this) && !ServiceCheck.justUpdated(this)) {
            Prefs.of(this).edit().putBoolean(ServiceCheck.ON, false).apply();
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (watch != null) watch.stop();
        watch = null;
        guard = null;
        if (running == this) running = null;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (captureKey(event)) return true;
        if (customKey(event)) return true;
        // Beside the stock launcher every key is its own, but Home's on an HDMI source box.
        if (guard != null && guard.hasStock()) return guard.onHomeKey(event);
        int code = event.getKeyCode();
        String app = appKeys.get(code);
        Intent open;
        if (app != null) open = null;
        // Afnon Settings, which lists the TV's own under Device settings.
        else if (code == SETTINGS_KEY || code == KeyEvent.KEYCODE_SETTINGS) open = new Intent(this, SettingsActivity.class);
        else if (code == KeyEvent.KEYCODE_TV || code == KeyEvent.KEYCODE_TV_INPUT) open = inputs();
        else if (code == KeyEvent.KEYCODE_ALL_APPS) open = new Intent(this, AllAppsActivity.class);
        else return false;
        // Nothing here answers it (the app is gone, or the firmware has no Android TV inputs screen): let the key
        // through so the firmware's own handling (a CVTE source menu, say) still gets it.
        if (app != null ? Apps.launchIntent(this, app) == null : open == null || explicit(new Intent(open)) == null) return false;
        // Act on release only, and swallow the press too so the app underneath never sees half a key.
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            if (app != null) {
                Apps.launch(this, app);
            } else {
                try {
                    startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ignored) {
                    // No input switcher or settings on this build; the key just does nothing.
                }
            }
        }
        return true;
    }

    /** Settings waiting for a button: its press goes there, its release and repeats nowhere. Back, moving, OK pass. */
    private boolean captureKey(KeyEvent e) {
        int code = e.getKeyCode();
        if (captured == code && (e.getAction() == KeyEvent.ACTION_UP || e.getRepeatCount() > 0)) {
            if (e.getAction() == KeyEvent.ACTION_UP) captured = -1;
            return true;
        }
        Capture c = capture;
        if (c == null || Buttons.excluded(code) || e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() > 0) return false;
        if (Buttons.id(e) == null) return false;
        captured = code;
        capture = null;
        KeyEvent copy = new KeyEvent(e);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> c.heard(copy));
        return true;
    }

    /**
     * A button the viewer customised: its action on release, the press and repeats swallowed. With a hold too, the
     * press starts Android's long-press time: still held then, the hold runs and the release does nothing.
     */
    private boolean customKey(KeyEvent e) {
        String action = Buttons.action(this, e);
        if (action == null || !Buttons.available(this, action)) return false;
        String hold = Buttons.longAction(this, e);
        if (hold != null && !Buttons.available(this, hold)) hold = null;
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (hold != null && e.getRepeatCount() == 0) {
                cancelHold();
                String run = hold;
                heldCode = code;
                heldFired = false;
                held = () -> {
                    heldFired = true;
                    held = null;
                    if (!Buttons.NONE.equals(run)) Buttons.run(this, run);
                };
                keys.postDelayed(held, Buttons.LONG_MS);
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_UP) return true;
        boolean fired = heldCode == code && heldFired;
        if (heldCode == code) {
            cancelHold();
            heldCode = -1;
        }
        if (!fired && !e.isCanceled() && !Buttons.NONE.equals(action)) Buttons.run(this, action);
        return true;
    }

    private void cancelHold() {
        if (held != null) keys.removeCallbacks(held);
        held = null;
    }

    /** Every button this service answers, with what it opens; buttons whose target is missing are left out. */
    private Map<Integer, Intent> targets() {
        Map<Integer, Intent> out = new HashMap<>();
        for (Map.Entry<Integer, String> e : appKeys.entrySet()) {
            Intent i = Apps.launchIntent(this, e.getValue());
            if (i != null) out.put(e.getKey(), i);
        }
        Intent settings = new Intent(this, SettingsActivity.class);
        out.put(SETTINGS_KEY, settings);
        out.put(KeyEvent.KEYCODE_SETTINGS, settings);
        Intent inputs = inputs();
        if (inputs != null) {
            out.put(KeyEvent.KEYCODE_TV, inputs);
            out.put(KeyEvent.KEYCODE_TV_INPUT, inputs);
        }
        out.put(KeyEvent.KEYCODE_ALL_APPS, new Intent(this, AllAppsActivity.class));
        for (Intent i : out.values()) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return out;
    }

    /** The Android TV inputs screen, else the firmware's own source menu; null if neither exists. */
    private Intent inputs() {
        Intent i = explicit(new Intent(ACTION_VIEW_INPUTS));
        return i != null ? i : FirmwareKeys.sourceMenu(this);
    }

    /** The intent pinned to the activity that answers it now, or null if none does. */
    private Intent explicit(Intent i) {
        ResolveInfo r = getPackageManager().resolveActivity(i, 0);
        if (r == null || r.activityInfo == null) return null;
        ActivityInfo a = r.activityInfo;
        return i.setComponent(new ComponentName(a.packageName, a.name));
    }

    /**
     * Filtering is on while Settings waits for a button, while a button is customised, and otherwise as before:
     * beside a stock launcher only while the service takes Home's button, without one where Afnon filtered.
     */
    void keysChanged() {
        boolean stock = guard != null && guard.hasStock();
        boolean on = capture != null || customFilter || (stock ? guard.takesHome() : afnonFilter);
        if (filtering != null && filtering == on) return;
        filterKeys(on);
        android.util.Log.i(HomeGuard.TAG, "Key filtering: " + on + (stock ? " (Home taken: " + guard.takesHome() + ")" : ""));
    }

    private void filterKeys(boolean on) {
        filtering = on;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        int flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.flags = on ? info.flags | flag : info.flags & ~flag;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Redirects.onWindowChanged(this, event);
        if (guard != null) guard.onEvent(event);
    }

    @Override
    public void onInterrupt() {
    }
}
