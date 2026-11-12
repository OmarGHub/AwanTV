package app.awanpro.tv;

import android.content.Context;

import com.batoulapps.adhan.Madhab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every city in the world of 15,000 people or more (34,000 of them), searchable by name in Arabic or
 * English, for Settings > Location > Any place in the world ({@link PlaceActivity}).
 *
 * <p>The list is assets/cities/cities.tsv, built by tools/AwanTV-build-cities.py from GeoNames (geonames.org,
 * CC BY 4.0; credited in Settings > About). Each line: name, Arabic name, latitude, longitude, country code,
 * population, search key. The Arabic name is the hand-written one where the city is in {@link Prefs#CITIES},
 * else the likeliest spelling among GeoNames' alternate names, so the viewer can still change it before saving.
 * Country names come from Android in the screen's language.
 *
 * <p>Read once, off the main thread, the first time a search runs; kept while the app lives (a few MB).
 */
final class Places {
    private Places() {}

    static final class Place {
        final String name, ar, cc, key;
        final double lat, lng;
        final long population;

        Place(String name, String ar, double lat, double lng, String cc, long population, String key) {
            this.name = name;
            this.ar = ar;
            this.lat = lat;
            this.lng = lng;
            this.cc = cc;
            this.population = population;
            this.key = key;
        }

        /** The name in the screen's language: Arabic where one is known. */
        String name(Text t) {
            return t.ar && !ar.isEmpty() ? ar : name;
        }

        String country(Text t) {
            return Places.country(cc, t);
        }
    }

    private static volatile List<Place> all;

    /** Loads the list if it is not loaded yet; slow the first time, so not on the main thread. */
    static List<Place> load(Context c) throws IOException {
        List<Place> got = all;
        if (got != null) return got;
        synchronized (Places.class) {
            if (all != null) return all;
            List<Place> out = new ArrayList<>(34_500);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    c.getApplicationContext().getAssets().open("cities/cities.tsv"), StandardCharsets.UTF_8), 1 << 16)) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] f = line.split("\t", -1);
                    if (f.length < 7) continue;
                    try {
                        out.add(new Place(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]), f[4],
                            Long.parseLong(f[5]), "|" + f[6] + "|"));
                    } catch (NumberFormatException skip) {
                        // A broken line: leave it out.
                    }
                }
            }
            all = out;
            return out;
        }
    }

    /** The listed place nearest a point (the first run's location); slow the first time, so not on the main thread. */
    static Place nearest(Context c, double lat, double lng) throws IOException {
        Place best = null;
        double bestKm = Double.MAX_VALUE;
        for (Place p : load(c)) {
            double d = km(lat, lng, p.lat, p.lng);
            if (d < bestKm) {
                bestKm = d;
                best = p;
            }
        }
        return best;
    }

    /** Great-circle distance in kilometres. */
    static double km(double lat1, double lng1, double lat2, double lng2) {
        double a = Math.toRadians(lat1), b = Math.toRadians(lat2);
        double h = Math.pow(Math.sin((b - a) / 2), 2)
            + Math.cos(a) * Math.cos(b) * Math.pow(Math.sin(Math.toRadians(lng2 - lng1) / 2), 2);
        return 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /**
     * Up to {@code max} cities for what was typed: names that start with it first, then names that contain it;
     * larger cities first within each. Empty for fewer than two letters.
     */
    static List<Place> search(Context c, String typed, int max) throws IOException {
        String q = normalise(typed);
        List<Place> out = new ArrayList<>();
        if (q.length() < 2) return out;
        List<Place> list = load(c);
        String start = "|" + q, word = " " + q;
        List<Place> later = new ArrayList<>();
        // The list is largest first, so the first found in each group are the largest.
        for (Place p : list) {
            if (p.key.contains(start)) {
                out.add(p);
                if (out.size() >= max) return out;
            } else if (later.size() < max && (p.key.contains(word) || p.key.contains(q))) {
                later.add(p);
            }
        }
        for (Place p : later) {
            if (out.size() >= max) break;
            out.add(p);
        }
        return out;
    }

    /**
     * The search key of a name, exactly as tools/AwanTV-build-cities.py key() builds it: lower case, no accents
     * or harakat, one form of alif, taa marbuta as haa, alif maqsura as yaa, and no "ال" at the start of a word.
     */
    static String normalise(String s) {
        s = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int type = Character.getType(ch);
            if (type == Character.NON_SPACING_MARK) continue;
            if ((ch >= 'ً' && ch <= 'ْ') || ch == 'ٰ' || ch == 'ـ') continue;
            switch (ch) {
                case 'أ': case 'إ': case 'آ': case 'ٱ': ch = 'ا'; break;
                case 'ة': ch = 'ه'; break;
                case 'ى': case 'ئ': case 'ی': ch = 'ي'; break;
                case 'ؤ': ch = 'و'; break;
                case 'ک': ch = 'ك'; break;
                case '-': case '\'': case '’': ch = ' '; break;
                default: break;
            }
            b.append(ch);
        }
        StringBuilder out = new StringBuilder(b.length());
        for (String w : b.toString().trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(w.startsWith("ال") && w.length() > 3 ? w.substring(2) : w);
        }
        return out.toString();
    }

    /** A country's name from its ISO code, in Arabic or English (Android's own names). */
    static String country(String cc, boolean ar) {
        if (cc == null || cc.isEmpty()) return "";
        String name = new Locale("", cc).getDisplayCountry(ar ? new Locale("ar") : Locale.ENGLISH);
        if ("PS".equals(cc)) return ar ? "فلسطين" : "Palestine";
        return name;
    }

    /**
     * A country in the screen's language: Arabic and English as above; any other language Android's own name in it
     * (languages plan, phase 3), Palestine ours (t.palestine()).
     */
    static String country(String cc, Text t) {
        if (t.ar || "en".equals(t.lang)) return country(cc, t.ar);
        if (cc == null || cc.isEmpty()) return "";
        if ("PS".equals(cc)) return t.palestine();
        return new Locale("", cc).getDisplayCountry(new Locale(t.lang));
    }

    // ---------------------------------------------------------------- the calculation a country's mosques follow

    /**
     * The method most of a country's mosques follow, for the countries Afnon's city list already sets (each with
     * its reasons in {@link Prefs#CITIES}); null for the rest, where the viewer is asked to check it.
     */
    static Method method(String cc) {
        if (cc == null) return null;
        switch (cc) {
            case "SA": return Method.UMM_AL_QURA;
            case "EG": return Method.EGYPTIAN;
            case "AE": return Method.DUBAI;
            case "KW": return Method.KUWAIT;
            case "QA": return Method.QATAR;
            // Oman's Ministry of Awqaf (Method.OMAN_AWQAF).
            case "OM": return Method.OMAN_AWQAF;
            // Morocco's Ministry of Habous: its own timetables, which Laayoune and Dakhla (GeoNames' EH) follow too.
            case "MA": case "EH": return Method.MOROCCO_HABOUS;
            // Iran's official times: the Institute of Geophysics, University of Tehran (Method.TEHRAN).
            case "IR": return Method.TEHRAN;
            case "US": case "CA": return Method.NORTH_AMERICA;
            case "IN": case "PK": case "BD": case "LK": return Method.KARACHI;
            case "ID": case "SG": case "BN": case "TH": return Method.SINGAPORE;
            case "MY": return Method.MOON_SIGHTING_COMMITTEE;
            case "JO": case "PS": case "LB": case "SY": case "IQ": case "BH": case "YE": case "MV":
            case "JP": case "KR": case "TR": case "GB": case "FR": case "DE": case "NL": case "BE": case "AT":
            case "CH": case "IT": case "ES": case "BA": case "KE": case "NG": case "ZA": case "AU": case "NZ":
                return Method.MUSLIM_WORLD_LEAGUE;
            default: return null;
        }
    }

    /** The Asr school most of a country's mosques follow: Hanafi in Pakistan, Bangladesh and most of India. */
    static Madhab madhab(String cc) {
        return "PK".equals(cc) || "BD".equals(cc) || "IN".equals(cc) ? Madhab.HANAFI : Madhab.SHAFI;
    }
}
