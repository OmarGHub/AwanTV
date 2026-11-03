package app.awanpro.tv;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The viewer's own remote buttons (Settings > Device settings > Remote buttons): a button pressed once opens an app,
 * one of Awan TV's screens, or nothing. Kept in the prefs key {@link #KEY} as a JSON array of {k, name, from, do}.
 *
 * <p>A button is known by its keycode, or by its scancode when it has none (TCL's Web button sends keycode 0 over IR
 * on some firmwares). The remote it came from is kept only to show it: the same button on two remotes is one button.
 * {@link RemoteKeys} answers them, filtering keys while at least one is set: the cost is that a held key (Volume)
 * moves one step (see {@link FirmwareKeys}), which the first customisation warns about. The keys that move around,
 * leave, power and set the volume are never taken ({@link #excluded}).
 *
 * <p>Not in the settings file nor copied between TVs: remotes and apps differ from one TV to the next. Reset clears it.
 */
final class Buttons {
    private Buttons() {}

    static final String KEY = "remote_buttons";
    /** The first-customisation warning was accepted once. */
    static final String WARNED = "remote_buttons_warned";

    /** Test builds show each button's keycode or scancode under its name, to learn the remotes; off in a release. */
    static final boolean SHOW_CODES = false;

    static final String NONE = "none", SAVER = "awan:saver", TODAY = "awan:today", RADIO = "awan:radio";
    private static final String APP = "app:";

    /** One customised button. */
    static final class Entry {
        final String id, name, from;
        String action;

        Entry(String id, String name, String from, String action) {
            this.id = id;
            this.name = name;
            this.from = from;
            this.action = action;
        }
    }

    /** The button's id: its keycode, or "s" and its scancode for a key with no keycode; null for neither. */
    static String id(KeyEvent e) {
        int code = e.getKeyCode();
        if (code != KeyEvent.KEYCODE_UNKNOWN) return String.valueOf(code);
        return e.getScanCode() > 0 ? "s" + e.getScanCode() : null;
    }

    /** Keys never customised: moving, OK, Back, Home, the volume and the power. */
    static boolean excluded(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_TV_POWER:
                return true;
            default:
                return false;
        }
    }

    /** The remote's own name ("G20", "Google TV Remote"), or "" if Android gives none. */
    static String remote(KeyEvent e) {
        InputDevice d = e.getDevice();
        if (d == null) d = InputDevice.getDevice(e.getDeviceId());
        return d == null || d.getName() == null ? "" : d.getName().trim();
    }

    /** A name for the button, in the screen's language where Android's name says what it is. */
    static String name(Text t, int code, int scan) {
        switch (code) {
            case KeyEvent.KEYCODE_PROG_RED: return t.ar ? "الزر الأحمر" : "Red button";
            case KeyEvent.KEYCODE_PROG_GREEN: return t.ar ? "الزر الأخضر" : "Green button";
            case KeyEvent.KEYCODE_PROG_YELLOW: return t.ar ? "الزر الأصفر" : "Yellow button";
            case KeyEvent.KEYCODE_PROG_BLUE: return t.ar ? "الزر الأزرق" : "Blue button";
            case KeyEvent.KEYCODE_SETTINGS: return t.ar ? "زر الإعدادات" : "Settings button";
            case KeyEvent.KEYCODE_TV_INPUT: return t.ar ? "زر المصدر" : "Input button";
            case KeyEvent.KEYCODE_GUIDE: return t.ar ? "زر الدليل" : "Guide button";
            case KeyEvent.KEYCODE_INFO: return t.ar ? "زر المعلومات" : "Info button";
            case KeyEvent.KEYCODE_BOOKMARK: return t.ar ? "زر المفضلة" : "Bookmark button";
            case KeyEvent.KEYCODE_ALL_APPS: return t.ar ? "زر التطبيقات" : "Apps button";
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return t.ar ? "زر التشغيل والإيقاف" : "Play/pause button";
            case KeyEvent.KEYCODE_CHANNEL_UP: return t.ar ? "القناة التالية" : "Channel up";
            case KeyEvent.KEYCODE_CHANNEL_DOWN: return t.ar ? "القناة السابقة" : "Channel down";
            case KeyEvent.KEYCODE_UNKNOWN: return t.d(t.ar ? "زر رقمه " + scan : "Button " + scan);
            default: break;
        }
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            return t.d((t.ar ? "الرقم " : "Number ") + (code - KeyEvent.KEYCODE_0));
        }
        String s = KeyEvent.keyCodeToString(code);
        if (s.startsWith("KEYCODE_")) s = s.substring(8);
        // A keycode with no Android name (TCL's own) prints as its number.
        if (s.matches("\\d+")) return t.d(t.ar ? "زر رقمه " + s : "Button " + s);
        return (t.ar ? "زر " : "") + s.replace('_', ' ') + (t.ar ? "" : " button");
    }

    /** For the test builds: the keycode's name and number, and the scancode. */
    static String codes(int code, int scan) {
        return KeyEvent.keyCodeToString(code) + " (" + code + ") scan " + scan;
    }

    // ---------------------------------------------------------------- the saved table

    private static String cachedRaw;
    private static Map<String, String> cachedActions = new HashMap<>();

    static List<Entry> all(Context c) {
        List<Entry> out = new ArrayList<>();
        String raw = Prefs.of(c).getString(KEY, null);
        if (raw == null) return out;
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Entry(o.getString("k"), o.optString("name"), o.optString("from"), o.getString("do")));
            }
        } catch (JSONException ignored) {
            // Garbled: as if none were set.
        }
        return out;
    }

    static boolean any(Context c) {
        return !actions(c).isEmpty();
    }

    static Entry find(Context c, String id) {
        for (Entry e : all(c)) if (e.id.equals(id)) return e;
        return null;
    }

    /** Saves the entry (replacing one with its id), or removes the id when action is null. */
    static void save(Context c, Entry entry, String action) {
        List<Entry> list = all(c);
        int at = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(entry.id)) at = i;
        if (action == null) {
            if (at >= 0) list.remove(at);
        } else {
            entry.action = action;
            if (at >= 0) list.set(at, entry);
            else list.add(entry);
        }
        JSONArray a = new JSONArray();
        try {
            for (Entry e : list) {
                a.put(new JSONObject().put("k", e.id).put("name", e.name).put("from", e.from).put("do", e.action));
            }
        } catch (JSONException ignored) {
            // Plain strings only; cannot happen.
        }
        SharedPreferences.Editor ed = Prefs.of(c).edit();
        if (list.isEmpty()) ed.remove(KEY);
        else ed.putString(KEY, a.toString());
        ed.apply();
    }

    /** The id -> action table, parsed once per change of the saved text (asked on every key while filtering). */
    private static synchronized Map<String, String> actions(Context c) {
        String raw = Prefs.of(c).getString(KEY, null);
        if (raw == null) return new HashMap<>();
        if (!raw.equals(cachedRaw)) {
            Map<String, String> m = new HashMap<>();
            for (Entry e : all(c)) m.put(e.id, e.action);
            cachedActions = m;
            cachedRaw = raw;
        }
        return cachedActions;
    }

    /** What the pressed button is set to do, or null when it is not customised (or never can be). */
    static String action(Context c, KeyEvent e) {
        if (excluded(e.getKeyCode())) return null;
        String id = id(e);
        return id == null ? null : actions(c).get(id);
    }

    // ---------------------------------------------------------------- actions

    static String app(String pkg) {
        return APP + pkg;
    }

    /** The app an action opens, or null for Awan TV's own screens and nothing. */
    static String pkg(String action) {
        return action != null && action.startsWith(APP) ? action.substring(APP.length()) : null;
    }

    /** False when the action cannot run here (its app is gone): the key then does what it always did. */
    static boolean available(Context c, String action) {
        String pkg = pkg(action);
        return pkg == null || Apps.launchIntent(c, pkg) != null;
    }

    static void run(Context c, String action) {
        String pkg = pkg(action);
        if (pkg != null) {
            Apps.launch(c, pkg);
            return;
        }
        Intent open = null;
        if (SAVER.equals(action)) open = SaverActivity.now(c);
        else if (TODAY.equals(action)) open = new Intent(c, PrayerDayActivity.class);
        else if (RADIO.equals(action)) app.awanpro.tv.Radio.toggle(c);
        if (open == null) return;
        try {
            c.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Nothing to open; the key does nothing.
        }
    }

    /**
     * The action as an intent, for TCL's remapping ({@link TclKeys}): an activity, or for the radio and nothing a
     * broadcast (TclKeys sends "app.awanpro.tv." actions as broadcasts). Null when its app is gone.
     */
    static Intent intent(Context c, String action) {
        String pkg = pkg(action);
        Intent i;
        if (pkg != null) i = Apps.launchIntent(c, pkg);
        else if (SAVER.equals(action)) i = SaverActivity.now(c);
        else if (TODAY.equals(action)) i = new Intent(c, PrayerDayActivity.class);
        else if (RADIO.equals(action)) return new Intent(RADIO_TOGGLE).setClass(c, Radio.class);
        else if (NONE.equals(action)) return new Intent(NOTHING).setPackage(c.getPackageName());
        else return null;
        return i == null ? null : i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    static final String RADIO_TOGGLE = "app.awanpro.tv.RADIO_TOGGLE";
    /** A broadcast no one receives: TCL swallows the button and nothing happens. */
    static final String NOTHING = "app.awanpro.tv.KEY_NOTHING";

    /** The radio button through TCL's remapping: starts or stops the radio. */
    public static final class Radio extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            if (RADIO_TOGGLE.equals(i.getAction())) app.awanpro.tv.Radio.toggle(c);
        }
    }

    /** The action as the chooser and the list show it. */
    static String label(Context c, Text t, String action) {
        if (NONE.equals(action)) return t.buttonOff();
        if (SAVER.equals(action)) return t.buttonAwan(t.saverNowRow());
        if (TODAY.equals(action)) return t.buttonAwan(t.today());
        if (RADIO.equals(action)) return t.buttonAwan(t.buttonRadio());
        String pkg = pkg(action);
        return pkg == null ? "" : Apps.name(c, t, pkg);
    }
}
