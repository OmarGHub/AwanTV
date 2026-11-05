package app.awanpro.tv;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recitations saved on the TV, to listen without internet (Settings > Radio > Saved recitations,
 * {@link LibraryActivity}; played by {@link Radio} as a station of its own).
 *
 * <p>From mp3quran.net, whose rights notice lets any developer use any of its material or links
 * (mp3quran.net/ar/privacy); the recitations remain their reciters'. The reciter list is its API
 * (mp3quran.net/api/v3/reciters, in Arabic and English, kept for a week); each surah is the reciter's server plus
 * the three-digit surah number and ".mp3". The app carries its own copy of the lists (assets/mp3quran, read again for
 * every release build by app/build.gradle), used until the TV reads them: a recording's names, kept in the app's data,
 * are found again by its key after a reinstall without internet (Android wipes the data, the files in Music stay, and
 * Music takes no file but audio to keep them in).
 *
 * <p>Downloads go through Android's own download manager (it resumes after a dropped connection or a restart),
 * one surah at a time, in the order asked, as ".part" into the app's own folder, then moved once complete into
 * the TV's shared Music folder (Music/Awan TV/&lt;reciter&gt;/&lt;surah&gt;.mp3), so it stays when the app is removed
 * and is found again after a reinstall (Android then counts the files as the old install's: reading them needs the
 * audio read permission the install tools grant; deleting them asks Android, see {@link #askToDelete}). Music only
 * takes audio, hence the ".part" kept apart. Other music players on the TV see them too: it is the Quran.
 * 2 GB are always left free for the TV: the queue waits when less is
 * left. {@link Done} hears each download finish and starts the next; there is no service of its own.
 */
final class Library {
    private Library() {}

    static final String TAG = "AwanLibrary";
    /** Left free on the TV whatever is queued. */
    static final long RESERVE = 2L * 1024 * 1024 * 1024;
    private static final String API = "https://mp3quran.net/api/v3/";
    private static final long LIST_MAX_AGE = 7L * 24 * 3600_000;
    /** The app's own copy of mp3quran.net's lists, in its assets. */
    private static final String BUNDLED = "mp3quran/";
    private static final int TIMEOUT_MS = 15_000;

    /** One reciter's recording (a reciter can have several: other riwayat, murattal or mujawwad). */
    static final class Reciter {
        final int id, moshaf;
        final String ar, en, moshafAr, moshafEn, server;
        final int[] surahs;
        /** Its names in the screens' other languages that mp3quran.net has (Languages.moreLists): {name, moshaf}. */
        final Map<String, String[]> more = new HashMap<>();

        Reciter(int id, int moshaf, String ar, String en, String moshafAr, String moshafEn, String server, int[] surahs) {
            this.id = id;
            this.moshaf = moshaf;
            this.ar = ar;
            this.en = en;
            this.moshafAr = moshafAr;
            this.moshafEn = moshafEn;
            this.server = server.endsWith("/") ? server : server + "/";
            this.surahs = surahs;
        }

        /** Its folder and station id: "r&lt;reciter&gt;-m&lt;moshaf&gt;". */
        String key() {
            return "r" + id + "-m" + moshaf;
        }

        String name(Text t) {
            String[] own = more.get(t.lang);
            if (own != null && !own[0].isEmpty()) return own[0];
            return t.ar || en.isEmpty() ? ar : en;
        }

        String moshaf(Text t) {
            String[] own = more.get(t.lang);
            if (own != null && !own[1].isEmpty()) return own[1];
            return t.ar || moshafEn.isEmpty() ? moshafAr : moshafEn;
        }

        String url(int surah) {
            return server + String.format(Locale.US, "%03d.mp3", surah);
        }
    }

    // ---------------------------------------------------------------- the reciter list

    /** The reciters (cached; read from mp3quran.net when there is no list yet or it is a week old). Not on the main thread. */
    static List<Reciter> reciters(Context c, boolean fresh) throws Exception {
        File ar = new File(dir(c, false), "reciters-ar.json"), en = new File(dir(c, false), "reciters-en.json");
        boolean old = !ar.exists() || System.currentTimeMillis() - ar.lastModified() > LIST_MAX_AGE;
        if (fresh || old) {
            try {
                save(ar, get(API + "reciters?language=ar"));
                save(en, get(API + "reciters?language=eng"));
                for (String lang : Languages.moreLists()) {
                    save(new File(dir(c, false), "reciters-" + lang + ".json"), get(API + "reciters?language=" + Languages.mp3quran(lang)));
                }
                synchronized (KNOWN_LOCK) {
                    known = null;
                }
            } catch (IOException e) {
                // Offline: the list kept from before, else the app's own copy (both() throws when there is neither).
                Log.i(TAG, "reciter list not read: " + e);
            }
        }
        return both(c);
    }

    /** The kept lists, Arabic with the English names; throws when there is no list at all. */
    private static List<Reciter> both(Context c) throws Exception {
        String arText = listText(c, "reciters-ar.json"), enText = listText(c, "reciters-en.json");
        if (arText == null) throw new IOException("no reciter list");
        Map<String, String[]> english = new LinkedHashMap<>();
        if (enText != null) for (Reciter r : parse(enText, null)) english.put(r.key(), new String[] {r.ar, r.moshafAr});
        List<Reciter> out = parse(arText, english);
        // Names in the screens' other languages, from their own lists (none for Arabic and English only).
        for (String lang : Languages.moreLists()) {
            String text = listText(c, "reciters-" + lang + ".json");
            if (text == null) continue;
            Map<String, String[]> names = new HashMap<>();
            for (Reciter r : parse(text, null)) names.put(r.key(), new String[] {r.ar, r.moshafAr});
            for (Reciter r : out) {
                String[] n = names.get(r.key());
                if (n != null) r.more.put(lang, n);
            }
        }
        return out;
    }

    /** A list as the TV last read it, else the app's own copy; null when there is neither. */
    private static String listText(Context c, String name) {
        File f = new File(dir(c, false), name);
        if (f.exists()) {
            try {
                return read(f);
            } catch (IOException e) {
                Log.i(TAG, "kept " + name + ": " + e);
            }
        }
        try (InputStream in = c.getAssets().open(BUNDLED + name)) {
            return readAll(in);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The reciters as kept on the TV, else the app's own copy, without asking mp3quran.net (the settings editor's
     * export runs on the main thread); when the TV has read none yet, a read starts in the background for next time.
     */
    static List<Reciter> cachedReciters(Context c) {
        File ar = new File(dir(c, false), "reciters-ar.json");
        if (!ar.exists() || System.currentTimeMillis() - ar.lastModified() > LIST_MAX_AGE) {
            Context app = c.getApplicationContext();
            new Thread(() -> {
                try {
                    reciters(app, false);
                    surahNames(app, true);
                    surahNames(app, false);
                } catch (Exception e) {
                    Log.i(TAG, "reciter list not read: " + e);
                }
            }, "awan-reciters").start();
        }
        try {
            return both(c);
        } catch (Exception e) {
            Log.i(TAG, "kept reciter list: " + e);
            return new ArrayList<>();
        }
    }

    /** When the kept reciter list was read (ms), 0 when there is none. */
    static long recitersAt(Context c) {
        return new File(dir(c, false), "reciters-ar.json").lastModified();
    }

    /** The surah names as kept on the TV, else the app's own copy (no network), index 1..114; null where not known. */
    static String[] cachedSurahNames(Context c, boolean ar) {
        return cachedSurahNames(c, ar ? "ar" : "en");
    }

    /** As above in any of the screens' languages (suwar-<lang>.json); all null for one mp3quran.net lacks. */
    static String[] cachedSurahNames(Context c, String lang) {
        String text = listText(c, "suwar-" + lang + ".json");
        String[] out = new String[115];
        if (text == null) return out;
        try {
            JSONArray list = new JSONObject(text).getJSONArray("suwar");
            for (int i = 0; i < list.length(); i++) {
                JSONObject s = list.getJSONObject(i);
                int n = s.getInt("id");
                if (n >= 1 && n <= 114) out[n] = s.getString("name").trim();
            }
        } catch (Exception e) {
            // Kept as none.
        }
        return out;
    }

    /** The surah names from mp3quran.net (cached with the reciters; offline, the app's own copy), index 1..114. */
    static String[] surahNames(Context c, boolean ar) {
        return surahNames(c, ar ? "ar" : "en");
    }

    /** As above in any of the screens' languages mp3quran.net has; all null for another. Not on the main thread. */
    static String[] surahNames(Context c, String lang) {
        String code = Languages.mp3quran(lang);
        if (code == null) return new String[115];
        File f = new File(dir(c, false), "suwar-" + lang + ".json");
        try {
            if (!f.exists()) save(f, get(API + "suwar?language=" + code));
        } catch (IOException e) {
            // Offline: the app's own copy.
        }
        return cachedSurahNames(c, lang);
    }

    private static List<Reciter> parse(String json, Map<String, String[]> english) throws Exception {
        List<Reciter> out = new ArrayList<>();
        JSONArray list = new JSONObject(json).getJSONArray("reciters");
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.getJSONObject(i);
            JSONArray moshafs = r.optJSONArray("moshaf");
            if (moshafs == null) continue;
            for (int k = 0; k < moshafs.length(); k++) {
                JSONObject m = moshafs.getJSONObject(k);
                String server = m.optString("server", "");
                if (!server.startsWith("https://")) continue;
                String[] parts = m.optString("surah_list", "").split(",");
                List<Integer> nums = new ArrayList<>();
                for (String p : parts) {
                    try {
                        int n = Integer.parseInt(p.trim());
                        if (n >= 1 && n <= 114) nums.add(n);
                    } catch (NumberFormatException skip) {
                        // Not a surah number.
                    }
                }
                if (nums.isEmpty()) continue;
                int[] surahs = new int[nums.size()];
                for (int j = 0; j < surahs.length; j++) surahs[j] = nums.get(j);
                Arrays.sort(surahs);
                Reciter rec = new Reciter(r.getInt("id"), m.getInt("id"), r.getString("name").trim(), "",
                    m.optString("name", "").trim(), "", server, surahs);
                String[] en = english == null ? null : english.get(rec.key());
                out.add(en == null ? rec : new Reciter(rec.id, rec.moshaf, rec.ar, en[0], rec.moshafAr, en[1], server, surahs));
            }
        }
        return out;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setConnectTimeout(TIMEOUT_MS);
        http.setReadTimeout(TIMEOUT_MS);
        http.setRequestProperty("User-Agent", "AwanTV (https://awanpro.app)");
        try {
            if (http.getResponseCode() != 200) throw new IOException("HTTP " + http.getResponseCode());
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = http.getInputStream()) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    body.write(buf, 0, n);
                    if (body.size() > 8 * 1024 * 1024) throw new IOException("too large");
                }
            }
            String text = body.toString("UTF-8");
            new JSONObject(text); // Only a whole, valid answer replaces the kept list.
            return text;
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        } finally {
            http.disconnect();
        }
    }

    private static void save(File f, String text) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) throw new IOException("rename");
    }

    private static String read(File f) throws IOException {
        try (InputStream in = new java.io.FileInputStream(f)) {
            return readAll(in);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    // ---------------------------------------------------------------- files

    /**
     * The saved recitations' folder (Music/Awan TV on the TV's shared storage); with {@code media} false, a place for
     * the lists. Null for the media folder when the TV has no shared storage.
     */
    @SuppressWarnings("deprecation") // getExternalStoragePublicDirectory: the path itself, read and written as files.
    static File dir(Context c, boolean media) {
        if (!media) {
            File f = new File(c.getFilesDir(), "quran");
            f.mkdirs();
            return f;
        }
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) return null;
        File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Awan TV");
        f.mkdirs();
        return f;
    }

    /** Where a surah downloads before it is complete: the app's own folder (Music takes audio files only). */
    private static File partFile(Context c, String key, int surah) {
        File d = c.getExternalFilesDir("quran-part");
        if (d == null) return null;
        d.mkdirs();
        return new File(d, key + "-" + String.format(Locale.US, "%03d.mp3.part", surah));
    }

    static File file(Context c, String key, int surah) {
        File d = dir(c, true);
        return d == null ? null : new File(new File(d, key), String.format(Locale.US, "%03d.mp3", surah));
    }

    static boolean saved(Context c, String key, int surah) {
        File f = file(c, key, surah);
        return f != null && f.length() > 0;
    }

    /**
     * The surah numbers saved for a recording, in order. Kept for a few seconds: the screensaver's radio line asks
     * every second while a saved recording plays, and each look is a folder listing on the TV's storage.
     */
    static int[] savedSurahs(Context c, String key) {
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (savedCache) {
            Object[] hit = savedCache.get(key);
            if (hit != null && now - (Long) hit[0] < 5000) return (int[]) hit[1];
        }
        int[] fresh = listSaved(c, key);
        synchronized (savedCache) {
            savedCache.put(key, new Object[] {now, fresh});
        }
        return fresh;
    }

    private static final Map<String, Object[]> savedCache = new java.util.HashMap<>();

    /** Forgets the kept listings (a surah saved or deleted). */
    static void savedChanged() {
        synchronized (savedCache) {
            savedCache.clear();
        }
    }

    private static int[] listSaved(Context c, String key) {
        File d = dir(c, true);
        String[] names = d == null ? null : new File(d, key).list();
        if (names == null) return new int[0];
        List<Integer> n = new ArrayList<>();
        for (String s : names) {
            if (!s.endsWith(".mp3") || s.length() != 7) continue;
            try {
                if (new File(new File(d, key), s).length() > 0) n.add(Integer.parseInt(s.substring(0, 3)));
            } catch (NumberFormatException skip) {
                // Not ours.
            }
        }
        int[] out = new int[n.size()];
        for (int i = 0; i < out.length; i++) out[i] = n.get(i);
        Arrays.sort(out);
        return out;
    }

    /** Every recording with at least one saved surah (folder names), in the order they were first saved. */
    static List<String> savedKeys(Context c) {
        List<String> out = new ArrayList<>();
        File d = dir(c, true);
        File[] dirs = d == null ? null : d.listFiles(File::isDirectory);
        if (dirs == null) return out;
        Arrays.sort(dirs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : dirs) if (savedSurahs(c, f.getName()).length > 0) out.add(f.getName());
        return out;
    }

    /** Bytes saved for one recording, or for all with key null. */
    static long savedBytes(Context c, String key) {
        File d = dir(c, true);
        if (d == null) return 0;
        long total = 0;
        File[] dirs = key == null ? d.listFiles(File::isDirectory) : new File[] {new File(d, key)};
        if (dirs == null) return 0;
        for (File dd : dirs) {
            File[] files = dd.listFiles();
            if (files != null) for (File f : files) total += f.length();
        }
        return total;
    }

    /** Bytes free on the TV's storage where the recitations go. */
    static long freeBytes(Context c) {
        File d = dir(c, true);
        if (d == null) return 0;
        try {
            return new StatFs(d.getPath()).getAvailableBytes();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /** Bytes on the TV's storage where the recitations go, free or not. */
    static long totalBytes(Context c) {
        File d = dir(c, true);
        if (d == null) return 0;
        try {
            return new StatFs(d.getPath()).getTotalBytes();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /**
     * Deletes one saved surah, or all of a recording's with surah 0; queued ones of it are dropped too. Returns the
     * files Android would not let go (saved before a reinstall): {@link #askToDelete} asks for them.
     */
    static List<File> delete(Context c, String key, int surah) {
        List<File> kept = new ArrayList<>();
        synchronized (Library.class) {
            List<String> q = queue(c);
            String current = Prefs.of(c).getString("dl_current", null);
            for (int i = q.size() - 1; i >= 0; i--) {
                if (matches(q.get(i), key, surah)) q.remove(i);
            }
            setQueue(c, q);
            if (current != null && matches(current, key, surah)) cancelCurrent(c);
        }
        savedChanged();
        File d = dir(c, true);
        if (d == null) return kept;
        File folder = new File(d, key);
        if (surah == 0) {
            File[] files = folder.listFiles();
            if (files != null) for (File f : files) if (!f.delete() && f.exists()) kept.add(f);
            folder.delete();
        } else {
            File f = file(c, key, surah);
            if (f != null && !f.delete() && f.exists()) kept.add(f);
            File part = partFile(c, key, surah);
            if (part != null) part.delete();
        }
        savedChanged();
        pump(c);
        return kept;
    }

    /** Every saved recitation (Remove Awan TV, asked first); returns what Android kept, as {@link #delete}. */
    static List<File> deleteAll(Context c) {
        List<File> kept = new ArrayList<>();
        for (String key : savedKeys(c)) kept.addAll(delete(c, key, 0));
        File d = dir(c, true);
        if (d != null) d.delete();
        return kept;
    }

    /**
     * Files saved before a reinstall belong, for Android 11 and later, to the old install: only Android's own question
     * ("Allow Awan TV to delete...?") lets them go. Started from an activity, whose onActivityResult hears the answer;
     * false when there is nothing to ask (or no way to).
     */
    static boolean askToDelete(Activity a, List<File> files, int requestCode) {
        if (files.isEmpty() || Build.VERSION.SDK_INT < 30) return false;
        List<Uri> uris = new ArrayList<>();
        for (File f : files) {
            try (Cursor cur = a.getContentResolver().query(MediaStore.Files.getContentUri("external"),
                    new String[] {MediaStore.MediaColumns._ID}, MediaStore.MediaColumns.DATA + "=?",
                    new String[] {f.getAbsolutePath()}, null)) {
                if (cur != null && cur.moveToFirst()) {
                    uris.add(android.content.ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), cur.getLong(0)));
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "no media entry for " + f, e);
            }
        }
        if (uris.isEmpty()) return false;
        try {
            a.startIntentSenderForResult(MediaStore.createDeleteRequest(a.getContentResolver(), uris).getIntentSender(),
                requestCode, null, 0, 0, 0);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "could not ask to delete", e);
            return false;
        }
    }

    private static boolean matches(String item, String key, int surah) {
        return item.equals(key + "/" + surah) || (surah == 0 && item.startsWith(key + "/"));
    }

    // ---------------------------------------------------------------- the queue

    /** Queues surahs of a recording that are neither saved nor queued already, in their order, and starts if idle. */
    static void enqueue(Context c, Reciter r, int[] surahs) {
        synchronized (Library.class) {
            List<String> q = queue(c);
            String current = Prefs.of(c).getString("dl_current", null);
            for (int s : surahs) {
                String item = r.key() + "/" + s;
                if (saved(c, r.key(), s) || q.contains(item) || item.equals(current)) continue;
                q.add(item);
            }
            setQueue(c, q);
            remember(c, r);
            Prefs.of(c).edit().putBoolean("dl_waiting_space", false).apply();
        }
        pump(c);
    }

    /** The queued items ("key/surah"), the one downloading not included. */
    static List<String> queue(Context c) {
        String s = Prefs.of(c).getString("dl_queue", "");
        List<String> out = new ArrayList<>();
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private static void setQueue(Context c, List<String> q) {
        Prefs.of(c).edit().putString("dl_queue", android.text.TextUtils.join(",", q)).apply();
    }

    /** True when the queue stopped because the TV has less than {@link #RESERVE} free. */
    static boolean waitingForSpace(Context c) {
        return Prefs.of(c).getBoolean("dl_waiting_space", false);
    }

    /** True when the TV's download manager refused the last download (turned off on this TV, say). */
    static boolean cannotDownload(Context c) {
        return Prefs.of(c).getBoolean("dl_broken", false);
    }

    /** The item downloading now ("key/surah"), or null. */
    static String current(Context c) {
        return Prefs.of(c).getString("dl_current", null);
    }

    /** How far the current download is, 0..100; -1 when unknown or none. */
    static int progress(Context c) {
        long id = Prefs.of(c).getLong("dl_id", -1);
        DownloadManager dm = c.getSystemService(DownloadManager.class);
        if (id < 0 || dm == null) return -1;
        try (Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cur == null || !cur.moveToFirst()) return -1;
            long done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            return total > 0 ? (int) Math.min(100, done * 100 / total) : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * Starts the next queued surah when nothing is downloading. Called after each download, when something is
     * queued, and when the app starts (the download manager carries on by itself across restarts).
     */
    static void pump(Context c) {
        Context app = c.getApplicationContext();
        synchronized (Library.class) {
            SharedPreferences p = Prefs.of(app);
            DownloadManager dm = app.getSystemService(DownloadManager.class);
            if (dm == null) return;
            long id = p.getLong("dl_id", -1);
            if (id >= 0) {
                int status = status(dm, id);
                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
                        || status == DownloadManager.STATUS_PAUSED) return;
                // Finished while nobody heard it (or gone): settle it as Done would.
                settle(app, dm, id, status);
            }
            List<String> q = queue(app);
            if (q.isEmpty()) {
                p.edit().putBoolean("dl_waiting_space", false).apply();
                return;
            }
            if (freeBytes(app) < RESERVE + 256L * 1024 * 1024) {
                p.edit().putBoolean("dl_waiting_space", true).apply();
                Log.i(TAG, "waiting: less than 2 GB free");
                return;
            }
            String item = q.get(0);
            String key = item.substring(0, item.indexOf('/'));
            int surah = Integer.parseInt(item.substring(item.indexOf('/') + 1));
            String server = p.getString("dl_server_" + key, null);
            File target = file(app, key, surah);
            if (server == null || target == null) {
                // Not downloadable any more (its names were cleared): drop it and go on.
                q.remove(0);
                setQueue(app, q);
                pump(app);
                return;
            }
            File part = partFile(app, key, surah);
            if (part == null) {
                p.edit().putBoolean("dl_broken", true).apply();
                return;
            }
            part.delete();
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(server + String.format(Locale.US, "%03d.mp3", surah)))
                .setDestinationUri(Uri.fromFile(part))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                .setAllowedOverMetered(true)
                .setTitle("Awan TV " + item)
                .addRequestHeader("User-Agent", "AwanTV (https://awanpro.app)");
            try {
                long newId = dm.enqueue(r);
                q.remove(0);
                setQueue(app, q);
                p.edit().putLong("dl_id", newId).putString("dl_current", item)
                    .putBoolean("dl_waiting_space", false).putBoolean("dl_broken", false).apply();
                Log.i(TAG, "downloading " + item);
            } catch (RuntimeException e) {
                // Refused (the TV's download manager is off, say): the queue stays as it is, and the screen says so.
                Log.w(TAG, "not queued: " + item, e);
                p.edit().remove("dl_id").remove("dl_current").putBoolean("dl_broken", true).apply();
            }
        }
    }

    /** The finished .part into Music: renamed where the storage allows it, else copied over and removed. */
    private static boolean moveInto(File part, File target) {
        File folder = target.getParentFile();
        if (folder != null) folder.mkdirs();
        if (part.renameTo(target)) return true;
        File tmp = new File(target.getParentFile(), "." + target.getName() + ".copy.mp3");
        try (InputStream in = new java.io.FileInputStream(part); java.io.OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException e) {
            Log.w(TAG, "could not move " + part + " to " + target, e);
            tmp.delete();
            return false;
        }
        part.delete();
        if (tmp.renameTo(target)) return true;
        tmp.delete();
        return false;
    }

    private static int status(DownloadManager dm, long id) {
        try (Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cur == null || !cur.moveToFirst()) return -1;
            return cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** A finished download: the .part becomes the .mp3, or a failure goes back to the queue's end (up to 3 times). */
    private static void settle(Context c, DownloadManager dm, long id, int status) {
        SharedPreferences p = Prefs.of(c);
        String item = p.getString("dl_current", null);
        p.edit().remove("dl_id").remove("dl_current").apply();
        if (item == null) return;
        String key = item.substring(0, item.indexOf('/'));
        int surah = Integer.parseInt(item.substring(item.indexOf('/') + 1));
        File target = file(c, key, surah);
        File part = partFile(c, key, surah);
        if (target == null || part == null) return;
        if (status == DownloadManager.STATUS_SUCCESSFUL && part.length() > 0) {
            if (!moveInto(part, target)) part.delete();
            // Seen by the TV's other players at once (Android 10 and older index files only when told).
            android.media.MediaScannerConnection.scanFile(c, new String[] {target.getPath()}, new String[] {"audio/mpeg"}, null);
            savedChanged();
            p.edit().remove("dl_tries_" + item).apply();
            Log.i(TAG, "saved " + item);
            return;
        }
        part.delete();
        dm.remove(id);
        int tries = p.getInt("dl_tries_" + item, 0) + 1;
        if (tries < 3) {
            p.edit().putInt("dl_tries_" + item, tries).apply();
            List<String> q = queue(c);
            q.add(item);
            setQueue(c, q);
        } else {
            p.edit().remove("dl_tries_" + item).apply();
        }
        Log.i(TAG, "failed " + item + " (" + tries + ")");
    }

    private static void cancelCurrent(Context c) {
        SharedPreferences p = Prefs.of(c);
        long id = p.getLong("dl_id", -1);
        DownloadManager dm = c.getSystemService(DownloadManager.class);
        if (id >= 0 && dm != null) dm.remove(id);
        p.edit().remove("dl_id").remove("dl_current").apply();
    }

    /** Stops every download and empties the queue (Remove Awan TV). */
    static void stopAll(Context c) {
        synchronized (Library.class) {
            cancelCurrent(c);
            setQueue(c, new ArrayList<>());
        }
    }

    // ---------------------------------------------------------------- what a saved recording is called

    /** Keeps a recording's server and names, so its downloads and its station work without the list. */
    private static void remember(Context c, Reciter r) {
        Prefs.of(c).edit().putString("dl_server_" + r.key(), r.server)
            .putString("lib_ar_" + r.key(), r.ar).putString("lib_en_" + r.key(), r.en.isEmpty() ? r.ar : r.en)
            .putString("lib_mar_" + r.key(), r.moshafAr).putString("lib_men_" + r.key(), r.moshafEn).apply();
        SharedPreferences.Editor more = Prefs.of(c).edit();
        for (Map.Entry<String, String[]> e : r.more.entrySet()) {
            more.putString("lib_" + e.getKey() + "_" + r.key(), e.getValue()[0]).putString("lib_m" + e.getKey() + "_" + r.key(), e.getValue()[1]);
        }
        more.apply();
    }

    /** The recording's reciter in the screen's language: its own name where mp3quran.net has one, else English's. */
    static String name(Context c, String key, Text t) {
        if (t.ar || "en".equals(t.lang)) return name(c, key, t.ar);
        String s = nameIn(c, key, t.lang);
        return s == null ? name(c, key, false) : s;
    }

    /** The recording's reciter in one of the screens' other languages as kept, else as its list has it; null: none. */
    static String nameIn(Context c, String key, String lang) {
        String s = Prefs.of(c).getString("lib_" + lang + "_" + key, null);
        if (s == null || s.isEmpty()) {
            Reciter r = known(c, key);
            String[] own = r == null ? null : r.more.get(lang);
            s = own == null ? null : own[0];
        }
        return s == null || s.isEmpty() ? null : s;
    }

    /** The recording's reciter as kept with it, else as the lists have it, else its key ("r30-m30"). */
    static String name(Context c, String key, boolean ar) {
        String s = Prefs.of(c).getString((ar ? "lib_ar_" : "lib_en_") + key, null);
        if (s != null) return s;
        Reciter r = known(c, key);
        return r == null ? key : ar || r.en.isEmpty() ? r.ar : r.en;
    }

    /** The recording's riwayah and style as kept with it ("حفص عن عاصم - مرتل"), else as the lists have it, else "". */
    static String moshafName(Context c, String key, boolean ar) {
        String s = Prefs.of(c).getString((ar ? "lib_mar_" : "lib_men_") + key, null);
        if (s != null) return s;
        Reciter r = known(c, key);
        return r == null ? "" : ar ? r.moshafAr : r.moshafEn;
    }

    private static final Object KNOWN_LOCK = new Object();
    /** The lists by key, read once when a name was not kept; null until then and after the TV reads the lists again. */
    private static Map<String, Reciter> known;

    /**
     * A recording whose names were not kept (the app removed and installed again: Android wipes its data, the files in
     * Music stay), found by its key in the lists and kept again; null when no list has it.
     */
    private static Reciter known(Context c, String key) {
        Map<String, Reciter> m;
        synchronized (KNOWN_LOCK) {
            if (known == null) {
                known = new HashMap<>();
                try {
                    for (Reciter r : both(c)) known.put(r.key(), r);
                } catch (Exception e) {
                    Log.i(TAG, "no reciter list for the names: " + e);
                }
            }
            m = known;
        }
        Reciter r = m.get(key);
        if (r != null) remember(c, r);
        return r;
    }

    // ---------------------------------------------------------------- the download manager's word

    /** Hears each download finish (manifest receiver: also when the app is not running) and starts the next. */
    public static final class Done extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            Context app = context.getApplicationContext();
            synchronized (Library.class) {
                if (id < 0 || id != Prefs.of(app).getLong("dl_id", -2)) return;
                DownloadManager dm = app.getSystemService(DownloadManager.class);
                if (dm == null) return;
                // Only a download that has really ended (the broadcast can come from any app).
                int status = status(dm, id);
                if (status != DownloadManager.STATUS_SUCCESSFUL && status != DownloadManager.STATUS_FAILED) return;
                settle(app, dm, id, status);
            }
            pump(app);
        }
    }
}
