package app.awanpro.tv;

import android.content.Context;
import android.icu.util.IslamicCalendar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * The Umm al-Qura calendar with the viewer's Hijri adjustment (Settings), as {@link PrayerDay} reads it:
 * the day for any date, the days of this Hijri month (Home's calendar), the next of the year's occasions
 * (Ramadan, Eid al-Fitr, Arafah, Eid al-Adha) and the moon's age for the weather icon.
 */
final class Hijri {
    private Hijri() {}

    static final int RAMADAN = 0, FITR = 1, ARAFAH = 2, ADHA = 3;
    /** Occasions show from this many days before. */
    static final int AHEAD_DAYS = 30;
    /** Each occasion's month (IslamicCalendar, 0 = Muharram) and day, in the order of the constants above. */
    private static final int[][] OCCASIONS = {
        {IslamicCalendar.RAMADAN, 1}, {IslamicCalendar.SHAWWAL, 1}, {IslamicCalendar.DHU_AL_HIJJAH, 9}, {IslamicCalendar.DHU_AL_HIJJAH, 10}};

    /** {year, month (0 = Muharram), day} for the civil date of d. */
    static int[] of(Date d, int offset) {
        IslamicCalendar h = new IslamicCalendar();
        h.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        h.setTime(d);
        if (offset != 0) h.add(IslamicCalendar.DAY_OF_MONTH, offset);
        return new int[] {h.get(IslamicCalendar.YEAR), h.get(IslamicCalendar.MONTH), h.get(IslamicCalendar.DAY_OF_MONTH)};
    }

    static int[] of(Context c, Date d) {
        return of(d, Prefs.hijriOffset(c));
    }

    /** The coming occasion: which one and in how many days (0 = today). */
    static final class Occasion {
        final int kind, days;

        Occasion(int kind, int days) {
            this.kind = kind;
            this.days = days;
        }
    }

    private static String nextFor;
    private static Occasion nextCached;

    /** The first occasion within {@link #AHEAD_DAYS} days from today, or null. Worked out once a day. */
    static synchronized Occasion next(Context c, Date now) {
        int offset = Prefs.hijriOffset(c);
        String key = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(now) + "/" + offset;
        if (key.equals(nextFor)) return nextCached;
        nextFor = key;
        nextCached = scan(now, offset);
        return nextCached;
    }

    private static Occasion scan(Date now, int offset) {
        Calendar g = Calendar.getInstance();
        g.setTime(now);
        for (int days = 0; days <= AHEAD_DAYS; days++) {
            int[] h = of(g.getTime(), offset);
            for (int k = 0; k < OCCASIONS.length; k++) {
                if (h[1] == OCCASIONS[k][0] && h[2] == OCCASIONS[k][1]) return new Occasion(k, days);
            }
            g.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    // ----------------------------------------------------------------

    static final int MONDAY = 0, THURSDAY = 1, WHITE = 2, TASUA = 3, ASHURA = 4;

    /**
     * Tomorrow's sunnah fast, or -1: Tasu'a and Ashura (9, 10 Muharram), the White Days (13 to 15), Monday and
     * Thursday. None in Ramadan, on the two Eids or the days of Tashriq, and none on Arafah, which has its own line.
     */
    static int fastTomorrow(Context c, Date now) {
        Calendar g = Calendar.getInstance();
        g.setTime(now);
        g.add(Calendar.DAY_OF_MONTH, 1);
        int[] h = of(g.getTime(), Prefs.hijriOffset(c));
        int m = h[1], d = h[2];
        if (m == IslamicCalendar.RAMADAN || (m == IslamicCalendar.SHAWWAL && d == 1)) return -1;
        if (m == IslamicCalendar.DHU_AL_HIJJAH && d >= 9 && d <= 13) return -1;
        if (m == IslamicCalendar.MUHARRAM && d == 9) return TASUA;
        if (m == IslamicCalendar.MUHARRAM && d == 10) return ASHURA;
        if (d >= 13 && d <= 15) return WHITE;
        int w = g.get(Calendar.DAY_OF_WEEK);
        return w == Calendar.MONDAY ? MONDAY : w == Calendar.THURSDAY ? THURSDAY : -1;
    }

    /**
     * The one gold line of Home's arch and the screensaver's Hijri date, or null: the coming occasion; in the
     * evening (from Asr until midnight) tomorrow's sunnah fast instead, unless the occasion is today or tomorrow.
     */
    static String notice(Context c, Text t, PrayerDay day, Date now) {
        Occasion o = next(c, now);
        if (o != null && o.days <= 1) return t.occasion(o.kind, o.days);
        // The Asr slot is always today's: from it until midnight, when a new day begins.
        if (Prefs.fastReminder(c) && !now.before(day.times[PrayerDay.ASR])) {
            int f = fastTomorrow(c, now);
            if (f >= 0) return t.fastTomorrow(f);
        }
        return o == null ? null : t.occasion(o.kind, o.days);
    }

    /** Days marked in Home's calendar: the White Days, Tasu'a and Ashura, the first of Ramadan, Arafah, the Eids. */
    static boolean marked(int month, int day) {
        if (month == IslamicCalendar.MUHARRAM && (day == 9 || day == 10)) return true;
        if (month == IslamicCalendar.RAMADAN && day == 1) return true;
        if (month == IslamicCalendar.SHAWWAL && day == 1) return true;
        if (month == IslamicCalendar.DHU_AL_HIJJAH && (day == 9 || day == 10)) return true;
        // The White Days, but not in Ramadan (fasted anyway) nor on the 13th of Dhu al-Hijjah (a day of Tashriq).
        if (month == IslamicCalendar.RAMADAN) return false;
        return day >= 13 && day <= 15 && !(month == IslamicCalendar.DHU_AL_HIJJAH && day == 13);
    }

    /** This Hijri month: its year and month, and the civil date of each of its days in order (29 or 30). */
    static final class Month {
        int year, month;
        final List<Date> days = new ArrayList<>();
        /** Index of today in {@link #days}. */
        int today;
    }

    static Month month(Context c, Date now) {
        int offset = Prefs.hijriOffset(c);
        int[] h = of(now, offset);
        Month m = new Month();
        m.year = h[0];
        m.month = h[1];
        m.today = h[2] - 1;
        Calendar g = Calendar.getInstance();
        g.setTime(now);
        g.set(Calendar.HOUR_OF_DAY, 12);
        g.set(Calendar.MINUTE, 0);
        g.set(Calendar.SECOND, 0);
        g.set(Calendar.MILLISECOND, 0);
        g.add(Calendar.DAY_OF_MONTH, -(h[2] - 1));
        for (int i = 0; i < 31; i++) {
            Date d = g.getTime();
            int[] x = of(d, offset);
            if (x[1] != m.month) break;
            m.days.add(d);
            g.add(Calendar.DAY_OF_MONTH, 1);
        }
        return m;
    }
}
