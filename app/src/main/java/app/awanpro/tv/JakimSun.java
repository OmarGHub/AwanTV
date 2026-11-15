package app.awanpro.tv;

/**
 * The sun for JAKIM's zone times (Malaysia, {@link Jakim}): plain Java, nothing from Android or adhan-java, so that
 * tools/AwanTV-build-jakim fits the zones with this very code and the app computes them with it again.
 *
 * Meeus, Astronomical Algorithms (2nd ed.): the sun's apparent position (ch. 25, low accuracy, as adhan-java's
 * SolarCoordinates), nutation (ch. 22, its short series) and sidereal time (ch. 12). Each event is the moment the sun
 * reaches its altitude, the sun taken at that very moment (four passes settle it to well under a second).
 */
final class JakimSun {
    private JakimSun() {}

    /** Raw times (minutes after the local midnight of the date, at utcOffset hours): see the indices. */
    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5,
        ASR_AT_0H = 6;

    /**
     * The day's raw times at a point: Fajr and Isha at 18° below the horizon, sunrise and Maghrib at −0°50′, Dhuhr at
     * the transit, Asr when the shadow is shadow lengths plus the noon shadow (ASR: the noon shadow from the sun at the
     * transit; ASR_AT_0H: from its declination at 00:00 UT of the date). NaN where the sun never reaches the altitude.
     */
    static double[] times(double lat, double lon, int year, int month, int day, double utcOffset, double shadow) {
        double[] out = new double[7];
        for (int i = 0; i < out.length; i++) out[i] = time(i, lat, lon, year, month, day, utcOffset, shadow);
        return out;
    }

    /** One of {@link #times}. */
    static double time(int which, double lat, double lon, int year, int month, int day, double utcOffset, double shadow) {
        double jd0 = julianDay(year, month, day);
        // The transit, as a fraction of the UT day.
        double t = 0.5 - lon / 360;
        for (int i = 0; i < 4; i++) t -= hourAngle(jd0 + t, lon) / SIDEREAL;
        double transit = t, at;
        switch (which) {
            case FAJR: at = event(jd0, transit, lat, lon, -18, -1); break;
            case SUNRISE: at = event(jd0, transit, lat, lon, -0.8333, -1); break;
            case DHUHR: at = transit; break;
            case ASR: at = event(jd0, transit, lat, lon, asrAltitude(Math.abs(lat - sun(jd0 + transit)[1]), shadow), 1); break;
            case MAGHRIB: at = event(jd0, transit, lat, lon, -0.8333, 1); break;
            case ISHA: at = event(jd0, transit, lat, lon, -18, 1); break;
            default: at = event(jd0, transit, lat, lon, asrAltitude(Math.abs(lat - sun(jd0)[1]), shadow), 1); break;
        }
        return at * 1440 + utcOffset * 60;
    }

    /** The sun's altitude when the shadow is shadow lengths longer than at noon (noonShadow: |latitude - declination|). */
    private static double asrAltitude(double noonShadow, double shadow) {
        return Math.toDegrees(Math.atan(1 / (shadow + Math.tan(Math.toRadians(noonShadow)))));
    }

    /** Degrees of hour angle per day. */
    private static final double SIDEREAL = 360.98564736629;

    /** The moment (fraction of the UT day) the sun is at altitude: before the transit (side -1) or after (+1). */
    private static double event(double jd0, double transit, double lat, double lon, double altitude, int side) {
        double t = transit;
        double f = Math.toRadians(lat);
        for (int i = 0; i < 4; i++) {
            double d = Math.toRadians(sun(jd0 + t)[1]);
            double cos = (Math.sin(Math.toRadians(altitude)) - Math.sin(f) * Math.sin(d)) / (Math.cos(f) * Math.cos(d));
            if (cos < -1 || cos > 1) return Double.NaN;
            double want = side * Math.toDegrees(Math.acos(cos));
            t += (want - hourAngle(jd0 + t, lon)) / SIDEREAL;
        }
        return t;
    }

    /** The sun's local hour angle in degrees, -180..180, at a Julian day (UT). */
    private static double hourAngle(double jd, double lon) {
        double[] s = sun(jd);
        double h = s[2] + lon - s[0];
        h = (h + 180) % 360;
        if (h < 0) h += 360;
        return h - 180;
    }

    /** {apparent right ascension, apparent declination, Greenwich apparent sidereal time}, degrees, at a Julian day. */
    static double[] sun(double jd) {
        double t = (jd - 2451545.0) / 36525;
        double l0 = 280.46646 + t * (36000.76983 + 0.0003032 * t);
        double m = Math.toRadians(357.52911 + t * (35999.05029 - 0.0001537 * t));
        double c = (1.914602 - t * (0.004817 + 0.000014 * t)) * Math.sin(m)
            + (0.019993 - 0.000101 * t) * Math.sin(2 * m) + 0.000289 * Math.sin(3 * m);
        double omega = Math.toRadians(125.04 - 1934.136 * t);
        double lambda = Math.toRadians(l0 + c - 0.00569 - 0.00478 * Math.sin(omega));
        double eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - 0.001813 * t))) / 60) / 60;
        double eps = Math.toRadians(eps0 + 0.00256 * Math.cos(omega));
        double ra = Math.toDegrees(Math.atan2(Math.cos(eps) * Math.sin(lambda), Math.cos(lambda)));
        double dec = Math.toDegrees(Math.asin(Math.sin(eps) * Math.sin(lambda)));
        // Nutation in longitude (arc seconds), for the apparent sidereal time.
        double l = Math.toRadians(280.4665 + 36000.7698 * t), lm = Math.toRadians(218.3165 + 481267.8813 * t);
        double psi = -17.20 * Math.sin(omega) - 1.32 * Math.sin(2 * l) - 0.23 * Math.sin(2 * lm) + 0.21 * Math.sin(2 * omega);
        double d = jd - 2451545.0;
        double gmst = 280.46061837 + 360.98564736629 * d + t * t * (0.000387933 - t / 38710000);
        double gast = gmst + psi / 3600 * Math.cos(eps);
        return new double[] {ra, dec, gast};
    }

    /** The Julian day at 00:00 UT of a Gregorian date. */
    static double julianDay(int year, int month, int day) {
        int y = month > 2 ? year : year - 1;
        int mo = month > 2 ? month : month + 12;
        int a = y / 100;
        int b = 2 - a + a / 4;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (mo + 1)) + day + b - 1524.5;
    }
}
