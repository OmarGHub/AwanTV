package app.awanpro.tv;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.PrayerAdjustments;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.data.CalendarUtil;
import com.batoulapps.adhan.data.DateComponents;
import com.batoulapps.adhan.data.TimeComponents;
import com.batoulapps.adhan.internal.SolarTime;

import java.util.Calendar;
import java.util.Date;

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
        if (Math.abs(where.latitude) <= FROM || !Prefs.HIGH_PROPORTION.equals(rule)) return own;
        // The UK Moonsighting Committee's method brings its own season rule (adhan-java's).
        if (params.method == CalculationMethod.MOON_SIGHTING_COMMITTEE) return own;
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
}
