package app.awanpro.tv;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.PrayerAdjustments;

import java.util.EnumMap;
import java.util.Map;

/**
 * A prayer-time calculation method: one of adhan-java's, or one Awan TV defines where adhan-java has none (Afnon
 * 0.8's Method, MIT). Its {@link #id} is what Settings saves: adhan-java's enum name for its own, so choices saved
 * before 1.8.0 keep working, and OMAN_AWQAF for Oman's, as since 1.3.0.
 */
final class Method {
    final String id;
    private final CalculationMethod base;

    private Method(String id, CalculationMethod base) {
        this.id = id;
        this.base = base;
    }

    private static final Map<CalculationMethod, Method> BY_ENUM = new EnumMap<>(CalculationMethod.class);

    /** The one Method for an adhan-java method, so == works. */
    static Method of(CalculationMethod m) {
        Method found = BY_ENUM.get(m);
        if (found == null) {
            found = new Method(m.name(), m);
            BY_ENUM.put(m, found);
        }
        return found;
    }

    static final Method EGYPTIAN = of(CalculationMethod.EGYPTIAN);
    static final Method UMM_AL_QURA = of(CalculationMethod.UMM_AL_QURA);
    static final Method MUSLIM_WORLD_LEAGUE = of(CalculationMethod.MUSLIM_WORLD_LEAGUE);
    static final Method KARACHI = of(CalculationMethod.KARACHI);
    static final Method DUBAI = of(CalculationMethod.DUBAI);
    static final Method KUWAIT = of(CalculationMethod.KUWAIT);
    static final Method QATAR = of(CalculationMethod.QATAR);
    static final Method NORTH_AMERICA = of(CalculationMethod.NORTH_AMERICA);
    static final Method MOON_SIGHTING_COMMITTEE = of(CalculationMethod.MOON_SIGHTING_COMMITTEE);
    static final Method SINGAPORE = of(CalculationMethod.SINGAPORE);

    /**
     * Oman's Ministry of Awqaf and Religious Affairs (Afnon 0.7): Fajr and Isha at 18°, Asr by the standard shadow, five
     * minutes on Dhuhr, Asr and Maghrib, fitted to the ministry's own timetable (mara.gov.om/arabic/calendar_page1.asp):
     * every prayer within about two minutes across Omani cities in 2026.
     */
    static final Method OMAN_AWQAF = new Method("OMAN_AWQAF", null);

    /**
     * Morocco's Ministry of Habous and Islamic Affairs (1.8.0): Fajr 19°, Isha 17°, Dhuhr five minutes after the transit,
     * sunrise and Maghrib at each locality's own horizon, its own rounding: see {@link Habous}, which computes it. These
     * parameters (19°/17°, Dhuhr +5) only describe it and stand in where the sun doesn't reach those angles.
     */
    static final Method MOROCCO_HABOUS = new Method("MOROCCO_HABOUS", null);

    /** Fresh parameters on every call: callers set the Asr school and adjustments on them. */
    CalculationParameters parameters() {
        if (base != null) return base.getParameters();
        if (this == MOROCCO_HABOUS) {
            return new CalculationParameters(19.0, 17.0, CalculationMethod.OTHER)
                .withMethodAdjustments(new PrayerAdjustments(0, 0, 5, 0, 0, 0));
        }
        return new CalculationParameters(18.0, 18.0, CalculationMethod.OTHER)
            .withMethodAdjustments(new PrayerAdjustments(0, 0, 5, 5, 5, 0));
    }

    @Override
    public String toString() {
        return id;
    }
}
