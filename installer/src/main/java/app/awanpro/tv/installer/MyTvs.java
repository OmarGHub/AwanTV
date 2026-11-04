package app.awanpro.tv.installer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * My TVs (1.12.0): the TVs this phone has installed Awan TV on or set up again, kept on the phone only (the installer's
 * prefs, "my_tvs"), so their version can be read and Awan TV updated on several at once. Only these: such a TV already
 * trusts this phone (Always allow, or paired), so reading it shows nothing on its screen; another TV on the network
 * would ask "Allow debugging?", which must not appear on someone else's TV unasked. Another is added through Find my
 * TV, which saves it once connected.
 */
final class MyTvs {
    private MyTvs() {}

    private static final String PREFS = "installer", KEY = "my_tvs";

    /** What My TVs knows of the TV now (not saved). */
    enum State { CHECKING, OLDER, SAME, NEWER, NONE, NO_ANSWER, PAIR, MOVED }

    static final class Tv {
        /** ro.serialno: the same TV at another address is still this one. */
        String id;
        String name, model, host;
        int classic, tls;
        /** Awan TV's version there when last read; "" none. */
        String version = "";
        // Now, not saved:
        State state = State.CHECKING;
        boolean chosen;
        String result;
        int percent = -1;

        String shown() {
            return name != null && !name.isEmpty() ? name : model != null && !model.isEmpty() ? model : host;
        }
    }

    static List<Tv> load(Context c) {
        List<Tv> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Tv t = new Tv();
                t.id = o.optString("id");
                t.name = o.optString("name");
                t.model = o.optString("model");
                t.host = o.optString("host");
                t.classic = o.optInt("classic");
                t.tls = o.optInt("tls");
                t.version = o.optString("version");
                if (!t.id.isEmpty() && !t.host.isEmpty()) out.add(t);
            }
        } catch (JSONException ignored) {
            // Unreadable: an empty list, written again at the next save.
        }
        return out;
    }

    private static void save(Context c, List<Tv> tvs) {
        JSONArray a = new JSONArray();
        try {
            for (Tv t : tvs) {
                a.put(new JSONObject().put("id", t.id).put("name", t.name).put("model", t.model).put("host", t.host)
                        .put("classic", t.classic).put("tls", t.tls).put("version", t.version));
            }
        } catch (JSONException ignored) {
            return;
        }
        prefs(c).edit().putString(KEY, a.toString()).apply();
    }

    /** A TV this phone just connected to (Find my TV's confirmation): added, or its address and name brought up to date. */
    static void remember(Context c, String id, String name, String model, String host, int classic, int tls, String version) {
        if (id == null || id.isEmpty() || host == null) return;
        List<Tv> tvs = load(c);
        Tv t = null;
        for (Tv x : tvs) if (x.id.equals(id)) t = x;
        if (t == null) {
            t = new Tv();
            t.id = id;
            tvs.add(t);
        }
        t.name = name == null ? "" : name;
        t.model = model == null ? "" : model;
        t.host = host;
        if (classic > 0) t.classic = classic;
        if (tls > 0) t.tls = tls;
        if (version != null) t.version = version;
        save(c, tvs);
    }

    /** Awan TV's version on a saved TV, after reading it or updating it. */
    static void version(Context c, String id, String version) {
        if (id == null || version == null) return;
        List<Tv> tvs = load(c);
        for (Tv t : tvs) if (t.id.equals(id)) t.version = version;
        save(c, tvs);
    }

    static void forget(Context c, String id) {
        List<Tv> tvs = load(c);
        List<Tv> keep = new ArrayList<>();
        for (Tv t : tvs) if (!t.id.equals(id)) keep.add(t);
        save(c, keep);
    }

    static int count(Context c) {
        return load(c).size();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
