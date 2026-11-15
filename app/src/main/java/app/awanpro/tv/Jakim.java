package app.awanpro.tv;

import android.content.Context;
import android.content.SharedPreferences;

import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.data.DateComponents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Malaysia's official times (1.26.0a): JAKIM's e-Solat timetables, one per zone (59 zones), worked out offline.
 *
 * JAKIM gives a whole zone one time: Fajr and Isha at 18° (Fajr since 25 February 2020, by the 116th Muzakarah of
 * the National Council for Islamic Religious Affairs; 20° before), sunrise and Maghrib at −0°50′, Dhuhr at the
 * transit, Asr by the standard shadow, each taken at the zone's far side and given a precaution of up to two minutes,
 * the minute rounded up (Determination of Zone-based Solah Times: A Practice in Malaysia, 2021; Mihrab's
 * documentation describes reading them off the tables the same way). assets/jakim/zones.tsv holds, per zone and
 * prayer, up to three points and a margin read off JAKIM's own timetables (2025-2026, checked on 2024) by
 * tools/AwanTV-build-jakim; {@link JakimSun} computes the raw times with the same code there and here. The time is the
 * latest of the points' raw times (the earliest, for sunrise, which ends Fajr) plus the margin, its minute rounded up.
 *
 * A place in Malaysia takes the zone of its nearest e-Solat town, or the one saved in Settings (Prayer times > JAKIM
 * zone). Outside Malaysia the method is its angles with the usual precaution ({@link Method#JAKIM}).
 */
final class Jakim {
    private Jakim() {}

    /** Settings: the zone chosen ("WLY01"...), none = automatic. */
    static final String PREF = "jakim_zone";
    static final String AUTOMATIC = "automatic";

    /** Within this of an e-Solat town, a place takes its zone whatever its country (a border town). */
    private static final double NEAR_KM = 30;

    /** One prayer's rule in a zone. */
    static final class Rule {
        final int variant;
        final double margin;
        final double[][] points;

        Rule(int variant, double margin, double[][] points) {
            this.variant = variant;
            this.margin = margin;
            this.points = points;
        }
    }

    /** An e-Solat town: name, point, zone. */
    static final class Town {
        final String name;
        final double lat, lng;
        final Zone zone;

        Town(String name, double lat, double lng, Zone zone) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
            this.zone = zone;
        }
    }

    /** A zone: its code, state, towns and the six rules. */
    static final class Zone {
        final String code, state;
        final List<Town> towns = new ArrayList<>();
        final Rule[] rules = new Rule[6];

        Zone(String code, String state) {
            this.code = code;
            this.state = state;
        }

        /** "WLY01 · Kuala Lumpur, Putrajaya": the code and its towns, as e-Solat names them (the same in every language). */
        String name() {
            StringBuilder b = new StringBuilder(code).append(" · ");
            for (int i = 0; i < towns.size(); i++) b.append(i == 0 ? "" : ", ").append(towns.get(i).name);
            return b.toString();
        }

        /**
         * The code and as many of its towns as fit about chars letters, then "…" (Settings' list: a zone of nine towns
         * ran to twice the list's width).
         */
        String name(int chars) {
            StringBuilder b = new StringBuilder(code).append(" · ");
            for (int i = 0; i < towns.size(); i++) {
                String next = (i == 0 ? "" : ", ") + towns.get(i).name;
                if (i > 0 && b.length() + next.length() > chars) return b.append("…").toString();
                b.append(next);
            }
            return b.toString();
        }

        /** "WLY01 · Kuala Lumpur": the code and its first town (Settings' row). */
        String shortName() {
            return towns.isEmpty() ? code : code + " · " + towns.get(0).name;
        }

        /** The nearest of its towns to a point, km. */
        double km(double lat, double lng) {
            double best = Double.MAX_VALUE;
            for (Town t : towns) best = Math.min(best, Places.km(lat, lng, t.lat, t.lng));
            return best;
        }

        /** The six times (PrayerDay.FAJR..ISHA) on their whole minute, or null if one can't be worked out. */
        Date[] times(DateComponents day, Madhab madhab) {
            double shadow = madhab == Madhab.HANAFI ? 2 : 1;
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.clear();
            c.set(day.year, day.month - 1, day.day);
            // Malaysia's clock (UTC+8, no summer time): the raw times are minutes after its midnight.
            long midnight = c.getTimeInMillis() - 8 * 3_600_000L;
            Date[] out = new Date[6];
            for (int p = 0; p < 6; p++) {
                Rule r = rules[p];
                double v = Double.NaN;
                for (double[] pt : r.points) {
                    double t = JakimSun.time(r.variant, pt[0], pt[1], day.year, day.month, day.day, 8, shadow);
                    if (Double.isNaN(t)) return null;
                    v = Double.isNaN(v) ? t : p == PrayerDay.SUNRISE ? Math.min(v, t) : Math.max(v, t);
                }
                long minute = (long) Math.ceil(v + r.margin - 1e-9);
                out[p] = new Date(midnight + minute * 60_000L);
            }
            return out;
        }
    }

    private static List<Zone> all;
    private static List<Town> towns;

    /** The zones as assets/jakim/zones.tsv lists them (by code). */
    static synchronized List<Zone> all(Context c) {
        if (all == null) {
            try (InputStream in = c.getApplicationContext().getAssets().open("jakim/zones.tsv")) {
                all = parse(in);
            } catch (IOException e) {
                android.util.Log.w("AwanJakim", "zones: " + e);
                all = Collections.emptyList();
            }
            towns = new ArrayList<>();
            for (Zone z : all) towns.addAll(z.towns);
        }
        return all;
    }

    /** zones.tsv: '#' lines, then zone, state, towns (name|lat|lon;...), and six rules "variant margin lat,lon ...". */
    static List<Zone> parse(InputStream in) throws IOException {
        List<Zone> out = new ArrayList<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t");
            if (f.length < 9) continue;
            Zone z = new Zone(f[0], f[1]);
            for (String t : f[2].split(";")) {
                String[] q = t.split("\\|");
                z.towns.add(new Town(q[0], Double.parseDouble(q[1]), Double.parseDouble(q[2]), z));
            }
            for (int p = 0; p < 6; p++) {
                String[] q = f[3 + p].split(" ");
                double[][] pts = new double[q.length - 2][];
                for (int i = 2; i < q.length; i++) {
                    String[] ll = q[i].split(",");
                    pts[i - 2] = new double[] {Double.parseDouble(ll[0]), Double.parseDouble(ll[1])};
                }
                z.rules[p] = new Rule(Integer.parseInt(q[0]), Double.parseDouble(q[1]), pts);
            }
            out.add(z);
        }
        return out;
    }

    /** The zones by the distance of their nearest town from a point, nearest first. */
    static List<Zone> nearest(Context c, double lat, double lng) {
        List<Zone> out = new ArrayList<>(all(c));
        Collections.sort(out, (a, b) -> Double.compare(a.km(lat, lng), b.km(lat, lng)));
        return out;
    }

    /** A zone by its code, or null. */
    static Zone byCode(Context c, String code) {
        if (code == null) return null;
        for (Zone z : all(c)) if (z.code.equals(code)) return z;
        return null;
    }

    /**
     * The zone the saved place takes, or null when it takes none (outside Malaysia): Malaysia by the place's country,
     * or within {@link #NEAR_KM} of an e-Solat town. The one saved in Settings if any, else {@link #automatic}.
     */
    static Zone zone(Context c) {
        Prefs.City city = Prefs.city(c);
        String key = city.id + "|" + city.lat + "," + city.lng + "|" + Prefs.of(c).getString("custom_cc", "")
            + "|" + Prefs.of(c).getString(PREF, "");
        synchronized (Jakim.class) {
            if (key.equals(zoneKey)) return zoneNow;
        }
        Zone z = find(c, city);
        synchronized (Jakim.class) {
            zoneKey = key;
            zoneNow = z;
        }
        return z;
    }

    // PrayerDay asks on every check: the last answer, for the same place and choice.
    private static String zoneKey;
    private static Zone zoneNow;

    private static Zone find(Context c, Prefs.City city) {
        Zone auto = automatic(c, city);
        if (auto == null) return null;
        String cc = Prefs.CUSTOM.equals(city.id) ? Prefs.of(c).getString("custom_cc", "") : "";
        if (!"MY".equals(cc) && auto.km(city.lat, city.lng) > NEAR_KM) return null;
        Zone saved = byCode(c, Prefs.of(c).getString(PREF, null));
        return saved != null ? saved : auto;
    }

    /** The zone the saved place takes with no choice saved: its nearest e-Solat town's. */
    static Zone automatic(Context c) {
        return automatic(c, Prefs.city(c));
    }

    private static Zone automatic(Context c, Prefs.City city) {
        all(c);
        Town best = null;
        double km = Double.MAX_VALUE;
        for (Town t : towns) {
            double d = Places.km(city.lat, city.lng, t.lat, t.lng);
            if (d < km) { km = d; best = t; }
        }
        return best == null ? null : best.zone;
    }

    /** Saves a zone (Settings), or automatic (null). */
    static void choose(Context c, String code) {
        SharedPreferences.Editor e = Prefs.of(c).edit();
        if (code == null || AUTOMATIC.equals(code)) e.remove(PREF); else e.putString(PREF, code);
        e.apply();
    }
}
