package app.awanpro.tv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;

import java.io.IOException;

/**
 * The adhan recordings shipped in assets/adhan (from github.com/abodehq/Athan-MP3, no licence stated; for this TV
 * only). Which one plays is chosen in Settings, with a separate choice for Fajr.
 */
final class Adhans {
    private Adhans() {}

    /** Id (assets/adhan/&lt;id&gt;.mp3), Arabic name, English name. */
    static final String[][] VOICES = {
        {"alafasy", "مشاري العفاسي", "Mishary Alafasy"},
        {"almalki", "حمدان المالكي", "Hamdan Al-Malki"},
        {"alarkani", "إبراهيم الأركاني", "Ibrahim Al-Arkani"},
        {"chebae_fajr", "مالك شيباي (أذان الفجر)", "Malek Chebae (Fajr)"},
        {"alhamathani", "ماجد الهمذاني", "Majed Al-Hamathani"},
        {"alzahrani", "منصور الزهراني", "Mansoor Az-Zahrani"},
        {"deghreri", "حمد الدغريري", "Hamad Al-Deghreri"},
        {"alqatami", "ناصر القطامي", "Nasser Al-Qatami"},
        {"khatba", "صهيب خطبا", "Suhaib Khatba"},
        {"menshawy", "محمد صديق المنشاوي", "Mohammad Al-Menshawy"},
        {"refaat", "محمد رفعت", "Mohammad Refaat"},
        {"abdulbasit", "عبد الباسط عبد الصمد", "Abdul Basit"},
        {"nuyne", "أحمد نعينع", "Ahmad Nuaina"},
        {"makkah", "الحرم المكي", "Makkah, Al-Haram"},
    };

    /** True when this build carries the recordings (tools/AwanTV-fetch-adhans before building; not in the source). */
    static boolean present(Context c) {
        if (present == null) {
            boolean any = false;
            try {
                String[] names = c.getAssets().list("adhan");
                if (names != null) for (String n : names) if (n.endsWith(".mp3")) { any = true; break; }
            } catch (IOException e) {
                // No folder: none.
            }
            present = any;
        }
        return present;
    }

    private static Boolean present;

    static int index(String id) {
        for (int i = 0; i < VOICES.length; i++) if (VOICES[i][0].equals(id)) return i;
        return 0;
    }

    static String name(Text t, String id) {
        String[] v = VOICES[index(id)];
        return t.ar ? v[1] : v[2];
    }

    /** Starts a recording at once; null if it can't be read. The caller releases it. */
    static MediaPlayer play(Context c, String id) {
        MediaPlayer mp = new MediaPlayer();
        try (AssetFileDescriptor fd = c.getAssets().openFd("adhan/" + id + ".mp3")) {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mp.prepare();
            mp.start();
            return mp;
        } catch (IOException | RuntimeException e) {
            mp.release();
            return null;
        }
    }

    /**
     * A recording's own level, a percent of the TV's sound (Fajr's level). The player's volume is
     * an amplitude, and loudness follows it to about the power 0.6 (half as loud is about 10 dB down), so 50% is
     * 0.32, 25% 0.10. Only this recording changes: not the TV's volume, so it works with a soundbar over HDMI too.
     */
    static void level(MediaPlayer mp, int percent) {
        if (mp == null || percent >= 100) return;
        float v = (float) Math.pow(Math.max(0, percent) / 100.0, 1 / 0.6);
        try {
            mp.setVolume(v, v);
        } catch (IllegalStateException ignored) {
            // Already released.
        }
    }

    static void stop(MediaPlayer mp) {
        if (mp == null) return;
        try {
            mp.stop();
        } catch (IllegalStateException ignored) {
            // Already finished.
        }
        mp.release();
    }
}
