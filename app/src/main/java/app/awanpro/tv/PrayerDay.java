package app.awanpro.tv;

import android.content.Context;
import android.icu.util.IslamicCalendar;

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
        this(c, now, true);
    }

    /**
     * The calendar day's own times, never the night still going on from the day before ({@link #PrayerDay(Date,
     * Coordinates, Method, Madhab, int[], int, Habous.Locality, boolean)}): for "yesterday's" Maghrib or Isha.
     */
    static PrayerDay ofDate(Context c, Date at) {
        return new PrayerDay(c, at, false);
    }

    private PrayerDay(Context c, Date now, boolean night) {
        this(c, now, night, Prefs.calc(c));
    }

    /** calc: the custom calculation (its base method in place of the chosen one), or null. */
    private PrayerDay(Context c, Date now, boolean night, Prefs.Calc calc) {
        this(now, Prefs.where(c), calc != null ? calc.base : Prefs.method(c), Prefs.madhab(c), Prefs.adjustments(c),
            Prefs.hijriOffset(c), calc == null && Prefs.method(c) == Method.MOROCCO_HABOUS ? Habous.table(c) : null, night, calc,
            (adhan, ramadan) -> Iqama.at(c, ISHA, adhan, ramadan));
    }

    /** Tonight's Isha iqamah for an Isha adhan (Iqama.at), or null when none is shown. */
    interface IshaIqamah {
        Date at(Date adhan, boolean ramadan);
    }

    /**
     * {@code table}: with Morocco's method, the ministry's locality whose timetable the place takes (its point and
     * horizons), or null outside Morocco (the place's own point at the ministry's coastal horizon).
     */
    PrayerDay(Date now, Coordinates where, Method method, Madhab madhab, int[] adjust, int hijriOffset, Habous.Locality table) {
        this(now, where, method, madhab, adjust, hijriOffset, table, true, null, null);
    }

    /** With an Isha iqamah (the checks' own, in place of Settings > Iqamah). */
    PrayerDay(Date now, Coordinates where, Method method, Madhab madhab, int[] adjust, int hijriOffset, Habous.Locality table,
              IshaIqamah iqamah) {
        this(now, where, method, madhab, adjust, hijriOffset, table, true, null, iqamah);
    }

    /** With a custom calculation (Settings > Prayer times > Calculation method > Custom calculation). */
    PrayerDay(Date now, Coordinates where, Prefs.Calc calc, Madhab madhab, int[] adjust, int hijriOffset) {
        this(now, where, calc.base, madhab, adjust, hijriOffset, null, true, calc, null);
    }

    /**
     * night: the prayer day is the one whose night is still going on. Far north in summer (Reykjavik, Nuuk, Oulu,
     * Fairbanks, Anchorage, Trondheim...) a day's Maghrib or Isha comes after midnight, and at 00:00 the new date's
     * times dropped it: no adhan, a wrong next prayer (1.23.0). Before today's Fajr, when yesterday's Isha came after
     * midnight, the times are yesterday's (its Isha "now" and called, then today's Fajr next); from Fajr on, today's. The prayers are moments in one line through
     * yesterday, today and tomorrow, as Mawaqit's and adhan's SunnahTimes take the night; adhan-java gives each
     * day's late times on the right instant already. The dates shown, the Hijri day and Friday still follow the clock.
     * The same holds when only that Isha's iqamah comes after midnight (1.24.1a: its countdown went at 00:00).
     */
    private PrayerDay(Date now, Coordinates where, Method method, Madhab madhab, int[] adjust, int hijriOffset,
                      Habous.Locality table, boolean night, Prefs.Calc calc, IshaIqamah iqamah) {
        CalculationParameters params = calc != null ? calc.parameters() : Prefs.parameters(method);
        // Above 48°: the seventh of the night below, or the custom calculation's rule (HighLatitude).
        String high = calc != null ? calc.high : Prefs.HIGH_SEVENTH;
        params.madhab = madhab;
        // The viewer's minutes on each time (Prefs.adjustments): the adhan, iqamah, reminders and screens all follow.
        params.adjustments = new PrayerAdjustments(adjust[FAJR], adjust[SUNRISE], adjust[DHUHR], adjust[ASR],
            adjust[MAGHRIB], adjust[ISHA]);
        // Above 48°, where summer nights never reach the method's angles: Fajr no earlier than a seventh of the night
        // before sunrise and Isha no later than a seventh after Maghrib (as meypod/al-azan and Afnon 0.8), not
        // adhan-java's default middle of the night, which put both near 1 a.m.
        // The custom calculation may choose adhan-java's middle of the night or twilight angle instead (Prefs.adhanRule).
        if (Math.abs(where.latitude) > 48) params.highLatitudeRule = Prefs.adhanRule(high);
        // The prayer day: the clock's date, or yesterday while its night is still going on (night).
        Calendar base = Calendar.getInstance();
        base.setTime(now);
        Date[] today = times(method, table, where, DateComponents.from(now), params, high);
        if (night && now.before(minute(today[FAJR]))) {
            Calendar y = (Calendar) base.clone();
            y.add(Calendar.DAY_OF_MONTH, -1);
            Date[] yesterday = times(method, table, where, DateComponents.from(y.getTime()), params, high);
            Date yIsha = minute(yesterday[ISHA]);
            boolean yRamadan = ramadanNight(y, hijriOffset);
            if (ummAlQuraRamadan(method, params) && yRamadan) yIsha = new Date(yIsha.getTime() + 30 * 60_000L);
            // Yesterday's Isha came after midnight: its night runs to today's Fajr. Up to Isha its Maghrib and Isha
            // are still to come; from Isha on, that Isha is the one whose adhan, iqamah and "now" are due (1.23.0b:
            // switching to today's times at that Isha left it out of the day, and its adhan never came).
            Calendar midnight = (Calendar) base.clone();
            midnight.set(Calendar.HOUR_OF_DAY, 0);
            midnight.set(Calendar.MINUTE, 0);
            midnight.set(Calendar.SECOND, 0);
            midnight.set(Calendar.MILLISECOND, 0);
            // An Isha a minute or two before midnight too: its call is due until CALL_WINDOW_MS after it. And an Isha
            // before midnight whose iqamah comes after it: its countdown runs until the iqamah (1.24.1a).
            long until = yIsha.getTime();
            Date yIqamah = iqamah != null ? iqamah.at(yIsha, yRamadan) : null;
            if (yIqamah != null && yIqamah.getTime() > until) until = yIqamah.getTime();
            if (until > midnight.getTimeInMillis() - CALL_WINDOW_MS) {
                today = yesterday;
                base = y;
            }
        }
        for (int i = 0; i < 6; i++) times[i] = minute(today[i]);

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
        // The day after the prayer day (tomorrow, or today while yesterday's night goes on).
        Calendar c = (Calendar) base.clone();
        c.add(Calendar.DAY_OF_MONTH, 1);
        ishaRamadan = ramadanNight(base, hijriOffset);
        // Umm al-Qura's Isha is 90 minutes after Maghrib, and 120 on Ramadan's nights (the Ministry of Islamic
        // Affairs, after Umm al-Qura). adhan-java leaves the Ramadan half hour to the caller
        // (CalculationMethod.UMM_AL_QURA: "add a +30 minute custom adjustment").
        if (ishaRamadan && ummAlQuraRamadan(method, params)) times[ISHA] = new Date(times[ISHA].getTime() + 30 * 60_000L);

        int latest = -1;
        for (int i = 0; i < 6; i++) if (!times[i].after(now)) latest = i;
        nowIndex = latest == SUNRISE ? -1 : latest;

        int next = -1;
        for (int i = 0; i < 6; i++) {
            if (i != SUNRISE && times[i].after(now)) { next = i; break; }
        }
        Date tomorrowFajr = minute(times(method, table, where, DateComponents.from(c.getTime()), params, high)[FAJR]);
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
    /**
     * Umm al-Qura's Ramadan half hour: its Isha by minutes after Maghrib, as the method's own or a custom calculation
     * from it that keeps minutes (not one that moved Isha to an angle).
     */
    private static boolean ummAlQuraRamadan(Method method, CalculationParameters params) {
        return method == Method.UMM_AL_QURA && params.ishaInterval > 0;
    }

    /** The night after day is one of Ramadan (the next day's Hijri date is in Ramadan). */
    private static boolean ramadanNight(Calendar day, int hijriOffset) {
        Calendar next = (Calendar) day.clone();
        next.add(Calendar.DAY_OF_MONTH, 1);
        IslamicCalendar h = new IslamicCalendar();
        h.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        h.setTime(next.getTime());
        if (hijriOffset != 0) h.add(IslamicCalendar.DAY_OF_MONTH, hijriOffset);
        return h.get(IslamicCalendar.MONTH) == IslamicCalendar.RAMADAN;
    }

    static Date[] night(Context c, PrayerDay day, Date now) {
        Date fajr = day.suhoorEnd;   // the coming Fajr: today's before dawn, otherwise tomorrow's
        boolean beforeDawn = now.before(day.times[FAJR]) && !now.after(fajr) && day.times[ISHA].after(now);
        Date maghrib = beforeDawn ? ofDate(c, new Date(now.getTime() - 24 * 3600_000L)).times[MAGHRIB] : day.times[MAGHRIB];
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
        // Far north in summer yesterday's own Isha may still be ahead (after midnight): then the one before it.
        Date yesterday = ofDate(c, new Date(now.getTime() - 24 * 3600_000L)).times[ISHA];
        if (!yesterday.after(now)) return yesterday;
        return ofDate(c, new Date(now.getTime() - 48 * 3600_000L)).times[ISHA];
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

    /** How long after a prayer time its call is still due (Reminders): a day keeps its prayers for that long. */
    static final long CALL_WINDOW_MS = 120_000;

    /**
     * The six times (FAJR..ISHA). Morocco's method by {@link Habous} at its locality's point and horizons (or the
     * place's own at the coast's) with the viewer's minutes added after the ministry's rounding, as adhan adds them;
     * where the sun doesn't reach 19°/17° (far north, chosen by hand), adhan-java's way below.
     */
    private static Date[] times(Method method, Habous.Locality table, Coordinates where, DateComponents date,
                                CalculationParameters params, String high) {
        if (method == Method.MOROCCO_HABOUS) {
            Coordinates at = table != null ? new Coordinates(table.lat, table.lng) : where;
            Date[] t = Habous.times(at, date, params.madhab, table != null ? table.horizon : Habous.COAST);
            if (t != null) {
                PrayerAdjustments a = params.adjustments;
                int[] minutes = {a.fajr, a.sunrise, a.dhuhr, a.asr, a.maghrib, a.isha};
                for (int i = 0; i < 6; i++) t[i] = new Date(t[i].getTime() + minutes[i] * 60_000L);
                return t;
            }
        }
        PrayerTimes p = new PrayerTimes(where, date, params);
        Date[] local = null;
        if (p.fajr != null) {
            // The custom calculation's rule above 48° (HighLatitude).
            Date[] fi = HighLatitude.fajrIsha(high, where, date, params, p);
            local = new Date[] {fi[0], p.sunrise, p.dhuhr, p.asr, p.maghrib, fi[1]};
        }
        // Makkah's clock times where the fast would pass twenty hours, or on a day adhan-java can't work out there (no
        // sunrise, sunset or Asr: HighLatitude.makkah).
        Date[] makkah = HighLatitude.makkah(high, where, date, params, local);
        if (makkah != null) return makkah;
        if (local != null) return local;
        p = at45(where, date, params);
        return new Date[] {p.fajr, p.sunrise, p.dhuhr, p.asr, p.maghrib, p.isha};
    }

    /** A day with no sunrise, sunset or Asr at the place (adhan-java gives none of its times): 45°'s, whatever the rule. */
    private static PrayerTimes at45(Coordinates where, DateComponents date, CalculationParameters params) {
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
