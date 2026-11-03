package app.awanpro.tv;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Hands Afnon's remote buttons to TCL's own key remapping, so RemoteKeys need not filter keys. With key filtering
 * on, Android re-injects every key and injected keys never auto-repeat: holding Volume moved it one step.
 *
 * TCL's window manager (TclCustomShortCutKeyManager in tcl-service.jar) reads Settings.Global "CustomKeyList", a JSON
 * array of keycodes, and for each one "CustomKey_<code>": {"keycode", "behavior": [{"keyaction", "type", "intent",
 * "appnotinstall"}]}. keyaction 1 acts on press, 2 on long press, 3 on release; type 1 starts an activity, 2 a service,
 * 3 sends a broadcast. It runs before any app sees the key and swallows it.
 *
 * That code catches nothing, inside system_server, so mistakes restart the TV's whole UI:
 * - An intent with no package, or one whose activity is missing, throws. Every intent names its package (TCL checks
 *   it is installed first and otherwise runs appnotinstall), and appnotinstall is a broadcast, which cannot throw.
 * - Changing "CustomKey_<code>" while <code> is not in the list throws (an observer outlives the list entry). So
 *   codes are never dropped from the list, only emptied, and keys are written a moment after the list, once TCL
 *   has read it.
 *
 * Every entry written here carries {@link #MISSING} (its appnotinstall), which is how ours are told from TCL's own
 * (or another app's): only ours are ever emptied. A TCL entry replaced by a viewer's button is kept in the prefs
 * ({@link #ORIGINAL}) and put back when the button goes. The viewer's buttons ({@link Buttons}) go here too, beside a
 * stock launcher or not, so TCL TVs answer them without key filtering. Entries outlive the app, so Remove Awan TV
 * empties them first ({@link #leave}), and tools/AwanTV-restore.ps1 empties every entry carrying the mark.
 */
final class TclKeys {

    private static final String TAG = "AwanKeys";
    private static final String FEATURE = "com.tcl.software.tinput_shortcut_config";
    private static final String LIST = "CustomKeyList";
    private static final String KEY_PREFIX = "CustomKey_";
    private static final String MISSING = "app.awanpro.tv.KEY_TARGET_MISSING";
    /** Time for TCL's observer to reread the list before the keys change. */
    private static final long LIST_SETTLE_MS = 1000;
    /** Prefs key prefix: TCL's own entry for a code, replaced by one of ours (SettingsTransfer.LOCAL keeps it). */
    static final String ORIGINAL = "tcl_orig_";

    /** True where TCL's remapping exists and Awan TV may write it (WRITE_SECURE_SETTINGS, granted over adb). */
    static boolean available(Context c) {
        return c.getPackageManager().hasSystemFeature(FEATURE)
            && c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Before Remove Awan TV: every entry of ours emptied (TCL's own put back), at once. */
    static void leave(Context c) {
        install(c, new java.util.HashMap<>(), new java.util.HashMap<>());
    }

    private TclKeys() {
    }

    /**
     * Maps each keycode to its intent, and to what holding it opens (holds: those that have one; the press then acts
     * on release, keyaction 3, and the hold on TCL's long press, keyaction 2). Returns false when this TV has no TCL remapping or the settings can't be
     * written (no WRITE_SECURE_SETTINGS grant); the caller then filters keys itself.
     */
    static boolean install(Context c, Map<Integer, Intent> targets, Map<Integer, Intent> holds) {
        if (!available(c)) return false;
        ContentResolver r = c.getContentResolver();
        try {
            Set<String> codes = codes(Settings.Global.getString(r, LIST));
            android.content.SharedPreferences prefs = Prefs.of(c);
            android.content.SharedPreferences.Editor ed = prefs.edit();
            JSONObject values = new JSONObject();
            for (Map.Entry<Integer, Intent> e : targets.entrySet()) {
                Intent i = packaged(e.getValue());
                if (i == null) continue;
                String code = String.valueOf(e.getKey());
                // TCL's own entry for this code, kept once, to put back when ours goes.
                String now = Settings.Global.getString(r, KEY_PREFIX + code);
                if (now != null && !now.isEmpty() && !now.contains(MISSING) && !prefs.contains(ORIGINAL + code)) {
                    ed.putString(ORIGINAL + code, now);
                }
                values.put(code, entry(e.getKey(), i, packaged(holds.get(e.getKey()))).toString());
            }
            // Ours no longer wanted (a button reset, its app uninstalled): TCL's own back, or emptied, which hands
            // the button back to the firmware. An entry that is not ours stays as it is.
            for (String code : codes) {
                if (values.has(code)) continue;
                String now = Settings.Global.getString(r, KEY_PREFIX + code);
                if (now == null || !now.contains(MISSING)) continue;
                values.put(code, prefs.getString(ORIGINAL + code, ""));
                ed.remove(ORIGINAL + code);
            }
            ed.apply();
            Set<String> all = new java.util.LinkedHashSet<>(codes);
            for (Iterator<String> it = values.keys(); it.hasNext(); ) all.add(it.next());
            if (all.equals(codes)) {
                // The list as it was: the keys at once (Remove Awan TV cannot wait).
                writeKeys(r, values);
            } else {
                Settings.Global.putString(r, LIST, new JSONArray(all).toString());
                new Handler(Looper.getMainLooper()).postDelayed(() -> writeKeys(r, values), LIST_SETTLE_MS);
            }
            return true;
        } catch (SecurityException | JSONException e) {
            Log.w(TAG, "TCL key remapping unavailable", e);
            return false;
        }
    }

    private static void writeKeys(ContentResolver r, JSONObject values) {
        for (Iterator<String> it = values.keys(); it.hasNext(); ) {
            String code = it.next();
            String value = values.optString(code);
            if (!value.equals(Settings.Global.getString(r, KEY_PREFIX + code))) {
                try {
                    Settings.Global.putString(r, KEY_PREFIX + code, value);
                } catch (SecurityException e) {
                    Log.w(TAG, "could not write " + KEY_PREFIX + code, e);
                }
            }
        }
    }

    /** The intent with its package set (TCL needs one), or null when it has none. */
    private static Intent packaged(Intent i) {
        if (i == null) return null;
        if (i.getPackage() == null && i.getComponent() != null) i.setPackage(i.getComponent().getPackageName());
        return i.getPackage() == null ? null : i;
    }

    /** One button: its press (on press, or on release when it has a hold), and its hold if any. */
    private static JSONObject entry(int code, Intent target, Intent hold) throws JSONException {
        JSONArray behaviors = new JSONArray().put(behavior(hold == null ? 1 : 3, target));
        if (hold != null) behaviors.put(behavior(2, hold));
        return new JSONObject().put("keycode", code).put("behavior", behaviors);
    }

    private static JSONObject behavior(int keyaction, Intent target) throws JSONException {
        JSONObject missing = new JSONObject()
                .put("type", 3)
                .put("intent", new Intent(MISSING).toUri(Intent.URI_INTENT_SCHEME));
        // Awan TV's own actions (the radio, nothing) are broadcasts; everything else opens an activity.
        boolean broadcast = target.getAction() != null && target.getAction().startsWith("app.awanpro.tv.");
        return new JSONObject()
                .put("keyaction", keyaction)
                .put("type", broadcast ? 3 : 1)
                .put("intent", target.toUri(Intent.URI_INTENT_SCHEME))
                .put("appnotinstall", missing);
    }

    private static Set<String> codes(String json) {
        Set<String> out = new HashSet<>();
        if (json == null) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) {
            // Not ours or garbled: overwrite it.
        }
        return out;
    }
}
