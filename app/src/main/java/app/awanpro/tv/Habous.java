package app.awanpro.tv;

import android.content.Context;
import android.content.SharedPreferences;

import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.data.DateComponents;
import com.batoulapps.adhan.internal.SolarTime;

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
 * Morocco's Ministry of Habous and Islamic Affairs (1.8.0). The ministry publishes a timetable for each of its 191
 * localities, not how it computes them; the rules were recovered from the tables by Afnon 0.8 (Habous.java by Moussaab
 * Waziz, MIT, whose times() and asr() these are) and agree with Mihrab's and with what the ministry's 2026 tables show:
 *
 * <ul>
 * <li>Fajr: the sun 19° below the horizon, rounded down to the minute.</li>
 * <li>Sunrise and Maghrib: at the locality's own horizon (arc minutes below it, east and west), rounded.</li>
 * <li>Dhuhr: transit + 5 minutes, rounded.</li>
 * <li>Asr: shadow = noon shadow + the object (twice for Hanafi), with the declination at Asr itself, rounded.</li>
 * <li>Isha: 17°, rounded.</li>
 * </ul>
 *
 * Each locality's point and horizons are in assets/habous/localities.tsv, fitted to the ministry's tables by
 * tools/AwanTV-build-habous.py: its published minute for 99% of the 53,862 times of 2026-09-26 to 2026-11-11, the rest
 * a minute off. A place in Morocco takes a locality's table (the one named like it near by, else the nearest; Settings
 * > Prayer times > Ministry table changes it), as its mosques do. Elsewhere, the method chosen by hand, the place's
 * own point at the ministry's coastal horizon ({@link #COAST}), so Maghrib never comes before the ministry's would.
 */
final class Habous {
    private Habous() {}

    /** The ministry's lowest horizons ({sunrise, Maghrib} arc minutes: its coastal localities at 100 m). */
    static final int[] COAST = {84, 96};

    /**
     * Seconds the ministry's tables put Asr and Isha after these rules (Afnon's finding, on its October 2026 tables;
     * the fit of all 191 localities agrees).
     */
    private static final int ASR_LATER = 12, ISHA_LATER = 3;

    /** The six times (PrayerDay.FAJR..ISHA) on their whole minute, or null where the sun doesn't reach 19°/17°. */
    static Date[] times(Coordinates where, DateComponents day, Madhab madhab, int[] horizon) {
        SolarTime s = new SolarTime(day, where);
        double asr = asr(s, where, day, madhab == Madhab.HANAFI ? 2 : 1);
        double[] hours = {
            s.hourAngle(-19.0, false),
            s.hourAngle(-horizon[0] / 60.0, false),
            s.transit + 5 / 60.0,
            asr + ASR_LATER / 3600.0,
            s.hourAngle(-horizon[1] / 60.0, true),
            s.hourAngle(-17.0, true) + ISHA_LATER / 3600.0,
        };
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(day.year, day.month - 1, day.day);
        long midnight = c.getTimeInMillis();
        Date[] out = new Date[6];
        for (int i = 0; i < 6; i++) {
            if (Double.isNaN(hours[i])) return null;
            long ms = midnight + Math.round(hours[i] * 3_600_000);
            out[i] = new Date((i == PrayerDay.FAJR ? ms : ms + 30_000) / 60_000 * 60_000);
        }
        return out;
    }

    /** Asr in hours UTC, its altitude from the declination at Asr (three passes are enough to settle on it). */
    private static double asr(SolarTime s, Coordinates where, DateComponents day, double shadow) {
        double jd = julianDay(day.year, day.month, day.day);
        double h = s.transit + 3;
        for (int i = 0; i < 3; i++) {
            double tangent = Math.abs(where.latitude - declination(jd + h / 24));
            h = s.hourAngle(Math.toDegrees(Math.atan(1.0 / (shadow + Math.tan(Math.toRadians(tangent))))), true);
            if (Double.isNaN(h)) return h;
        }
        return h;
    }

    // adhan-java's SolarCoordinates and CalendricalHelper (Meeus, Astronomical Algorithms), which it keeps
    // package-private: the same formulas, so Asr's declination agrees with the rest of its solar positions.

    private static double julianDay(int year, int month, int day) {
        int y = month > 2 ? year : year - 1;
        int m = month > 2 ? month : month + 12;
        int a = y / 100;
        int b = 2 - a + (a / 4);
        return (int) (365.25 * (y + 4716)) + (int) (30.6001 * (m + 1)) + day + b - 1524.5;
    }

