package app.awanpro.tv;

import android.content.Context;

import java.util.Calendar;
import java.util.Date;

/**
 * Friday's khutbah. By default at the Dhuhr adhan, as Jumu'ah is prayed in Dhuhr's time, except where
 * a country sets one time for all its mosques, with the official source beside it (as {@link Iqama} does for its
 * presets); the viewer may also choose Dhuhr or a time of their own (Settings > Prayer times > Friday prayer).
 */
final class Jumuah {
    private Jumuah() {}

    /**
     * Countries with one official Friday time, in minutes after midnight. Add a country only with its source.
     * United Arab Emirates: 12:45, from 2 January 2026, by circular of the General Authority of Islamic Affairs,
     * Endowments and Zakat (reported by The National, 10 December 2025; Al Khaleej, 1 January 2026), replacing 13:15
     * (2022). Not Sharjah, which kept its own Friday times in 2022 (Khaleej Times); whether the 2026 circular covers it
     * was not found, so there the khutbah stays at Dhuhr until a source says otherwise.
     */
    static int presetMinutes(Context c) {
        Prefs.City city = Prefs.city(c);
        boolean uae = "UAE".equals(city.countryEn) || "United Arab Emirates".equalsIgnoreCase(city.countryEn);
        if (uae && !city.en.toLowerCase(java.util.Locale.ROOT).contains("sharjah") && !"sharjah".equals(city.id)) return 12 * 60 + 45;
        return -1;
    }

    /** The khutbah's own time on this Friday, or null when it is at the Dhuhr adhan (or it is not Friday). */
    static Date at(Context c, PrayerDay day) {
        if (!day.friday) return null;
        int mode = Prefs.fridayMode(c);
        int minutes = mode == Prefs.FRIDAY_FIXED ? Prefs.fridayFixed(c) : mode == Prefs.FRIDAY_COUNTRY ? presetMinutes(c) : -1;
        if (minutes < 0) return null;
        Calendar k = Calendar.getInstance();
        k.setTime(day.times[PrayerDay.DHUHR]);
        k.set(Calendar.HOUR_OF_DAY, minutes / 60);
        k.set(Calendar.MINUTE, minutes % 60);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        return k.getTime();
    }

    /** Ramadan's imsak (Settings > Prayer times): the coming Fajr less the viewer's minutes, or null when it is off. */
    static Date imsak(Context c, PrayerDay day) {
        int m = Prefs.imsak(c);
        return m <= 0 ? null : new Date(day.suhoorEnd.getTime() - m * 60_000L);
    }
}
