package app.awanpro.tv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Short hadiths from Sahih al-Bukhari and Sahih Muslim for the screensaver's middle outside the adhkar
 * times, on the TV (assets/hadith/hadith.json, no network). The text is the books' own, vocalised, from
 * github.com/fawazahmed0/hadith-api (The Unlicense, public domain), cut and checked by tools/AwanTV-build-hadith.py;
 * the numbers are the books' (al-Bukhari as in Fath al-Bari, Muslim as numbered by Muhammad Fu'ad Abd al-Baqi).
 * The same file holds the supplication after the adhan (NowActivity) and Hisn al-Muslim's adhkar after
 * the prayer (the screensaver), the Quranic ones taken unchanged from assets/adhkar/adhkar.json.
 */
final class Hadith {
    private Hadith() {}

    /** Each one stays this long; the same one shows on every TV at the same time. */
    static final long EVERY_MS = 10 * 60_000L;

    static final class Item {
        final String text;
        /** How many times, and where it is reported, in each language it is written in (Languages.fields). */
        final java.util.Map<String, String> counts, refs;
        final int bukhari, muslim;

        Item(JSONObject o) {
            text = o.optString("text");
            bukhari = o.optInt("bukhari");
            muslim = o.optInt("muslim");
            counts = Languages.fields(o, "count");
            refs = Languages.fields(o, "ref");
        }

        /** Seconds on screen after the prayer: as a dhikr (Adhkar), never under 15 s or over 90. */
        int seconds() {
            return Math.max(15, Math.min(90, text.length() / 12 + 8));
        }
    }

    private static List<Item> all, afterAdhan, afterPrayer;

    static synchronized List<Item> all(Context c) {
        load(c);
        return all;
    }

    /** The supplication after the adhan; empty if the asset has none. */
    static synchronized List<Item> afterAdhan(Context c) {
        load(c);
        return afterAdhan;
    }

    /** The adhkar after the prayer, in Hisn al-Muslim's order. */
    static synchronized List<Item> afterPrayer(Context c) {
        load(c);
        return afterPrayer;
    }

    private static void load(Context c) {
        if (all != null) return;
        JSONObject o = null;
        try (InputStream in = c.getAssets().open("hadith/hadith.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            o = new JSONObject(new String(b.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            // A broken asset: nothing to show rather than a crash in the screensaver.
        }
        all = list(o, "hadiths");
        afterAdhan = list(o, "afterAdhan");
        afterPrayer = list(o, "afterPrayer");
    }

    private static List<Item> list(JSONObject o, String key) {
        List<Item> out = new ArrayList<>();
        JSONArray a = o == null ? null : o.optJSONArray(key);
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e == null) continue;
            Item h = new Item(e);
            if (!h.text.isEmpty()) out.add(h);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * When the ten minutes of adhkar after a prayer began, if now is inside them, or -1. They start
     * seven minutes after the iqamah (the prayer done), or twenty after the adhan where there is no iqamah time,
     * forty on Friday at Dhuhr (the khutbah); each dhikr stays for its time and they go round. Once a minute.
     */
    static long afterPrayerWindow(Context c, PrayerDay day, java.util.Date now) {
        List<Item> list = afterPrayer(c);
        if (list.isEmpty() || !Prefs.afterPrayer(c)) return -1;
        long t = now.getTime();
        for (int i : new int[] {PrayerDay.FAJR, PrayerDay.DHUHR, PrayerDay.ASR, PrayerDay.MAGHRIB, PrayerDay.ISHA}) {
            long start = afterPrayerStart(c, day, i);
            if (start >= 0 && t >= start && t < start + AFTER_PRAYER_MS) return start;
        }
        // After midnight, the night's Isha is yesterday's: a late one (or its iqamah) runs into the new day.
        if (day.times[PrayerDay.ISHA].after(now)) {
            PrayerDay yesterday = new PrayerDay(c, new java.util.Date(t - 24 * 3600_000L));
            long start = afterPrayerStart(c, yesterday, PrayerDay.ISHA);
            if (start >= 0 && t >= start && t < start + AFTER_PRAYER_MS) return start;
        }
        return -1;
    }

    /** The dhikr shown at nowMs in a window that began at start ({@link #afterPrayerWindow}); quick, every second. */
    static Item afterPrayerItem(Context c, long start, long nowMs) {
        List<Item> list = afterPrayer(c);
        if (start < 0 || list.isEmpty()) return null;
        long into = Math.max(0, nowMs - start) / 1000, round = 0;
        for (Item h : list) round += h.seconds();
        into %= Math.max(1, round);
        for (Item h : list) {
            if (into < h.seconds()) return h;
            into -= h.seconds();
        }
        return list.get(0);
    }

    static final long AFTER_PRAYER_MS = 10 * 60_000L;

    /** When the adhkar after prayer i begin today, or -1. */
    private static long afterPrayerStart(Context c, PrayerDay day, int i) {
        java.util.Date at = day.times[i];
        // After Isha the Fajr slot holds tomorrow's, still ahead, so it simply does not match yet.
        if (day.friday && i == PrayerDay.DHUHR) return at.getTime() + 40 * 60_000L;
        java.util.Date iqama = Iqama.mode(c) == Iqama.OFF ? null : Iqama.at(c, day, i);
        return iqama != null ? iqama.getTime() + 7 * 60_000L : at.getTime() + 20 * 60_000L;
    }

    /** The one for this ten minutes, in the list's order; null when there are none. */
    static Item at(Context c, long nowMs) {
        List<Item> list = all(c);
        if (list.isEmpty()) return null;
        return list.get((int) ((nowMs / EVERY_MS) % list.size()));
    }
}
