package app.awanpro.tv;

import android.content.Context;
import android.icu.util.IslamicCalendar;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.HighLatitudeRule;
import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerAdjustments;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.Qibla;
import com.batoulapps.adhan.data.DateComponents;

import java.util.Calendar;
import java.util.Date;

/** One moment's view of the prayer day: the six times, which prayer is current and which is next. */
final class PrayerDay {
    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;

    /** Today's times, except that after Isha the Fajr slot holds tomorrow's Fajr (it is the next prayer). */
    final Date[] times = new Date[6];
    /** Index of the current prayer, or -1 before Fajr and between Sunrise and Dhuhr. */
    final int nowIndex;
    /** Index of the next prayer (never Sunrise, which is a marker, not a prayer). */
    final int nextIndex;
    final Date nextTime;
    /** The Fajr that ends tonight's Suhoor: today's before dawn, otherwise tomorrow's. */
    final Date suhoorEnd;
    final boolean friday;
    final boolean ramadan;
    /** Ramadan for the Fajr in the Fajr slot, which after Isha is tomorrow's (the first suhoor, Eid's dawn). */
    final boolean fajrRamadan;
    /** Tonight is a night of Ramadan (tomorrow's Hijri day is): Isha's Umm al-Qura half hour and its iqamah. */
    final boolean ishaRamadan;
    final int hijriDay, hijriMonth, hijriYear;
    final double qibla;

    /** With the saved location, method, Asr school, the times' adjustments and the Hijri adjustment (Settings). */
    PrayerDay(Context c, Date now) {
        this(now, Prefs.where(c), Prefs.method(c), Prefs.madhab(c), Prefs.adjustments(c), Prefs.hijriOffset(c));
    }

    PrayerDay(Date now, Coordinates where, CalculationMethod method, Madhab madhab, int[] adjust, int hijriOffset) {
        CalculationParameters params = method.getParameters();
        params.madhab = madhab;
        // The viewer's minutes on each time (Prefs.adjustments): the adhan, iqamah, reminders and screens all follow.
        params.adjustments = new PrayerAdjustments(adjust[FAJR], adjust[SUNRISE], adjust[DHUHR], adjust[ASR],
            adjust[MAGHRIB], adjust[ISHA]);
        // Above 48°, where summer nights never reach the method's angles: Fajr no earlier than a seventh of the night
        // before sunrise and Isha no later than a seventh after Maghrib (as meypod/al-azan and Afnon 0.8), not
        // adhan-java's default middle of the night, which put both near 1 a.m.
        if (Math.abs(where.latitude) > 48) params.highLatitudeRule = HighLatitudeRule.SEVENTH_OF_THE_NIGHT;
        PrayerTimes today = times(where, DateComponents.from(now), params);
        times[FAJR] = minute(today.fajr);
        times[SUNRISE] = minute(today.sunrise);
        times[DHUHR] = minute(today.dhuhr);
        times[ASR] = minute(today.asr);
        times[MAGHRIB] = minute(today.maghrib);
        times[ISHA] = minute(today.isha);

        IslamicCalendar h = new IslamicCalendar();
        h.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        h.setTime(now);
        if (hijriOffset != 0) h.add(IslamicCalendar.DAY_OF_MONTH, hijriOffset);
        hijriDay = h.get(IslamicCalendar.DAY_OF_MONTH);
        hijriMonth = h.get(IslamicCalendar.MONTH);
        hijriYear = h.get(IslamicCalendar.YEAR);
        ramadan = hijriMonth == IslamicCalendar.RAMADAN;
        // Tonight belongs to tomorrow's Hijri day (the date shown still turns at midnight): the first night of
        // Ramadan is the evening of Sha'ban's last day, and the night of Eid is not Ramadan.
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.DAY_OF_MONTH, 1);
        IslamicCalendar tomorrowHijri = new IslamicCalendar();
        tomorrowHijri.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        tomorrowHijri.setTime(c.getTime());
        if (hijriOffset != 0) tomorrowHijri.add(IslamicCalendar.DAY_OF_MONTH, hijriOffset);
        ishaRamadan = tomorrowHijri.get(IslamicCalendar.MONTH) == IslamicCalendar.RAMADAN;
        // Umm al-Qura's Isha is 90 minutes after Maghrib, and 120 on Ramadan's nights (the Ministry of Islamic
        // Affairs, after Umm al-Qura). adhan-java leaves the Ramadan half hour to the caller
        // (CalculationMethod.UMM_AL_QURA: "add a +30 minute custom adjustment").
        if (ishaRamadan && method == CalculationMethod.UMM_AL_QURA) times[ISHA] = new Date(times[ISHA].getTime() + 30 * 60_000L);

