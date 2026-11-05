package app.awanpro.tv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * The morning and evening adhkar shown on the screensaver, from Hisn al-Muslim (assets/adhkar/adhkar.json,
 * built from github.com/Seen-Arabic/Morning-And-Evening-Adhkar-DB, MIT). The text is used exactly as published.
 *
 * <p>When they show (Settings > Reminders): the extended time by default, morning from Fajr to Dhuhr and evening from
 * Asr to Isha, which the four schools allow (Dar al-Ifta al-Misriyya, fatwa 20149); or the preferred time only,
 * Fajr to sunrise and Asr to Maghrib; or never. The closing verses of Al-Baqarah are read at night, so they show
 * after Maghrib only.
 */
final class Adhkar {
    private Adhkar() {}

    static final int OFF = 0, EXTENDED = 1, PREFERRED = 2;
    static final int NONE = 0, MORNING = 1, EVENING = 2;

    /** One dhikr. */
    static final class Dhikr {
        final int order, type, count;
        final String text;
        /** How many times, and where it is reported, in each language it is written in (Languages.fields). */
        final java.util.Map<String, String> counts, refs;
        final boolean night;

        Dhikr(JSONObject o) {
            order = o.optInt("order");
            type = o.optInt("type");
            count = o.optInt("count", 1);
            text = o.optString("text");
            counts = Languages.fields(o, "count");
            refs = Languages.fields(o, "ref");
            night = o.optBoolean("night");
        }

        /**
         * Seconds on screen: long enough to read it through, up to three times for a repeated one (the rest are
         * counted on the fingers), never under 15 s or over two minutes.
         */
        int seconds() {
            int reads = Math.max(1, Math.min(count, 3));
            int s = text.length() * reads / 12 + 6;
            return Math.max(15, Math.min(120, s));
        }
    }

    private static List<Dhikr> all;

    static synchronized List<Dhikr> all(Context c) {
        if (all != null) return all;
        List<Dhikr> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open("adhkar/adhkar.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            JSONArray a = new JSONObject(new String(b.toByteArray(), StandardCharsets.UTF_8)).getJSONArray("adhkar");
            for (int i = 0; i < a.length(); i++) out.add(new Dhikr(a.getJSONObject(i)));
        } catch (Exception e) {
            // A broken asset: no adhkar rather than a crash in the screensaver.
        }
        all = Collections.unmodifiableList(out);
        return all;
    }

    static int mode(Context c) {
        int m = Prefs.of(c).getInt("adhkar_mode", EXTENDED);
        return m >= OFF && m <= PREFERRED ? m : EXTENDED;
    }

    static void setMode(Context c, int mode) {
        Prefs.of(c).edit().putInt("adhkar_mode", mode).apply();
    }

    /** MORNING, EVENING or NONE at this moment of this day. */
    static int period(Context c, PrayerDay day, Date now) {
        int mode = mode(c);
        if (mode == OFF) return NONE;
        long t = now.getTime();
        // After Isha the Fajr slot holds tomorrow's Fajr, which is still ahead: no morning then.
        long fajr = day.times[PrayerDay.FAJR].getTime();
        long morningEnd = day.times[mode == PREFERRED ? PrayerDay.SUNRISE : PrayerDay.DHUHR].getTime();
        if (t >= fajr && t < morningEnd) return MORNING;
        long asr = day.times[PrayerDay.ASR].getTime();
        long eveningEnd = day.times[mode == PREFERRED ? PrayerDay.MAGHRIB : PrayerDay.ISHA].getTime();
        if (t >= asr && t < eveningEnd) return EVENING;
        return NONE;
    }

    /** The adhkar of a period, in Hisn al-Muslim's order; night-only ones once Maghrib has come. */
    static List<Dhikr> of(Context c, int period, PrayerDay day, Date now) {
        List<Dhikr> out = new ArrayList<>();
        if (period == NONE) return out;
        boolean night = !now.before(day.times[PrayerDay.MAGHRIB]);
        for (Dhikr d : all(c)) {
            boolean fits = d.type == 0 || (period == MORNING ? d.type == 1 : d.type == 2);
            if (!fits || (d.night && (period != EVENING || !night))) continue;
            out.add(d);
        }
        return out;
    }

    /** Where each period's round got to, so a screensaver that stops and starts again goes on rather than restarts. */
    private static final int[] position = new int[3];

    static int position(int period) {
        return position[period];
    }

    static void setPosition(int period, int i) {
        position[period] = i;
    }
}
