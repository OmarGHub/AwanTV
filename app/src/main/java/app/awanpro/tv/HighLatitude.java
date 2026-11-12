package app.awanpro.tv;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerAdjustments;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.data.CalendarUtil;
import com.batoulapps.adhan.data.DateComponents;
import com.batoulapps.adhan.data.TimeComponents;
import com.batoulapps.adhan.internal.SolarTime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The share of the night at 45° above 48° (Settings > Prayer times > Custom calculation > High latitudes, 1.24.0): Fajr
 * and Isha take the same share of the night as at 45° that date: the Muslim World League's Islamic Fiqh Council, 9th
 * session, resolution 6 (1406 AH), for 48° to 66° ("بالقياس النسبي"), as Egypt's Dar al-Ifta, fatwa 2259 (2012), "يعين
 * وقت صلاة العشاء والفجر بالقياس النسبي على نظيريهما في ليل أقرب مكان". The custom calculation's other rules are
 * adhan-java's own (Prefs.adhanRule).
 *
 * The share is taken only where it is needed: where the angle is not reached that night (the rulings' "where the signs
 * vanish"), or where Isha would come only after the first third of the night (Fajr before the last third: the end of
 * Isha's chosen time in the Shafi'i school, and the hardship the 2021 Istanbul congress allows estimating for), and then
 * never later than the sign itself. Applied all year, it would put a winter Isha in Reykjavik an hour early while the
 * sign is plain. Isha by minutes after Maghrib (Umm al-Qura, Qatar, or chosen) is left as it is. Days with no sunrise or
 * no sunset keep PrayerDay's 45°.
 *
 * Two more, from bodies that publish tables (1.24.0b):
 * <ul>
 * <li>The International Prayer Times Congress (Istanbul, 2021: Turkey's Diyanet with the European Council for Fatwa and
 * Research), as Diyanet's tables apply it ({@link Congress}).
 * <li>Makkah's clock times where the fast would pass twenty hours or the sun doesn't rise or set, as northern Norway's
 * mosques (Tromsø's Alnor Senter and seven towns, 2012) ({@link #makkah}).
 * </ul>
 */
final class HighLatitude {
    private HighLatitude() {}

    /** Below this latitude the method's angles always hold (the Fiqh Council's first zone ends at 48°). */
    static final double FROM = 48;

    /**
     * {Fajr, Isha} by the rule for p's day (with the viewer's and the method's minutes, rounded as adhan-java rounds);
     * p's own for adhan-java's rules, below 48° or when the day can't be worked out.
     */
    static Date[] fajrIsha(String rule, Coordinates where, DateComponents date, CalculationParameters params, PrayerTimes p) {
        Date[] own = {p.fajr, p.isha};
        if (Math.abs(where.latitude) <= FROM) return own;
        // The UK Moonsighting Committee's method brings its own season rule (adhan-java's).
        if (params.method == CalculationMethod.MOON_SIGHTING_COMMITTEE) return own;
        if (Prefs.HIGH_CONGRESS.equals(rule)) return Congress.fajrIsha(where, date, params, p, false);
        // Makkah's: on the local days, Alnor's tables carry one share on both sides (see makkah).
        if (Prefs.HIGH_MAKKAH.equals(rule)) return Congress.fajrIsha(where, date, params, p, true);
        if (!Prefs.HIGH_PROPORTION.equals(rule)) return own;
        Night n = Night.of(where, date, params);
        Night o = Night.of(new Coordinates(Math.copySign(45, where.latitude), where.longitude), date, params);
        if (n == null || o == null || o.fajr == null || o.isha == null) return own;
        Date fajr = n.fajr, isha = n.isha;
        long night = n.nextSunrise.getTime() - n.sunset.getTime();
        long otherNight = o.nextSunrise.getTime() - o.sunset.getTime();
        long before = o.sunrise.getTime() - o.fajr.getTime();
        long after = o.isha.getTime() - o.sunset.getTime();
        Date safeFajr = new Date(n.sunrise.getTime() - before * night / otherNight);
        Date safeIsha = new Date(n.sunset.getTime() + after * night / otherNight);
        if (fajr == null || fajr.getTime() < n.sunrise.getTime() - night / 3) {
            if (fajr == null || fajr.before(safeFajr)) fajr = safeFajr;
        }
        if (isha == null || isha.getTime() > n.sunset.getTime() + night / 3) {
            if (isha == null || isha.after(safeIsha)) isha = safeIsha;
        }
        PrayerAdjustments a = params.adjustments, m = params.methodAdjustments;
        Date f = CalendarUtil.roundedMinute(new Date(fajr.getTime() + (a.fajr + m.fajr) * 60_000L));
        // Isha by minutes after Maghrib: adhan-java's, untouched.
        Date i = params.ishaInterval > 0 ? p.isha
            : CalendarUtil.roundedMinute(new Date(isha.getTime() + (a.isha + m.isha) * 60_000L));
        return new Date[] {f, i};
    }

    /** A day's sun at a place: sunrise, sunset, the next sunrise, and Fajr and Isha by the angles (null: not reached). */
    private static final class Night {
        Date sunrise, sunset, nextSunrise, fajr, isha;

        static Night of(Coordinates where, DateComponents date, CalculationParameters params) {
            SolarTime today = new SolarTime(date, where);
            DateComponents next = DateComponents.fromUTC(CalendarUtil.add(CalendarUtil.resolveTime(date), 1, Calendar.DATE));
            SolarTime tomorrow = new SolarTime(next, where);
            Night n = new Night();
            n.sunrise = at(date, today.sunrise);
            n.sunset = at(date, today.sunset);
            n.nextSunrise = at(next, tomorrow.sunrise);
            if (n.sunrise == null || n.sunset == null || n.nextSunrise == null) return null;
            n.fajr = at(date, today.hourAngle(-params.fajrAngle, false));
            n.isha = at(date, today.hourAngle(-params.ishaAngle, true));
            return n;
        }
    }

    private static Date at(DateComponents date, double hours) {
        TimeComponents t = TimeComponents.fromDouble(hours);
        return t == null ? null : t.dateComponents(date);
    }

    /**
     * The International Prayer Times Congress (Istanbul, 26–27 September 2021; TRT Haber's text of its decisions,
     * trthaber.com/haber/guncel/612071), as Diyanet's own tables apply it: where the dawn no longer forms (the "gap"
     * around midsummer), the share of the night that a third of the shar'i night (Maghrib to the dawn) took on the last
     * day it formed is carried: Isha that share of the night after Maghrib, Fajr the same share times Fajr's angle over
     * Isha's (18/16 in Diyanet's) before sunrise. The night is the day's own (24 h less sunrise to Maghrib). Next to the
     * gap the true time moves in a straight line to the carried one, over the days on which it lies within twenty
     * minutes of the carried time at the gap's edge. Where the dawn forms all summer but midsummer's Isha comes later
     * than the third of its night, midsummer alone is the gap.
     *
     * Read from the description in mihrab-methods' docs/methods.md ("Sweden and Diyanet", Hassan El Ghamri, MPL-2.0;
     * no code taken), which checks it against Diyanet's tables. The gap and the transitions take the sun as Diyanet does
     * (once a day, Meeus); the other days are the sign itself. Checked here against Diyanet's printed times for
     * Longyearbyen (78°N), 1 to 8 March 2027, to the minute, and for Oslo and Stockholm in winter (within two minutes;
     * with the viewer's minutes set to Diyanet's: sunrise −7, Dhuhr +5, Asr +4, Maghrib +7). Not taken: its five-hour
     * floor on the day and the night (sunrise and Maghrib stay the sun's own); days with no sunrise or sunset keep
     * PrayerDay's 45°, as the congress left the latitudes above 60° for later. The angles are the custom calculation's:
     * Diyanet's are 18° and 16° (17° outside Europe and North America).
     */
    static final class Congress {
        private Congress() {}

        /** Twenty minutes, in hours. */
        private static final double RAMP = 20 / 60.0;

        private static final Map<String, Congress> CACHE = new LinkedHashMap<String, Congress>(8, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Congress> e) { return size() > 8; }
        };

        private Coordinates where;
        private double fajrAngle, ishaAngle;
        /** The minutes on each (the viewer's and the method's), in hours. */
        private double onFajr, onSunrise, onMaghrib, onIsha;
        private boolean ishaByAngle, even;
        /** Fajr's share over Isha's. */
        private double scale;
        /** The gap (days since 1970), or none (gapFrom > gapTo). */
        private long gapFrom = 1, gapTo = 0;
        private double share;
        /** The carried times at the gap's edges, and the far end of each transition ({fajr, isha}; none: edge). */
        private double[] firstTakdir, lastTakdir;
        private final long[] rampBefore = new long[2], rampAfter = new long[2];

        /** even: Fajr takes the same share as Isha (Makkah's local days), not scaled by the angles. */
        static Date[] fajrIsha(Coordinates where, DateComponents date, CalculationParameters params, PrayerTimes p, boolean even) {
            Date[] own = {p.fajr, p.isha};
            long day = epochDay(date);
            Congress season = season(where, params, solstice(where.latitude, day), even);
            double[] t = season.at(day);
            if (t == null) return own;
            // Elsewhere the sign itself (adhan-java's sun), not adhan-java's rule, which also caps a winter Fajr.
            SolarTime sun = null;
            if (Double.isNaN(t[0]) || (season.ishaByAngle && Double.isNaN(t[1]))) sun = new SolarTime(date, where);
            if (Double.isNaN(t[0])) t[0] = sun.hourAngle(-params.fajrAngle, false) + season.onFajr;
            if (season.ishaByAngle && Double.isNaN(t[1])) t[1] = sun.hourAngle(-params.ishaAngle, true) + season.onIsha;
            Date f = HighLatitude.at(date, t[0]);
            Date i = season.ishaByAngle ? HighLatitude.at(date, t[1]) : own[1];
            if (f == null || i == null) return own;
            f = CalendarUtil.roundedMinute(f);
            if (season.ishaByAngle) i = CalendarUtil.roundedMinute(i);
            // Near the days the sun stops setting, Diyanet's sun and adhan-java's part by minutes: Fajr no later than
            // sunrise, Isha no earlier than Maghrib.
            if (f.after(p.sunrise)) f = p.sunrise;
            if (i.before(p.maghrib)) i = p.maghrib;
            return new Date[] {f, i};
        }

        /** {Fajr, Isha} on a day, in hours of its UTC date with the minutes on (NaN: the sign's own); null: no sunrise or sunset. */
        private double[] at(long day) {
            Sun s = sun(day);
            if (Double.isNaN(s.sunrise)) return null;
            if (day >= gapFrom && day <= gapTo) return takdir(s);
            // Elsewhere the sign itself (NaN here: fajrIsha takes adhan-java's sun), but on the days moving to the carried times.
            double[] out = {Double.NaN, Double.NaN};
            boolean before = day < gapFrom;
            if (gapFrom <= gapTo) {
                long edge = before ? gapFrom : gapTo;
                double[] carried = before ? firstTakdir : lastTakdir;
                long[] far = before ? rampBefore : rampAfter;
                for (int k = 0; k < 2; k++) {
                    if (far[k] == edge || (before ? day < far[k] : day > far[k])) continue;
                    double along = (double) Math.abs(day - edge) / Math.abs(far[k] - edge);
                    out[k] = carried[k] + (k == 0 ? RAMP : -RAMP) * along;
                }
            }
            return out;
        }

        private double[] takdir(Sun s) {
            double night = 24 - (s.maghrib - s.sunrise);
            return new double[] {s.sunrise - share * scale * night, s.maghrib + share * night};
        }

        /** The season around one solstice: its gap, the share carried, and the transitions. */
        private static Congress season(Coordinates where, CalculationParameters params, long center, boolean even) {
            PrayerAdjustments a = params.adjustments, m = params.methodAdjustments;
            String key = where.latitude + "," + where.longitude + "," + params.fajrAngle + "," + params.ishaAngle + ","
                + params.ishaInterval + "," + (a.fajr + m.fajr) + "," + (a.sunrise + m.sunrise) + ","
                + (a.maghrib + m.maghrib) + "," + (a.isha + m.isha) + "," + center + "," + even;
            synchronized (CACHE) {
                Congress c = CACHE.get(key);
                if (c == null) {
                    c = new Congress();
                    c.where = where;
                    c.fajrAngle = params.fajrAngle;
                    c.ishaAngle = params.ishaAngle;
                    c.onFajr = (a.fajr + m.fajr) / 60.0;
                    c.onSunrise = (a.sunrise + m.sunrise) / 60.0;
                    c.onMaghrib = (a.maghrib + m.maghrib) / 60.0;
                    c.onIsha = (a.isha + m.isha) / 60.0;
                    c.ishaByAngle = params.ishaInterval <= 0;
                    c.even = even;
                    c.find(center);
                    CACHE.put(key, c);
                }
                return c;
            }
        }

        private void find(long center) {
            scale = even ? 1 : ishaByAngle ? fajrAngle / ishaAngle : 18 / 16.0;
            Sun mid = sun(center);
            if (!mid.signs()) {
                long a = center, z = center;
                while (center - a < 183 && missing(a - 1)) a--;
                while (z - center < 183 && missing(z + 1)) z++;
                Sun last = sun(a - 1);
                if (!last.signs()) return;
                share = (last.fajr + 24 - last.maghrib) / 3 / (24 - (last.maghrib - last.sunrise));
                gapFrom = a;
                gapTo = z;
            } else {
                double third = (mid.fajr + 24 - mid.maghrib) / 3;
                if (!ishaByAngle || mid.isha <= mid.maghrib + third) return;
                share = third / (24 - (mid.maghrib - mid.sunrise));
                gapFrom = gapTo = center;
            }
            Sun first = sun(gapFrom), end = sun(gapTo);
            if (Double.isNaN(first.sunrise) || Double.isNaN(end.sunrise)) { gapFrom = 1; gapTo = 0; return; }
            firstTakdir = takdir(first);
            lastTakdir = takdir(end);
            for (int k = 0; k < (ishaByAngle ? 2 : 1); k++) {
                rampBefore[k] = far(gapFrom, -1, k, firstTakdir[k]);
                rampAfter[k] = far(gapTo, 1, k, lastTakdir[k]);
            }
            if (!ishaByAngle) { rampBefore[1] = gapFrom; rampAfter[1] = gapTo; }
        }

        /** No dawn (or no Isha by angle) that day, or no sunrise or sunset. */
        private boolean missing(long day) {
            return !sun(day).signs();
        }

        /** The far end of a transition: the last day of the run, going out from the edge, within twenty minutes. */
        private long far(long edge, int step, int k, double carried) {
            long far = edge;
            for (long x = edge + step; Math.abs(x - edge) < 183; x += step) {
                Sun s = sun(x);
                double v = k == 0 ? s.fajr : s.isha;
                if (!Double.isNaN(v) && Math.abs(v - carried) <= RAMP) far = x;
                else if (far != edge) break;
            }
            return far;
        }

        /**
         * A day's sunrise, Maghrib, Fajr and Isha (NaN: the sun doesn't get there) with the minutes on, in hours of its
         * UTC date. The sun is taken once a day, at 0h UT, by Meeus's low-precision formulas
         * (Astronomical Algorithms, ch. 25), as Diyanet's tables take it: near the day the dawn stops, adhan-java's sun
         * (moving through the night) puts it far enough off to change the share carried by minutes.
         */
        private Sun sun(long day) {
            if (even) {
                // Alnor's tables are matched on adhan-java's sun (to the minute, where Diyanet's sun puts Isha 4 late).
                SolarTime t = new SolarTime(date(day), where);
                Sun s = new Sun();
                s.sunrise = t.sunrise + onSunrise;
                s.maghrib = t.sunset + onMaghrib;
                s.fajr = t.hourAngle(-fajrAngle, false) + onFajr;
                s.isha = ishaByAngle ? t.hourAngle(-ishaAngle, true) + onIsha : 0;
                return s;
            }
            double t = (day - 10957.5 - 45 / 86400.0) / 36525;   // Julian centuries from J2000.0, at 0h UT less 45 s
            double l0 = ((280.46646 + t * (36000.76983 + 0.0003032 * t)) % 360 + 360) % 360;
            double m = Math.toRadians(357.52911 + 35999.05029 * t);
            double c = (1.914602 - 0.004817 * t) * Math.sin(m) + 0.019993 * Math.sin(2 * m) + 0.000289 * Math.sin(3 * m);
            double omega = Math.toRadians(125.04 - 1934.136 * t);
            double eps = Math.toRadians(23 + (26 + (21.448 - 46.815 * t) / 60) / 60
                + (9.2 * Math.cos(omega) + 0.57 * Math.cos(Math.toRadians(2 * (280.4665 + 36000.7698 * t)))) / 3600);
            double lambda = Math.toRadians(l0 + c);
            double ra = (Math.toDegrees(Math.atan2(Math.cos(eps) * Math.sin(lambda), Math.cos(lambda))) % 360 + 360) % 360;
            double dec = Math.asin(Math.sin(eps) * Math.sin(lambda));
            double eot = (((l0 - ra + 180) % 360 + 360) % 360 - 180) / 15;   // hours
            double transit = 12 - where.longitude / 15 - eot;
            double rise = arc(-0.833, dec);
            Sun s = new Sun();
            s.sunrise = transit - rise + onSunrise;
            s.maghrib = transit + rise + onMaghrib;
            s.fajr = transit - arc(-fajrAngle, dec) + onFajr;
            s.isha = ishaByAngle ? transit + arc(-ishaAngle, dec) + onIsha : 0;
            return s;
        }

        /** Hours from the transit to the sun at an altitude (NaN: it doesn't get there). */
        private double arc(double altitude, double dec) {
            double lat = Math.toRadians(where.latitude);
            double cos = (Math.sin(Math.toRadians(altitude)) - Math.sin(lat) * Math.sin(dec)) / (Math.cos(lat) * Math.cos(dec));
            return Math.abs(cos) > 1 ? Double.NaN : Math.toDegrees(Math.acos(cos)) / 15;
        }

        /** NaN sunrise and Maghrib: the sun doesn't rise or set that day (its dawn may still form, in the polar night). */
        private final class Sun {
            double sunrise, maghrib, fajr, isha;
            boolean signs() { return !Double.isNaN(fajr) && !Double.isNaN(isha) && !Double.isNaN(sunrise); }
        }

        /** The nearest midsummer (21 June in the north, 21 December in the south), in days since 1970. */
        private static long solstice(double latitude, long day) {
            int year = LocalDate.ofEpochDay(day).getYear();
            long best = 0;
            for (int y = year - 1; y <= year + 1; y++) {
                long s = LocalDate.of(y, latitude >= 0 ? 6 : 12, 21).toEpochDay();
                if (y == year - 1 || Math.abs(s - day) < Math.abs(best - day)) best = s;
            }
            return best;
        }

        private static long epochDay(DateComponents d) {
            return LocalDate.of(d.year, d.month, d.day).toEpochDay();
        }

        private static DateComponents date(long day) {
            LocalDate l = LocalDate.ofEpochDay(day);
            return new DateComponents(l.getYear(), l.getMonthValue(), l.getDayOfMonth());
        }
    }


    /**
     * Asr above 48°, where adhan-java's goes wrong near the polar night: it takes the noon shadow from the sun's
     * declination at 0h UT of the date but the moment from the declination at noon, and with the sun a degree or two
     * above the horizon that mismatch is hours (Murmansk, 16 January 2027: twelve hours). In 34,006 cities, every day
     * of 2027: Asr before Dhuhr or after Maghrib on 374 days in 36 cities, more than two minutes off on 13,811.
     * Here, the classical rule as Morocco's ministry and PrayTimes.org take it: the noon shadow from the declination at
     * the transit, and Asr when the shadow is one length (Hanafi: two) more, on the sun as it moves. Where the sun is
     * below the true horizon at noon (seen only by refraction) the shadow never forms: Asr at Dhuhr, as Diyanet's
     * published rule prints it. Checked against Diyanet's printed times (Harstad, Kirkenes, Oslo, Reykjavik, Anchorage,
     * 52 days of 2027): within 3 minutes, where adhan-java's was up to ten hours off. With the viewer's and the
     * method's minutes, rounded as adhan-java rounds, between Dhuhr and Maghrib. At 48° and below adhan-java's own,
     * unchanged (a minute off there at most); days adhan-java can't work out keep 45°'s times (PrayerDay.at45).
     */
    static Date asr(Coordinates where, DateComponents date, CalculationParameters params, PrayerTimes p) {
        if (Math.abs(where.latitude) <= FROM || p.asr == null) return p.asr;
        SolarTime sun = new SolarTime(date, where);
        double jd0 = LocalDate.of(date.year, date.month, date.day).toEpochDay() + 2440587.5;   // 0h UT of the date
        double lat = where.latitude;
        double zenith = Math.abs(lat - declination(jd0 + sun.transit / 24));
        if (zenith >= 90) return p.dhuhr;
        double shadow = params.madhab == Madhab.HANAFI ? 2 : 1;
        double altitude = Math.toDegrees(Math.atan(1 / (shadow + Math.tan(Math.toRadians(zenith)))));
        // The sun goes down from the transit to the sunset, through that altitude once: halve the interval to it.
        double lo = sun.transit, hi = Double.isNaN(sun.sunset) ? sun.transit + 12 : sun.sunset;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (altitude(lat, declination(jd0 + mid / 24), (mid - sun.transit) * 15) > altitude) lo = mid;
            else hi = mid;
        }
        TimeComponents at = TimeComponents.fromDouble(hi);
        if (at == null) return p.asr;
        long minute = 60_000L;
        long raw = at.dateComponents(date).getTime() + (params.adjustments.asr + params.methodAdjustments.asr) * minute;
        Date asr = new Date((raw + 30_000L) / minute * minute);
        if (asr.before(p.dhuhr)) return p.dhuhr;
        if (asr.after(p.maghrib)) return p.maghrib;
        return asr;
    }

    /** The sun's apparent declination in degrees at a Julian day (Meeus, Astronomical Algorithms, ch. 25, as adhan-java). */
    static double declination(double jd) {
        double t = (jd - 2451545.0) / 36525;
        double l0 = 280.46646 + t * (36000.76983 + 0.0003032 * t);
        double m = Math.toRadians(357.52911 + t * (35999.05029 - 0.0001537 * t));
        double c = (1.914602 - t * (0.004817 + 0.000014 * t)) * Math.sin(m) + (0.019993 - 0.000101 * t) * Math.sin(2 * m)
            + 0.000289 * Math.sin(3 * m);
        double omega = Math.toRadians(125.04 - 1934.136 * t);
        double lambda = Math.toRadians(l0 + c - 0.00569 - 0.00478 * Math.sin(omega));
        double eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - 0.001813 * t))) / 60) / 60;
        double eps = Math.toRadians(eps0 + 0.00256 * Math.cos(omega));
        return Math.toDegrees(Math.asin(Math.sin(eps) * Math.sin(lambda)));
    }

    /** The sun's altitude in degrees at a latitude, for its declination and hour angle (degrees). */
    private static double altitude(double latitude, double declination, double hourAngle) {
        double f = Math.toRadians(latitude), d = Math.toRadians(declination);
        return Math.toDegrees(Math.asin(Math.sin(f) * Math.sin(d) + Math.cos(f) * Math.cos(d) * Math.cos(Math.toRadians(hourAngle))));
    }

    /** Makkah (al-Masjid al-Haram). */
    private static final Coordinates MAKKAH = new Coordinates(21.4225, 39.8262);
    private static final ZoneId MAKKAH_CLOCK = ZoneOffset.ofHours(3);

    /**
     * All six times on a day as Makkah's clock shows them, read on the local clock unchanged (Makkah's 04:32 is 04:32
     * here), where the local fast (Fajr to Maghrib) would pass twenty hours, or where the day has no times of its own
     * (local null: no sunrise, sunset or Asr); null on other days. As northern Norway's mosques since 2012 (Tromsø's
     * Alnor Senter and seven more towns, after Sheikh Khalid Almosleh's fatwa: "when local time exceeds 20 hours of
     * fasting", Utrop, 30 May 2012; the fatwa and the tables on alnorsenter.no). Makkah's by Umm al-Qura (Fajr 18.5°,
     * Isha 90 minutes after Maghrib) with the viewer's Asr school and minutes.
     *
     * Checked against Alnor's tables: August (tromso8.pdf) is Makkah's to the minute until the 10th and local from the
     * 11th (here from the 10th, by the twenty hours), and its local days are the carried share above, one share on both
     * sides, on adhan-java's sun (Fajr 15°, Isha 12.5°: within a minute or two all month); November (tromso11.pdf) turns
     * to Makkah's on the first day without sunrise. Not taken: Tromsø's fixed winter Isha at 19:30.
     */
    static Date[] makkah(String rule, Coordinates where, DateComponents date, CalculationParameters params, Date[] local) {
        if (!Prefs.HIGH_MAKKAH.equals(rule) || Math.abs(where.latitude) <= FROM
            || params.method == CalculationMethod.MOON_SIGHTING_COMMITTEE) return null;
        if (local != null && local[PrayerDay.MAGHRIB].getTime() - local[PrayerDay.FAJR].getTime() <= 20 * 3600_000L) return null;
        CalculationParameters p = Method.UMM_AL_QURA.parameters();
        p.madhab = params.madhab;
        p.adjustments = params.adjustments;
        PrayerTimes m = new PrayerTimes(MAKKAH, date, p);
        Date[] mecca = {m.fajr, m.sunrise, m.dhuhr, m.asr, m.maghrib, m.isha};
        ZoneId here = ZoneId.systemDefault();
        Date[] out = new Date[6];
        for (int i = 0; i < 6; i++) {
            LocalDateTime clock = LocalDateTime.ofInstant(mecca[i].toInstant(), MAKKAH_CLOCK);
            out[i] = Date.from(clock.atZone(here).toInstant());
        }
        return out;
    }
}
