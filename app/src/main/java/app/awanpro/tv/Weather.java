package app.awanpro.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * The weather by the clock, on Home and the screensaver: the temperature now and an icon for the sky.
 *
 * <p>From Open-Meteo (open-meteo.com: free, no key, data CC BY 4.0, credited in Settings > About) for the chosen
 * place's coordinates, so it needs no location permission and works for any place. One small request at most every
 * 30 minutes, and only while Home or the screensaver is on screen: the screens ask for it on their minute tick
 * ({@link #refresh}), there is no timer or service of its own. The last reading is kept; with no internet it
 * stays up to 3 hours, then the weather simply disappears rather than show an old temperature as today's.
 * Settings > Display > Weather turns it off (no request at all then) or picks °C / °F.
 */
final class Weather {
    private Weather() {}

    /** The sky, from the WMO weather code: what {@link WeatherIcon} draws. */
    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, DRIZZLE = 4, RAIN = 5, SNOW = 6, STORM = 7;

    private static final long EVERY_MS = 30 * 60_000L, RETRY_MS = 5 * 60_000L, KEEP_MS = 3 * 3600_000L;
    private static final int TIMEOUT_MS = 10_000;

    /** A reading: the temperature in °C, the sky ({@link #CLEAR}...) and whether the sun is up. */
    static final class Now {
        final double celsius;
        final int sky;
        final boolean day;

        Now(double celsius, int sky, boolean day) {
            this.celsius = celsius;
            this.sky = sky;
            this.day = day;
        }
    }

    private static volatile boolean fetching;
    private static long lastTry;

    /** The weather to show now, or null: turned off, nothing read yet for this place, or too old. */
    static Now now(Context c) {
        if ("off".equals(Prefs.weather(c))) return null;
        SharedPreferences p = Prefs.of(c);
        long at = p.getLong("wx_at", 0);
        if (at == 0 || System.currentTimeMillis() - at > KEEP_MS || !samePlace(c)) return null;
        return new Now(Double.longBitsToDouble(p.getLong("wx_c", 0)), p.getInt("wx_sky", CLEAR), p.getBoolean("wx_day", true));
    }

    /** The temperature in the chosen unit, rounded, with its degree sign, in the screen's digits. */
    static String temperature(Context c, Text t, Now n) {
        double v = "f".equals(Prefs.weather(c)) ? n.celsius * 9 / 5 + 32 : n.celsius;
        long r = Math.round(v);
        return t.d(String.valueOf(r == 0 ? 0 : r)) + "°";
    }

    /**
     * Fetches a new reading when the last one is 30 minutes old (or for another place), in the background; the
     * screens show it on their next minute. Called from the screens' minute tick.
     */
    static void refresh(Context c) {
        if ("off".equals(Prefs.weather(c)) || fetching) return;
        SharedPreferences p = Prefs.of(c);
        long now = System.currentTimeMillis();
        boolean fresh = samePlace(c) && now - p.getLong("wx_at", 0) < EVERY_MS;
        if (fresh || now - lastTry < RETRY_MS) return;
        lastTry = now;
        fetching = true;
        Context app = c.getApplicationContext();
        Prefs.City city = Prefs.city(app);
        double lat = city.lat, lng = city.lng;
        new Thread(() -> {
            try {
                read(app, lat, lng);
            } catch (Exception e) {
                // No internet, or the service did not answer: keep the last reading; try again in 5 minutes.
                Log.i(HomeGuard.TAG, "weather: " + e);
            } finally {
                fetching = false;
            }
        }, "awan-weather").start();
    }

    private static void read(Context c, double lat, double lng) throws Exception {
        URL url = new URL(String.format(Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day",
            lat, lng));
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setConnectTimeout(TIMEOUT_MS);
        http.setReadTimeout(TIMEOUT_MS);
        http.setRequestProperty("User-Agent", "AwanTV (https://awanpro.app)");
        try {
            if (http.getResponseCode() != 200) throw new java.io.IOException("HTTP " + http.getResponseCode());
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = http.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && body.size() < 64 * 1024) body.write(buf, 0, n);
            }
            JSONObject cur = new JSONObject(body.toString("UTF-8")).getJSONObject("current");
            double celsius = cur.getDouble("temperature_2m");
            int sky = sky(cur.getInt("weather_code"));
            boolean day = cur.optInt("is_day", 1) == 1;
            Prefs.of(c).edit()
                .putLong("wx_c", Double.doubleToLongBits(celsius)).putInt("wx_sky", sky).putBoolean("wx_day", day)
                .putLong("wx_at", System.currentTimeMillis())
                .putLong("wx_lat", Double.doubleToLongBits(lat)).putLong("wx_lng", Double.doubleToLongBits(lng))
                .apply();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (listener != null) listener.run();
            });
        } finally {
            http.disconnect();
        }
    }

    /** A screen that wants the new reading at once instead of on its next minute. */
    static Runnable listener;

    /** True when the kept reading is for the place chosen now (not one from before the place changed). */
    private static boolean samePlace(Context c) {
        SharedPreferences p = Prefs.of(c);
        Prefs.City city = Prefs.city(c);
        double lat = Double.longBitsToDouble(p.getLong("wx_lat", 0)), lng = Double.longBitsToDouble(p.getLong("wx_lng", 0));
        return Math.abs(lat - city.lat) < 0.01 && Math.abs(lng - city.lng) < 0.01;
    }

    /** WMO weather interpretation codes (as Open-Meteo documents them), in eight skies. */
    static int sky(int code) {
        if (code == 0) return CLEAR;
        if (code == 1 || code == 2) return PARTLY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return STORM;
        return CLOUDY;
    }
}
