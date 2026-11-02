package app.awanpro.tv;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every reciter channel mp3quran.net streams around the clock, not only the ten {@link Radio#STATIONS}
 * Afnon writes by hand. The same source and the same streams (backup.qurango.net/radio/&lt;name&gt;): the list is
 * its API (mp3quran.net/api/v3/radios, in Arabic and English), kept on the TV for a week and read again in the
 * background when Settings > Radio is open. Settings > Radio > Station > Reciters shows Afnon's ten first, then
 * these. A channel's station id is the last part of its stream address, as for Afnon's ten, so the two never
 * double up.
 */
final class Radios {
    private Radios() {}

    private static final String API = "https://mp3quran.net/api/v3/radios?language=";
    private static final long MAX_AGE = 7L * 24 * 3600_000;
    private static final int TIMEOUT_MS = 15_000;
    private static volatile boolean fetching;
    private static List<Radio.Station> cached;
    private static long cachedAt;

    /** The channels beyond Afnon's ten, by name; empty until the list has been read once. Quick: from the TV. */
    static synchronized List<Radio.Station> extra(Context c) {
        File ar = file(c, true);
        if (cached != null && ar.lastModified() == cachedAt) return cached;
        List<Radio.Station> out = new ArrayList<>();
        try {
            Map<Integer, String> english = new HashMap<>();
            File en = file(c, false);
            if (en.exists()) {
                JSONArray list = new JSONObject(read(en)).getJSONArray("radios");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.getJSONObject(i);
                    english.put(r.getInt("id"), r.getString("name").trim());
                }
            }
            Set<String> known = new HashSet<>();
            for (Radio.Station s : Radio.STATIONS) known.add(s.id);
            if (ar.exists()) {
                JSONArray list = new JSONObject(read(ar)).getJSONArray("radios");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.getJSONObject(i);
                    String url = r.optString("url", "");
                    String id = idOf(url);
                    if (id == null || known.contains(id)) continue;
                    known.add(id);
                    String nameAr = clean(r.getString("name"));
                    String nameEn = english.containsKey(r.getInt("id")) ? clean(english.get(r.getInt("id"))) : nameAr;
                    out.add(Radio.Station.reciter(id, nameAr, nameEn));
                }
            }
        } catch (Exception e) {
            Log.i(Library.TAG, "radios list: " + e);
        }
        // In Arabic alphabetical order (alif with or without hamza together).
        java.text.Collator order = java.text.Collator.getInstance(new Locale("ar"));
        Collections.sort(out, (a, b) -> order.compare(a.ar, b.ar));
        cached = out;
        cachedAt = ar.lastModified();
        return out;
    }

    /** A name as it should read: without the decorations some carry ("***قصص الأنبياء***"). */
    private static String clean(String name) {
        return name.replaceAll("[*_~]+", " ").replaceAll("\\s+", " ").trim();
    }

    /** A channel by its station id, or null when the list does not have it (any more). */
    static Radio.Station find(Context c, String id) {
        for (Radio.Station s : extra(c)) if (s.id.equals(id)) return s;
        return null;
    }

    /** Reads the list again in the background when it is a week old (or missing). */
    static void refresh(Context c) {
        Context app = c.getApplicationContext();
        if (fetching || System.currentTimeMillis() - file(app, true).lastModified() < MAX_AGE) return;
        fetching = true;
        new Thread(() -> {
            try {
                save(file(app, true), get(API + "ar"));
                save(file(app, false), get(API + "eng"));
            } catch (Exception e) {
                Log.i(Library.TAG, "radios list not read: " + e);
            } finally {
                fetching = false;
            }
        }, "awan-radios").start();
    }

    /** "mishary_alafasi" from https://backup.qurango.net/radio/mishary_alafasi; null for anything not https. */
    private static String idOf(String url) {
        if (!url.startsWith("https://")) return null;
        String path = url.replaceAll("/+$", "");
        String id = path.substring(path.lastIndexOf('/') + 1);
        return id.isEmpty() || id.startsWith(Radio.SAVED) ? null : id.toLowerCase(Locale.ROOT);
    }

    private static File file(Context c, boolean ar) {
        return new File(Library.dir(c, false), ar ? "radios-ar.json" : "radios-en.json");
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
                    if (body.size() > 4 * 1024 * 1024) throw new IOException("too large");
                }
            }
            String text = body.toString("UTF-8");
            new JSONObject(text).getJSONArray("radios"); // Only a whole, valid list replaces the kept one.
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
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }
}