        int latest = -1;
        for (int i = 0; i < 6; i++) if (!times[i].after(now)) latest = i;
        nowIndex = latest == SUNRISE ? -1 : latest;

        int next = -1;
        for (int i = 0; i < 6; i++) {
            if (i != SUNRISE && times[i].after(now)) { next = i; break; }
        }
        Date tomorrowFajr = minute(times(where, DateComponents.from(c.getTime()), params).fajr);
        boolean tomorrowsFajr = next == -1;
        if (next == -1) {
            next = FAJR;
            times[FAJR] = tomorrowFajr;
        }
        fajrRamadan = tomorrowsFajr ? ishaRamadan : ramadan;
        nextIndex = next;
        nextTime = times[next];
        suhoorEnd = times[FAJR].after(now) ? times[FAJR] : tomorrowFajr;

        c.setTime(now);
        friday = c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;

        qibla = new Qibla(where).direction;
    }

    /**
     * {the middle of the night, the start of its last third}, the night being Maghrib to Fajr: tonight's,
     * or before dawn the one still going (yesterday's Maghrib to today's Fajr), as adhan-java's SunnahTimes counts it.
     */
    static Date[] night(Context c, PrayerDay day, Date now) {
        Date fajr = day.suhoorEnd;   // the coming Fajr: today's before dawn, otherwise tomorrow's
        boolean beforeDawn = now.before(day.times[FAJR]) && !now.after(fajr) && day.times[ISHA].after(now);
        Date maghrib = beforeDawn ? new PrayerDay(c, new Date(now.getTime() - 24 * 3600_000L)).times[MAGHRIB] : day.times[MAGHRIB];
        long span = fajr.getTime() - maghrib.getTime();
        return new Date[] {minute(new Date(maghrib.getTime() + span / 2 + 30_000)),
                           minute(new Date(maghrib.getTime() + span * 2 / 3 + 30_000))};
    }

    /**
     * The last Isha before now: today's once it has come, otherwise yesterday's (after midnight the
     * night still belongs to it: Home's Isha bar, the screensaver's dimming).
     */
    static Date lastIsha(Context c, PrayerDay day, Date now) {
        Date isha = day.times[ISHA];
        if (!isha.after(now)) return isha;
        return new PrayerDay(c, new Date(now.getTime() - 24 * 3600_000L)).times[ISHA];
    }

    /**
     * The time on its whole minute. adhan-java keeps the milliseconds of the moment it was asked, so the same
     * prayer came out a little different on every check, and "already shown" never matched: the reminder and the
     * call kept coming back after being closed.
     */
    /**
     * The day's times for the place; on a polar day or night (no sunrise or no sunset that day, when adhan-java gives
     * no times at all) those of latitude 45° on the same longitude, so Dhuhr stays the place's own. Saudi Arabia's
     * Council of Senior Scholars (decision 61) has such places "estimate their times relying on the nearest lands",
     * and the Islamic Fiqh Council in Makkah takes 45° as the nearest where worship is feasible (as collected by
     * erej.org); Murmansk's Muslims follow Crimea's times on those days. The nearest latitude that still had a sunset
     * gave three prayers in 34 minutes near midsummer. Days that have sunrise and sunset keep the place's own times.
     */
    static final double POLAR_FALLBACK_LATITUDE = 45;

    private static PrayerTimes times(Coordinates where, DateComponents date, CalculationParameters params) {
        PrayerTimes p = new PrayerTimes(where, date, params);
        if (p.fajr != null) return p;
        // 45° is below the seventh-of-the-night band: its own times, by the method's angles as anywhere below 48°.
        HighLatitudeRule rule = params.highLatitudeRule;
        params.highLatitudeRule = HighLatitudeRule.MIDDLE_OF_THE_NIGHT;
        try {
            return new PrayerTimes(new Coordinates(Math.copySign(POLAR_FALLBACK_LATITUDE, where.latitude), where.longitude), date, params);
        } finally {
            params.highLatitudeRule = rule;
        }
    }

    private static Date minute(Date d) {
        return new Date(d.getTime() / 60_000 * 60_000);
    }
}
