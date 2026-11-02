package app.awanpro.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/**
 * Background photos (assets/photos, built by tools/AwanTV-build-photos.py from tools/photos-list.txt;
 * the Haramain, al-Aqsa, mosques, the night sky and nature, all from Unsplash), on the TV, no network.
 * Home (Settings > Display > Background): one a day, under a shade of the theme's ground colour, darker over a bright
 * photo. The screensaver (Settings > Awan TV > Photos): a new one every ten minutes with the hadith,
 * under a black shade that deepens towards the times; from Isha until Fajr only the dark ones.
 * Calligraphy of the Name of Allah and the Quran ("where": "saver") never goes under Home's tiles. One decoded
 * photo at a time (about 8 MB at 1920x1080, a quarter on a 720p screen).
 */
final class Photos {
    private Photos() {}

    static final int PATTERN = 0, PHOTOS = 1;
    /** How often the photo changes (Settings > Display), in Text.photoEveryChoices' order. */
    static final int EVERY_AUTO = 0, EVERY_10 = 1, EVERY_30 = 2, EVERY_HOUR = 3, EVERY_PRAYER = 4, EVERY_DAY = 5;
    /** Photos at most this bright (0-255) are the night's. */
    private static final int NIGHT_LIGHT = 60;

    static final class Photo {
        final String file, kind;
        final boolean home;
        final int light;

        Photo(JSONObject o) {
            file = o.optString("file");
            kind = o.optString("kind");
            home = "all".equals(o.optString("where"));
            light = o.optInt("light", 128);
        }
    }

    private static List<Photo> all;
    private static String decodedFile;
    private static Bitmap decoded;

    static synchronized List<Photo> all(Context c) {
        if (all != null) return all;
        List<Photo> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open("photos/photos.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            JSONArray a = new JSONObject(new String(b.toByteArray(), StandardCharsets.UTF_8)).optJSONArray("photos");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && !o.optString("file").isEmpty()) out.add(new Photo(o));
            }
        } catch (Exception e) {
            // No photos in this build: the pattern and the black screensaver stay.
        }
        all = Collections.unmodifiableList(out);
        return all;
    }

    /**
     * Which turn of photos now is (the same number until the photo should change): by Prefs.photoEvery, Automatic
     * being a day on Home and ten minutes (with the hadith) on the screensaver; "with each prayer" turns at every
     * adhan (Fajr, Dhuhr, Asr, Maghrib, Isha; before Fajr the night's Isha still holds).
     */
    static long turn(Context c, boolean home, PrayerDay day, java.util.Date now) {
        long t = now.getTime();
        int every = Prefs.photoEvery(c);
        if (every == EVERY_AUTO) every = home ? EVERY_DAY : EVERY_10;
        switch (every) {
            case EVERY_10: return t / Hadith.EVERY_MS;
            case EVERY_30: return t / 1_800_000L;
            case EVERY_HOUR: return t / 3_600_000L;
            case EVERY_PRAYER: return lastAdhan(c, day, now) / 60_000L;
            default: return (t + TimeZone.getDefault().getOffset(t)) / 86_400_000L;
        }
    }

    /** The last adhan at or before now: today's, or before Fajr the night's Isha. */
    private static long lastAdhan(Context c, PrayerDay day, java.util.Date now) {
        long t = now.getTime(), last = -1;
        for (int i : new int[] {PrayerDay.FAJR, PrayerDay.DHUHR, PrayerDay.ASR, PrayerDay.MAGHRIB, PrayerDay.ISHA}) {
            long at = day.times[i].getTime();
            if (at <= t && at > last) last = at;
        }
        return last >= 0 ? last : PrayerDay.lastIsha(c, day, now).getTime();
    }

    /** Home's photo for this turn, or null. */
    static Photo forHome(Context c, long turn) {
        List<Photo> list = new ArrayList<>();
        for (Photo p : all(c)) if (p.home) list.add(p);
        if (list.isEmpty()) return null;
        return list.get((int) (Math.abs(turn) % list.size()));
    }

    /** The screensaver's photo for this turn, or null; at night only the dark ones. */
    static Photo forSaver(Context c, long turn, boolean night) {
        List<Photo> list = new ArrayList<>();
        for (Photo p : all(c)) if (!night || p.light <= NIGHT_LIGHT) list.add(p);
        if (list.isEmpty()) return null;
        return list.get((int) (Math.abs(turn) % list.size()));
    }

    /** Home's background: the photo under the theme's ground colour, from 55% over a dark photo to 80% over a bright one. */
    static Drawable homeBackground(Context c, Photo p) {
        Bitmap b = bitmap(c, p);
        if (b == null) return null;
        boolean light = Tokens.applied == Tokens.PARCHMENT;
        int a = light ? 0xC8 + p.light * 0x20 / 255 : 0x8C + p.light * 0x40 / 255;
        Drawable photo = new BitmapDrawable(c.getResources(), b);
        return new LayerDrawable(new Drawable[] {photo, new ColorDrawable(Tokens.alpha(Tokens.LAPIS_950, a))});
    }

    /**
     * Home's right column (the arch, the month, Today & Qibla) over a photo: its colour a little see-through, 30% over
     * a dark photo to 10% over a bright one, so it shows the photo alike in every one and its small figures stay
     * readable.
     */
    static int panel(Photo p) {
        int clear = 30 - p.light * 20 / 255;
        return Tokens.alpha(Tokens.LAPIS_800, 255 - clear * 255 / 100);
    }

    /** The screensaver's background: the photo under black, 72% at the top to 88% at the bottom, where the times are. */
    static Drawable saverBackground(Context c, Photo p) {
        Bitmap b = bitmap(c, p);
        if (b == null) return null;
        int lift = p.light > 140 ? 0x10 : 0;
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {(0xB8 + lift) << 24, (0xCC + lift) << 24, Math.min(0xF0, 0xE0 + lift) << 24});
        return new LayerDrawable(new Drawable[] {new BitmapDrawable(c.getResources(), b), shade});
    }

    /** The photo decoded at the screen's size; the last one is kept, the one before is let go. */
    private static synchronized Bitmap bitmap(Context c, Photo p) {
        if (p == null) return null;
        if (p.file.equals(decodedFile) && decoded != null) return decoded;
        BitmapFactory.Options o = new BitmapFactory.Options();
        int w = c.getResources().getDisplayMetrics().widthPixels;
        o.inSampleSize = w > 0 && w <= 1280 ? 2 : 1;
        try (InputStream in = c.getAssets().open("photos/" + p.file)) {
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (b == null) return null;
            decoded = b;
            decodedFile = p.file;
            return b;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
