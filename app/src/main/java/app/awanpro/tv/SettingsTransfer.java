package app.awanpro.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Settings from one TV to another, for tools/AwanTV-settings.ps1. Only adb reaches it: the
 * manifest guards it with android.permission.DUMP, which adb's shell holds and apps do not.
 *
 * <pre>
 * am broadcast -n app.awanpro.tv/app.awanpro.tv.SettingsTransfer -a app.awanpro.tv.EXPORT
 * am broadcast -n app.awanpro.tv/app.awanpro.tv.SettingsTransfer -a app.awanpro.tv.IMPORT
 * </pre>
 * The file is {@link SettingsFile}'s, every setting with its comment, {@link #FILE} in the app's
 * external files folder (/sdcard/Android/data/app.awanpro.tv/files), which adb pulls and pushes. What belongs to
 * one TV ({@link #LOCAL}) is not in it. The result (English, for the terminal) is the broadcast's data; its code is
 * 1 when it worked.
 */
public class SettingsTransfer extends BroadcastReceiver {
    static final String EXPORT = "app.awanpro.tv.EXPORT", IMPORT = "app.awanpro.tv.IMPORT";
    /**
     * The editor's "Identify": this TV's name, as the computer calls it, large on
     * the screen for ten seconds over any app, so a viewer with several alike knows which is which. Extras: name_b64
     * (UTF-8 in Base64: adb's command line would garble Arabic) and id (its serial). Nothing is kept on the TV.
     */
    static final String IDENTIFY = "app.awanpro.tv.IDENTIFY";
    /**
     * Test builds only (a version with a letter, 1.5.0a): pretend GitHub's latest is the extra "version", and forget
     * what the notice was told, to see UpdateNotice without a newer release out; or, with the extra int "days_ago",
     * move when the notice was last closed back that many days (the weekly reminder); or, with the extra "download"
     * (a version, "1.6.0"), make Update now download that real release instead. A release answers Unknown action.
     */
    static final String TEST_UPDATE = "app.awanpro.tv.TEST_UPDATE";
    /**
     * The editor's TVs now (1.10.0): STATUS answers "radio=on|off station=<id> sound=yes|no name=<Base64 of the
     * station's Arabic name, a new line, its English one>" (sound: something plays on the TV now, Awan TV's radio
     * aside). RADIO with --es do play|stop starts or stops the radio at its chosen station; play does not take the
     * sound from another app playing (a video stops when the radio starts: Android gives sound to one) unless
     * --ez force true, and then answers "Busy: ...".
     */
    static final String STATUS = "app.awanpro.tv.STATUS", RADIO = "app.awanpro.tv.RADIO";
    static final String FILE = "awantv-settings.jsonc";
    /**
     * Written beside {@link #FILE} on export, for tools/AwanTV-editor.ps1 (SettingsFile.schema): the description in
     * each language, for the editor's own choice.
     */
    static final String SCHEMA_AR = "awantv-settings-schema-ar.json", SCHEMA_EN = "awantv-settings-schema-en.json";

    /** Keys (or, ending in "_", key prefixes) that describe this TV, not the viewer's choices. */
    private static final String[] LOCAL = {
        "saver_before", "sleep_saved", "sleep_timeout", "called", "reminded", "announced", "radio_viz", "guard",
        "last_adhan", "last_adhan_at", "last_adhan_ok", "service_on", "dl_", "lib_", "saved_ms_", "saved_surah_", "wx_", "upd_", "tcl_", "news_seen",
    };

    @Override
    public void onReceive(Context c, Intent intent) {
        String result;
        boolean ok = false;
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) throw new IOException("This TV gives the app no external files folder");
            File file = new File(dir, FILE);
            if (EXPORT.equals(intent.getAction())) {
                try (OutputStream os = new FileOutputStream(file)) {
                    os.write(SettingsFile.write(c, versionName(c)).getBytes(StandardCharsets.UTF_8));
                }
                try (OutputStream os = new FileOutputStream(new File(dir, SCHEMA_AR))) {
                    os.write(SettingsFile.schema(c, versionName(c), true).getBytes(StandardCharsets.UTF_8));
                }
                try (OutputStream os = new FileOutputStream(new File(dir, SCHEMA_EN))) {
                    os.write(SettingsFile.schema(c, versionName(c), false).getBytes(StandardCharsets.UTF_8));
                }
                // This TV's saved recitations and stations, for the editor's Saved recitations page and station list.
                try (OutputStream os = new FileOutputStream(new File(dir, LibraryRemote.FILE))) {
                    os.write(LibraryRemote.state(c).getBytes(StandardCharsets.UTF_8));
                }
                result = "Exported to " + file.getAbsolutePath();
            } else if (IMPORT.equals(intent.getAction())) {
                if (!file.isFile()) throw new IOException("No file " + file.getAbsolutePath() + " (push it first)");
                SettingsFile.Imported done = SettingsFile.read(c, readText(file));
                int n = done.changed;
                // The TV in step with the new settings: its screensaver, Home's guard, the iqamah countdown.
                Saver.apply(c);
                if (RemoteKeys.guard != null) RemoteKeys.guard.refresh();
                if (RemoteKeys.watch != null) RemoteKeys.watch.refreshCounter();
                // The screen in front shows them now, not after leaving it.
                if (n > 0) Screen.settingsChanged();
                // One line per part, " | " between them (the editor shows each in its own language).
                boolean more = done.recordings > 0 || !done.notes.isEmpty();
                StringBuilder r = new StringBuilder(n > 0 ? "Changed " + n + " setting(s)"
                    : more ? "No setting changed" : "Nothing to change: the TV already has these settings");
                if (done.recordings > 0) r.append(" | Queued ").append(done.queued).append(" surah(s) of ").append(done.recordings).append(" recitation(s)");
                for (String note : done.notes) r.append(" | ").append(note);
                result = r.toString();
            } else if (IDENTIFY.equals(intent.getAction())) {
                if (RemoteKeys.watch == null) throw new IOException("The Awan TV service is not running on this TV");
                String name = "";
                String b64 = intent.getStringExtra("name_b64");
                if (b64 != null && !b64.isEmpty()) {
                    name = new String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), StandardCharsets.UTF_8).trim();
                }
                RemoteKeys.watch.showIdentify(name, intent.getStringExtra("id"), versionName(c));
                result = "Shown on the TV";
            } else if (STATUS.equals(intent.getAction())) {
                result = status(c);
            } else if (RADIO.equals(intent.getAction())) {
                String what = intent.getStringExtra("do");
                if ("stop".equals(what)) {
                    if (Radio.on()) {
                        Radio.stop();
                        result = "Stopped";
                    } else {
                        result = "Already stopped";
                    }
                } else if ("play".equals(what)) {
                    if (Radio.on()) {
                        result = "Already playing";
                    } else if (otherSound(c) && !intent.getBooleanExtra("force", false)) {
                        result = "Busy: another app is playing sound";
                    } else {
                        Radio.start(c);
                        result = "Playing";
                    }
                } else {
                    throw new IOException("Give --es do play or --es do stop");
                }
                result += " | " + status(c);
            } else if (TEST_UPDATE.equals(intent.getAction()) && versionName(c).matches(".*\\d[a-z]+$")) {
                int days = intent.getIntExtra("days_ago", -1);
                String download = intent.getStringExtra("download");
                if (intent.getBooleanExtra("updated", false)) {
                    // As if Update now had just installed this version over Home: the service, when it connects again,
                    // opens Home (SelfUpdate.afterUpdate).
                    Prefs.of(c).edit().putLong("upd_self_at", System.currentTimeMillis()).putBoolean("upd_self_home", true).commit();
                    result = "Marked as just updated from Home (test build); restart the service to see Home come back";
                } else if (download != null) {
                    // Update now downloads this real release instead (an older one: Android then refuses it).
                    SelfUpdate.testDownload = download.isEmpty() ? null : download;
                    result = download.isEmpty() ? "Update now downloads the version offered again (test build)"
                        : "Update now downloads " + download + " instead (test build, until the app restarts)";
                } else if (days >= 0) {
                    // The notice last closed that many days ago: a week's wait seen at once.
                    Prefs.of(c).edit().putLong("upd_shown_at", System.currentTimeMillis() - days * 24 * 3600_000L).apply();
                    result = "Notice last closed " + days + " day(s) ago (test build)";
                } else {
                    String v = intent.getStringExtra("version");
                    if (v == null || !v.matches("\\d+(\\.\\d+)*[a-z]*")) throw new IOException("Give --es version 1.9.0 or --ei days_ago 8");
                    Prefs.of(c).edit().putString("upd_latest", v).putLong("upd_at", System.currentTimeMillis())
                        .remove("upd_shown").remove("upd_shown_at").remove("upd_muted").apply();
                    result = "Latest set to " + v + " (test build)";
                }
            } else {
                throw new IOException("Unknown action " + intent.getAction());
            }
            ok = true;
        } catch (IOException | JSONException | RuntimeException e) {
            result = "Failed: " + e.getMessage();
        }
        android.util.Log.i("AwanSettings", result);
        setResult(ok ? 1 : 0, result, null);
    }

    /** Whether something plays on the TV now other than Awan TV's radio (a video, music in another app). */
    private static boolean otherSound(Context c) {
        android.media.AudioManager am = c.getSystemService(android.media.AudioManager.class);
        return am != null && am.isMusicActive() && !Radio.on();
    }

    /** STATUS's answer: the radio, its station (id and names) and whether another app plays sound. */
    private static String status(Context c) {
        Radio.Station st = Radio.station(c);
        String names = st.ar + "\n" + st.en;
        if (st.savedKey() != null) {
            String m = Library.moshafName(c, st.savedKey(), true), me = Library.moshafName(c, st.savedKey(), false);
            names = st.ar + (m.isEmpty() ? "" : " · " + m) + "\n" + st.en + (me.isEmpty() ? "" : " · " + me);
        } else if (!st.reciter) {
            names = st.ar + " · " + st.whereAr + "\n" + st.en + " · " + st.whereEn;
        }
        String b64 = android.util.Base64.encodeToString(names.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
        return "radio=" + (Radio.on() ? "on" : "off") + " station=" + st.id + " sound=" + (otherSound(c) ? "yes" : "no") + " name=" + b64;
    }

    /**
     * Kept by Settings > About > Reset settings, for AwanTV-settings.ps1 -Restore: one file per reset, named
     * by its time (BEFORE_RESET + yyyyMMdd-HHmmss + .jsonc), the last {@link #KEEP_RESETS} kept, and none written when
     * nothing but the place is set, so a second reset in a row never pushes the good copy out.
     */
    static final String BEFORE_RESET = "awantv-settings-before-reset-";
    private static final int KEEP_RESETS = 5;
    private static final java.util.regex.Pattern RESET_NAME =
        java.util.regex.Pattern.compile("^awantv-settings-before-reset-\\d{8}-\\d{6}\\.jsonc$");

    /** Writes the settings before a reset; false when there was nothing to keep or it could not be written. */
    static boolean saveBeforeReset(Context c) {
        try {
            // Nothing worth keeping: as a new install's but the place, which the city list after a reset sets (a
            // reset, the city picked, then another reset would write a copy of the defaults newer than the good
            // one). The last copy stays.
            boolean any = false;
            for (String key : Prefs.of(c).getAll().keySet()) if (!local(key) && !place(key)) { any = true; break; }
            if (!any) return false;
            File dir = c.getExternalFilesDir(null);
            if (dir == null) return false;
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(new java.util.Date());
            try (OutputStream os = new FileOutputStream(new File(dir, BEFORE_RESET + stamp + ".jsonc"))) {
                os.write(SettingsFile.write(c, versionName(c)).getBytes(StandardCharsets.UTF_8));
            }
            // The newest few only; the names sort by time.
            String[] names = dir.list();
            if (names != null) {
                java.util.List<String> kept = new java.util.ArrayList<>();
                for (String n : names) if (RESET_NAME.matcher(n).matches()) kept.add(n);
                java.util.Collections.sort(kept, java.util.Collections.reverseOrder());
                for (int i = KEEP_RESETS; i < kept.size(); i++) new File(dir, kept.get(i)).delete();
            }
            return true;
        } catch (IOException | JSONException | RuntimeException e) {
            android.util.Log.w("AwanSettings", "Before reset: " + e);
            return false;
        }
    }

    /** What the city list sets with a place: the place itself, its method and Asr school, Morocco's table. */
    private static boolean place(String key) {
        return key.equals("city") || key.startsWith("custom_") || key.equals("method") || key.equals("madhab")
            || key.equals(Habous.PREF);
    }

    static boolean local(String key) {
        for (String k : LOCAL) {
            if (k.endsWith("_") ? key.startsWith(k) : key.equals(k)) return true;
        }
        return false;
    }

    private static String readText(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (InputStream is = new FileInputStream(file)) {
            int at = 0;
            while (at < data.length) {
                int n = is.read(data, at, data.length - at);
                if (n < 0) break;
                at += n;
            }
        }
        String s = new String(data, StandardCharsets.UTF_8);
        // Notepad may save with a byte order mark.
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }

    private static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