    private static double declination(double jd) {
        double t = (jd - 2451545.0) / 36525;
        double l0 = unwind(280.4664567 + 36000.76983 * t + 0.0003032 * t * t);
        double mRad = Math.toRadians(unwind(357.52911 + 35999.05029 * t - 0.0001537 * t * t));
        double center = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(mRad)
            + (0.019993 - 0.000101 * t) * Math.sin(2 * mRad) + 0.000289 * Math.sin(3 * mRad);
        double omega = 125.04 - 1934.136 * t;
        double lambda = Math.toRadians(unwind(l0 + center - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega))));
        double e0 = 23.439291 - 0.013004167 * t - 0.0000001639 * t * t + 0.0000005036 * t * t * t;
        double e = Math.toRadians(e0 + 0.00256 * Math.cos(Math.toRadians(omega)));
        return Math.toDegrees(Math.asin(Math.sin(e) * Math.sin(lambda)));
    }

    private static double unwind(double a) {
        double v = a - 360 * Math.floor(a / 360);
        return v < 0 ? v + 360 : v;
    }

    // ---------------------------------------------------------------- the ministry's localities

    /** One of the ministry's localities: its id on habous.gov.ma, names, fitted point and horizons. */
    static final class Locality {
        final int id;
        final String ar, latin;
        final double lat, lng;
        final int[] horizon;

        Locality(int id, String ar, String latin, double lat, double lng, int sunrise, int maghrib) {
            this.id = id;
            this.ar = ar;
            this.latin = latin;
            this.lat = lat;
            this.lng = lng;
            this.horizon = new int[] {sunrise, maghrib};
        }

        String name(boolean arabic) { return arabic ? ar : latin; }
    }

    /** The saved choice of table: a locality's id (Settings > Prayer times > Ministry table); none = automatic. */
    static final String PREF = "habous_city";
    /** A place this near one of the ministry's localities takes its table even outside Morocco's country code (Sebta). */
    private static final double NEAR_KM = 30;

    private static List<Locality> all;

    /** The 191 localities (assets/habous/localities.tsv), read once. */
    static synchronized List<Locality> all(Context c) {
        if (all == null) {
            try (InputStream in = c.getApplicationContext().getAssets().open("habous/localities.tsv")) {
                all = parse(in);
            } catch (IOException e) {
                android.util.Log.w("AwanHabous", "localities: " + e);
                all = Collections.emptyList();
            }
        }
        return all;
    }

    /** localities.tsv: '#' lines, then id, Arabic name, Latin name, lat, lon, sunrise and Maghrib arc minutes. */
    static List<Locality> parse(InputStream in) throws IOException {
        List<Locality> out = new ArrayList<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t");
            if (f.length < 7) continue;
            out.add(new Locality(Integer.parseInt(f[0]), f[1], f[2], Double.parseDouble(f[3]), Double.parseDouble(f[4]),
                Integer.parseInt(f[5]), Integer.parseInt(f[6])));
        }
        return out;
    }

    /** The localities by their distance from a point, nearest first. */
    static List<Locality> nearest(Context c, double lat, double lng) {
        List<Locality> out = new ArrayList<>(all(c));
        Collections.sort(out, (a, b) -> Double.compare(Places.km(lat, lng, a.lat, a.lng), Places.km(lat, lng, b.lat, b.lng)));
        return out;
    }

    /**
     * The table the saved place takes, or null when it takes none (outside Morocco): Morocco by the place's country,
     * or within {@link #NEAR_KM} of a locality. The one saved in Settings if any, else {@link #automatic}.
     */
    static Locality table(Context c) {
        Prefs.City city = Prefs.city(c);
        String key = city.id + "|" + city.lat + "," + city.lng + "|" + city.ar + "|" + Prefs.of(c).getString("custom_cc", "")
            + "|" + Prefs.of(c).getInt(PREF, 0);
        synchronized (Habous.class) {
            if (key.equals(tableKey)) return tableNow;
        }
        Locality l = find(c, city);
        synchronized (Habous.class) {
            tableKey = key;
            tableNow = l;
        }
        return l;
    }

    // PrayerDay asks on every check: the last answer, for the same place and choice.
    private static String tableKey;
    private static Locality tableNow;

    private static Locality find(Context c, Prefs.City city) {
        List<Locality> near = nearest(c, city.lat, city.lng);
        if (near.isEmpty()) return null;
        String cc = Prefs.CUSTOM.equals(city.id) ? Prefs.of(c).getString("custom_cc", "") : "";
        boolean morocco = "MA".equals(cc) || "EH".equals(cc)
            || Places.km(city.lat, city.lng, near.get(0).lat, near.get(0).lng) <= NEAR_KM;
        if (!morocco) return null;
        int saved = Prefs.of(c).getInt(PREF, 0);
        if (saved != 0) for (Locality l : near) if (l.id == saved) return l;
        return automatic(city, near);
    }

    /** The table the saved place takes with no choice saved (Settings' list keeps it automatic when picked). */
    static Locality automatic(Context c) {
        Prefs.City city = Prefs.city(c);
        List<Locality> near = nearest(c, city.lat, city.lng);
        return near.isEmpty() ? null : automatic(city, near);
    }

    /** The locality named like the place within {@link #NEAR_KM} (Marrakech, not its neighbour Tassoultante), else the nearest. */
    private static Locality automatic(Prefs.City city, List<Locality> near) {
        String a = Places.normalise(city.ar), e = Places.normalise(city.en);
        for (Locality l : near) {
            if (Places.km(city.lat, city.lng, l.lat, l.lng) > NEAR_KM) break;
            String la = Places.normalise(l.ar), ll = Places.normalise(l.latin);
            if (alike(a, la) || alike(e, ll) || alike(a, ll) || alike(e, la)) return l;
        }
        return near.get(0);
    }

    /** The same name, or one or two letters apart in a long one (Marrakesh, Marrakech). */
    private static boolean alike(String x, String y) {
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        return Math.min(x.length(), y.length()) >= 6 && distance(x, y) <= 2;
    }

    private static int distance(String x, String y) {
        int[] prev = new int[y.length() + 1], cur = new int[y.length() + 1];
        for (int j = 0; j <= y.length(); j++) prev[j] = j;
        for (int i = 1; i <= x.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= y.length(); j++) {
                int cost = x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[y.length()];
    }

    /** Saves a locality's table (Settings), or automatic (0). */
    static void choose(Context c, int id) {
        SharedPreferences.Editor e = Prefs.of(c).edit();
        if (id == 0) e.remove(PREF); else e.putInt(PREF, id);
        e.apply();
    }
}
