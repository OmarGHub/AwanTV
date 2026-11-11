package app.awanpro.tv;

import android.content.Context;

import java.util.Calendar;
import java.util.Date;

/**
 * The iqamah under each prayer. Four modes, chosen in Settings:
 * <ul>
 * <li>{@link #OFF}: no iqamah shown.</li>
 * <li>{@link #COUNTRY}: the official wait after the adhan in the city's country, where one is published.</li>
 * <li>{@link #MINUTES}: the viewer's own minutes after the adhan, per prayer.</li>
 * <li>{@link #FIXED}: a fixed clock time per prayer, as many mosques set it.</li>
 * </ul>
 * The default is {@link #COUNTRY} where the country has a preset, otherwise {@link #OFF}: no numbers are made up for a
 * country without an official source. Sunrise has no iqamah, and neither has Jumu'ah (Friday's Dhuhr): the khutbah.
 */
final class Iqama {
    private Iqama() {}

    static final int OFF = 0, COUNTRY = 1, MINUTES = 2, FIXED = 3;
    /** Prayers with an iqamah, as {@link PrayerDay} indices. */
    static final int[] PRAYERS = {PrayerDay.FAJR, PrayerDay.DHUHR, PrayerDay.ASR, PrayerDay.MAGHRIB, PrayerDay.ISHA};

    /**
     * Official waits between the adhan and the iqamah, in minutes per {@link PrayerDay} index (Sunrise unused).
     * Add a country only with its official source next to it.
     */
    private static final String[] PRESET_COUNTRIES = {"Saudi Arabia"};
    private static final int[][] PRESETS = {
        // Ministry of Islamic Affairs (moia.gov.sa, reaffirmed 2021): Fajr 25, Maghrib 10, every other prayer 20.
        {25, 0, 20, 20, 10, 20},
    };
    /** Where the viewer's own minutes start when there is no preset to start from. */
    private static final int[] START_MINUTES = {25, 0, 20, 20, 10, 20};

    /** The city's country preset, or null when its country has none. */
    static int[] preset(Context c) {
        return presetFor(Prefs.city(c).countryEn);
    }

    /** A country's preset by its English name (as in Prefs.CITIES, or Android's for a place from the world list). */
    static int[] presetFor(String country) {
        for (int i = 0; i < PRESET_COUNTRIES.length; i++) if (PRESET_COUNTRIES[i].equals(country)) return PRESETS[i];
        return null;
    }

    static int mode(Context c) {
        int m = Prefs.of(c).getInt("iqama_mode", -1);
        if (m == COUNTRY && preset(c) == null) return OFF; // Moved to a city without one.
        if (m >= OFF && m <= FIXED) return m;
        return preset(c) != null ? COUNTRY : OFF;
    }

    static void setMode(Context c, int mode) {
        Prefs.of(c).edit().putInt("iqama_mode", mode).apply();
    }

    /** The viewer's minutes after the adhan for one prayer (MINUTES mode). */
    static int minutes(Context c, int prayer) {
        int[] p = preset(c);
        return Prefs.of(c).getInt("iqama_min_" + prayer, (p != null ? p : START_MINUTES)[prayer]);
    }

    static void setMinutes(Context c, int prayer, int minutes) {
        Prefs.of(c).edit().putInt("iqama_min_" + prayer, Math.max(0, Math.min(90, minutes))).apply();
    }

    /** Minutes after the adhan for one prayer in Ramadan; -1 (the default) follows the other days. */
    static int ramadanMinutes(Context c, int prayer) {
        return Prefs.of(c).getInt("iqama_ramadan_" + prayer, -1);
    }

    static void setRamadanMinutes(Context c, int prayer, int minutes) {
        Prefs.of(c).edit().putInt("iqama_ramadan_" + prayer, Math.max(-1, Math.min(90, minutes))).apply();
    }

    /** The fixed time for one prayer (FIXED mode), in minutes after midnight; -1 until set. */
    static int fixed(Context c, int prayer) {
        return Prefs.of(c).getInt("iqama_at_" + prayer, -1);
    }

    static void setFixed(Context c, int prayer, int minuteOfDay) {
        Prefs.of(c).edit().putInt("iqama_at_" + prayer, Math.floorMod(minuteOfDay, 24 * 60)).apply();
    }

    /**
     * A starting point for FIXED: today's adhan plus the wait, rounded up to the next five minutes. Settings
     * seeds every prayer with it the first time FIXED is chosen.
     */
    static int suggestedFixed(Context c, PrayerDay day, int prayer) {
        Date at = afterAdhan(day.times[prayer], waitFor(c, prayer));
        Calendar cal = Calendar.getInstance();
        cal.setTime(at);
        int m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        return (m + 4) / 5 * 5 % (24 * 60);
    }

    /** The iqamah of one prayer on this day, or null when none is shown (off, Sunrise, Jumu'ah, a fixed time unset). */
    static Date at(Context c, PrayerDay day, int prayer) {
        if (prayer == PrayerDay.SUNRISE) return null;
        if (day.friday && prayer == PrayerDay.DHUHR) return null;
        boolean ramadan = prayer == PrayerDay.FAJR ? day.fajrRamadan : prayer == PrayerDay.ISHA ? day.ishaRamadan : day.ramadan;
        return at(c, prayer, day.times[prayer], ramadan);
    }

    /**
     * The iqamah of one prayer whose adhan is at {@code adhan}, or null when none is shown; ramadan: that prayer's own
     * Ramadan (for Isha, tonight's). Also asked by {@link PrayerDay} before it exists: does last night's Isha
     * iqamah still lie ahead after midnight.
     */
    static Date at(Context c, int prayer, Date adhan, boolean ramadan) {
        int mode = mode(c);
        // Ramadan's own wait, where one is set, in every mode but off.
        if (mode != OFF && ramadan && ramadanMinutes(c, prayer) >= 0) return afterAdhan(adhan, ramadanMinutes(c, prayer));
        switch (mode) {
            case COUNTRY: {
                int[] p = preset(c);
                return p == null ? null : afterAdhan(adhan, p[prayer]);
            }
            case MINUTES:
                return afterAdhan(adhan, minutes(c, prayer));
            case FIXED: {
                int m = fixed(c, prayer);
                if (m < 0) return null;
                // On the adhan's own day (after Isha the Fajr slot holds tomorrow's Fajr).
                Calendar cal = Calendar.getInstance();
                cal.setTime(adhan);
                cal.set(Calendar.HOUR_OF_DAY, m / 60);
                cal.set(Calendar.MINUTE, m % 60);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                Date at = cal.getTime();
                if (at.before(adhan)) {
                    // Far before the adhan is after midnight (a late Isha in summer at high latitudes): next day.
                    if (adhan.getTime() - at.getTime() > 12 * 3600_000L) at = new Date(at.getTime() + 24 * 3600_000L);
                    // Shortly before it, the season has moved past the set time: showing it would be wrong.
                    else return null;
                }
                return at;
            }
            default:
                return null;
        }
    }

    private static int waitFor(Context c, int prayer) {
        int[] p = preset(c);
        if (mode(c) == MINUTES || p == null) return minutes(c, prayer);
        return p[prayer];
    }

    private static Date afterAdhan(Date adhan, int minutes) {
        return new Date(adhan.getTime() + minutes * 60_000L);
    }
}
