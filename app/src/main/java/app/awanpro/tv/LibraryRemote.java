package app.awanpro.tv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saved recitations and the station from the settings editor (tools/AwanTV-editor.ps1, 1.9.0), through
 * {@link SettingsTransfer} (adb only). On export the TV writes {@link #FILE}: its storage, what is saved, downloading
 * and queued, the stations it can play and its kept reciter list (no network: that list is mp3quran.net's as the TV
 * last read it). A file the editor sends may carry "downloads": recordings to queue, each with all the TV needs
 * to fetch it (its server, names and surahs), so a TV that never read the list can take it; then {@link Library}
 * downloads as when picked on the TV. Nothing is deleted from here: deleting stays on the TV (Android's own question).
 */
final class LibraryRemote {
    private LibraryRemote() {}

    static final String FILE = "awantv-library.json";

    /** One recording to download and its surahs. */
    static final class Download {
        final Library.Reciter reciter;
        final int[] surahs;

        Download(Library.Reciter reciter, int[] surahs) {
            this.reciter = reciter;
            this.surahs = surahs;
        }
    }

    /** What the editor's Saved recitations page and its station list show for this TV. */
    static String state(Context c) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("app", c.getPackageName());
        out.put("free", Library.freeBytes(c));
        out.put("total", Library.totalBytes(c));
        out.put("reserve", Library.RESERVE);
        out.put("station", Prefs.of(c).getString("station", "cairo"));
        out.put("waiting_space", Library.waitingForSpace(c));
        out.put("broken", Library.cannotDownload(c));

        List<Library.Reciter> list = Library.cachedReciters(c);
        Map<String, Library.Reciter> byKey = new HashMap<>();
        for (Library.Reciter r : list) byKey.put(r.key(), r);

        JSONArray saved = new JSONArray();
        for (String key : Library.savedKeys(c)) {
            JSONObject o = names(c, key);
            o.put("count", Library.savedSurahs(c, key).length);
            Library.Reciter r = byKey.get(key);
            o.put("of", r == null ? 0 : r.surahs.length);
            saved.put(o);
        }
        out.put("saved", saved);

        String current = Library.current(c);
        if (current != null && current.contains("/")) {
            String key = current.substring(0, current.indexOf('/'));
            JSONObject o = names(c, key);
            o.put("surah", Integer.parseInt(current.substring(current.indexOf('/') + 1)));
            o.put("percent", Library.progress(c));
            out.put("current", o);
        }
        // The queue by recording, in its order: how many surahs wait for each.
        Map<String, Integer> waiting = new java.util.LinkedHashMap<>();
        for (String item : Library.queue(c)) {
            int slash = item.indexOf('/');
            if (slash <= 0) continue;
            String key = item.substring(0, slash);
            Integer n = waiting.get(key);
            waiting.put(key, n == null ? 1 : n + 1);
        }
        JSONArray queue = new JSONArray();
        for (Map.Entry<String, Integer> e : waiting.entrySet()) {
            JSONObject o = names(c, e.getKey());
            o.put("count", e.getValue());
            queue.put(o);
        }
        out.put("queue", queue);

        // The stations by group, as Settings > Radio lists them.
        JSONArray stations = new JSONArray();
        for (Radio.Station s : Radio.STATIONS) stations.put(station(s.reciter ? "reciters" : "stations", s));
        for (Radio.Station s : Radios.extra(c)) stations.put(station("reciters", s));
        for (String key : Library.savedKeys(c)) {
            JSONObject o = names(c, key);
            o.put("group", "saved");
            o.put("id", Radio.SAVED + key);
            o.put("count", Library.savedSurahs(c, key).length);
            stations.put(o);
        }
        out.put("stations", stations);

        JSONArray reciters = new JSONArray();
        for (Library.Reciter r : list) reciters.put(reciter(r));
        out.put("reciters", reciters);
        out.put("reciters_at", list.isEmpty() ? 0 : Library.recitersAt(c));
        out.put("suwar_ar", new JSONArray(java.util.Arrays.asList(Library.cachedSurahNames(c, true))));
        out.put("suwar_en", new JSONArray(java.util.Arrays.asList(Library.cachedSurahNames(c, false))));
        // Compact: the reciter list alone is hundreds of recordings with their surahs.
        return out.toString();
    }

    private static JSONObject names(Context c, String key) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("key", key);
        o.put("ar", Library.name(c, key, true));
        o.put("en", Library.name(c, key, false));
        o.put("moshaf_ar", Library.moshafName(c, key, true));
        o.put("moshaf_en", Library.moshafName(c, key, false));
        return o;
    }

    private static JSONObject station(String group, Radio.Station s) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("group", group);
        o.put("id", s.id);
        o.put("ar", s.ar);
        o.put("en", s.en);
        o.put("where_ar", s.whereAr);
        o.put("where_en", s.whereEn);
        return o;
    }

    private static JSONObject reciter(Library.Reciter r) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", r.id);
        o.put("moshaf", r.moshaf);
        o.put("key", r.key());
        o.put("ar", r.ar);
        o.put("en", r.en);
        o.put("moshaf_ar", r.moshafAr);
        o.put("moshaf_en", r.moshafEn);
        o.put("server", r.server);
        JSONArray surahs = new JSONArray();
        for (int n : r.surahs) surahs.put(n);
        o.put("surahs", surahs);
        return o;
    }

    /**
     * The file's "downloads", checked: each a recording on mp3quran.net (https, its own servers) and surahs 1..114 it
     * has. Any mistake refuses the whole file, as for a setting.
     */
    static List<Download> parse(JSONArray in) throws IOException {
        List<Download> out = new ArrayList<>();
        if (in == null) return out;
        for (int i = 0; i < in.length(); i++) {
            JSONObject o = in.optJSONObject(i);
            if (o == null) throw new IOException("downloads: item " + (i + 1) + " is not { ... }");
            int id = o.optInt("id", -1), moshaf = o.optInt("moshaf", -1);
            String server = o.optString("server", ""), ar = o.optString("ar", "").trim();
            if (id < 0 || moshaf < 0 || ar.isEmpty()) throw new IOException("downloads: item " + (i + 1) + " needs id, moshaf and ar");
            String host;
            try {
                java.net.URL url = new java.net.URL(server);
                host = "https".equals(url.getProtocol()) ? url.getHost() : "";
            } catch (java.net.MalformedURLException e) {
                host = "";
            }
            if (!host.equals("mp3quran.net") && !host.endsWith(".mp3quran.net")) {
                throw new IOException("downloads: item " + (i + 1) + ": not one of mp3quran.net's servers");
            }
            JSONArray has = o.optJSONArray("surahs"), want = o.optJSONArray("want");
            Set<Integer> offered = numbers(has, i);
            if (offered.isEmpty()) throw new IOException("downloads: item " + (i + 1) + " has no surahs");
            Set<Integer> chosen = want == null ? offered : numbers(want, i);
            for (int n : chosen) if (!offered.contains(n)) throw new IOException("downloads: item " + (i + 1) + ": surah " + n + " is not in this recording");
            int[] all = toArray(offered), pick = toArray(chosen);
            Library.Reciter r = new Library.Reciter(id, moshaf, ar, o.optString("en", "").trim(), o.optString("moshaf_ar", "").trim(),
                o.optString("moshaf_en", "").trim(), server, all);
            out.add(new Download(r, pick));
        }
        return out;
    }

    private static Set<Integer> numbers(JSONArray a, int item) throws IOException {
        Set<Integer> out = new LinkedHashSet<>();
        if (a == null) return out;
        for (int k = 0; k < a.length(); k++) {
            int n = a.optInt(k, -1);
            if (n < 1 || n > 114) throw new IOException("downloads: item " + (item + 1) + ": " + a.opt(k) + " is not a surah (1 to 114)");
            out.add(n);
        }
        return out;
    }

    private static int[] toArray(Set<Integer> s) {
        int[] out = new int[s.size()];
        int i = 0;
        for (int n : s) out[i++] = n;
        java.util.Arrays.sort(out);
        return out;
    }

    /** Queues them as Settings > Radio > Saved recitations would; returns how many surahs were newly queued. */
    static int enqueue(Context c, List<Download> downloads) {
        int before = Library.queue(c).size() + (Library.current(c) != null ? 1 : 0);
        for (Download d : downloads) Library.enqueue(c, d.reciter, d.surahs);
        int after = Library.queue(c).size() + (Library.current(c) != null ? 1 : 0);
        return Math.max(0, after - before);
    }
}
