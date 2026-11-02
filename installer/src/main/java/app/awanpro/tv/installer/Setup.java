package app.awanpro.tv.installer;

import android.content.Context;
import android.content.pm.PackageInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

/**
 * What the installer puts on the TV: the Awan TV release it carries (assets/AwanTV.apk, copied in at build time, so
 * nothing is downloaded and no repository has to be public), and the setup after the install, the same steps as
 * tools/AwanTV-install.ps1: the screensaver it replaces kept, the permissions, TCL's app ops, Awan TV as the
 * screensaver, its accessibility service on and bound, and Awan TV opened with the screensaver to put back. Nothing
 * on the TV is turned off or uninstalled (unlike Afnon's installer, which takes the stock launcher's place).
 */
final class Setup {
    private Setup() {}

    static final String PACKAGE = "app.awanpro.tv";
    private static final String ASSET = "AwanTV.apk";
    /** The screensaver Awan TV replaces, kept on the TV (shell can write here) so any phone or a PC finds it. */
    private static final String SAVER_BEFORE = "/data/local/tmp/awantv-saver-before.txt";
    private static final String SERVICE = PACKAGE + "/" + PACKAGE + ".RemoteKeys";
    private static final String DREAM = PACKAGE + "/" + PACKAGE + ".AwanDream";
    private static final String PERMISSIONS = "RECORD_AUDIO WRITE_SECURE_SETTINGS ACCESS_FINE_LOCATION";
    /**
     * Shell: $st, the storage permissions this TV's Android has for the saved recitations in Music/Awan TV: audio
     * read on 13+, storage read on 11 and 12, read and write on 10 and older.
     */
    private static final String STORAGE = "s=$(getprop ro.build.version.sdk); if [ \"$s\" -ge 33 ]; then st=READ_MEDIA_AUDIO; "
            + "elif [ \"$s\" -ge 30 ]; then st=READ_EXTERNAL_STORAGE; else st=\"READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE\"; fi; ";
    /** Bound, not only listed: a TCL BeyondTV (Android TV 11) kept it enabled but unbound until the list changed. */
    private static final String BOUND = "dumpsys accessibility | grep 'Bound services:' | grep -q 'label=Awan TV'";
    private static final String LIST = "settings get secure enabled_accessibility_services";
    private static final String NOTHING_KEY = "KEYCODE_SHIFT_LEFT";
    /** Listed in its full or short form ("app.awanpro.tv/.RemoteKeys": Android may write the list back so). */
    private static final String LISTED = LIST + " | tr ':' '\\n' | grep -qE '^app\\.awanpro\\.tv/(app\\.awanpro\\.tv)?\\.RemoteKeys$'";
    /** The other apps' services, each followed by ":". */
    private static final String OTHERS = "$(" + LIST + " | tr ':' '\\n' | grep -v '^" + PACKAGE
            + "/' | grep -v '^null$' | grep . | tr '\\n' ':')";
    /**
     * Up to three tries, each until the system bound it: missing from the list (an update force-stops the app, and
     * Android may take its service out a moment after the install answered: a Google TV Streamer did) it is listed
     * again; listed but unbound (the BeyondTV) the list is deleted and written again a second later, since Android
     * reads the list when it hears of a change and may only see the last write. Other apps' services stay.
     */
    private static final String SERVICE_ON = "i=0; while [ $i -lt 3 ]; do i=$((i+1)); L=\"" + OTHERS + SERVICE + "\"; "
            + "if [ $i -gt 1 ] && " + LISTED + "; then settings delete secure enabled_accessibility_services; sleep 1; fi; "
            + "if [ $i -eq 1 ] || ! " + LISTED + "; then settings put secure enabled_accessibility_services \"$L\"; fi; "
            + "settings put secure accessibility_enabled 1; sleep 3; " + BOUND + " && break; done; true";

    /** One setup command, and the check that tells it is in place (null: none). */
    static final class Step {
        final boolean required;
        final String en, ar, command, check;
        Step(boolean required, String en, String ar, String command, String check) {
            this.required = required;
            this.en = en;
            this.ar = ar;
            this.command = command;
            this.check = check;
        }
        String label(boolean arabic) { return arabic ? ar : en; }
    }

    /** In order; a step whose check already passes is left as it is. */
    static final List<Step> STEPS = Arrays.asList(
        new Step(false, "The TV's screensaver kept, to put back", "حفظ شاشة الاستراحة الحالية لإعادتها لاحقًا",
            "s=$(settings get secure screensaver_components); case \"$s\" in ''|null|" + PACKAGE + "/*) ;; *) [ -s "
                + SAVER_BEFORE + " ] || echo \"$s\" > " + SAVER_BEFORE + " ;; esac; true",
            null),
        new Step(true, "Permissions (radio, sleep timer, screensaver, location, saved recitations)",
            "الأذونات (الإذاعة، ومؤقت النوم، وشاشة الاستراحة، والموقع، والتلاوات المحفوظة)",
            STORAGE + "for p in " + PERMISSIONS + " $st; do pm grant " + PACKAGE + " android.permission.$p 2>/dev/null; done; true",
            STORAGE + "d=$(dumpsys package " + PACKAGE + "); ok=0; for p in " + PERMISSIONS
                + " $st; do echo \"$d\" | grep -q \"android.permission.$p: granted=true\" || ok=1; done; [ $ok = 0 ]"),
        new Step(false, "Starts by itself after a restart (TCL)", "يعمل وحده بعد إعادة التشغيل (شاشات TCL)",
            "for o in APP_AUTO_START APP_ASSOC_START AUTO_START; do cmd appops set " + PACKAGE
                + " $o allow 2>/dev/null; done; true",
            "o=$(cmd appops get " + PACKAGE + " APP_AUTO_START 2>&1); case \"$o\" in *allow*) true ;; *) false ;; esac"),
        new Step(false, "Awan TV is the screensaver", "Awan TV شاشة الاستراحة",
            "settings put secure screensaver_components " + DREAM + " && settings put secure screensaver_enabled 1",
            "settings get secure screensaver_components | grep -q '" + DREAM + "'"),
        new Step(true, "Home over the TV's own, and the adhan over any app", "الشاشة الرئيسية والأذان فوق أي تطبيق",
            SERVICE_ON, LISTED + " && " + BOUND),
        // A key that does nothing (Shift) around the start: the TV sat untouched through the install, so it may be
        // idle or already in its screensaver, and "am start" is no user activity: it would open straight into the
        // screensaver. The key counts as activity and closes a screensaver, so the city question shows. Not WAKEUP: on
        // an HDMI source box that can send HDMI-CEC to the TV and soundbar.
        new Step(false, "Awan TV opened on the TV", "فتح Awan TV على التلفاز",
            "input keyevent " + NOTHING_KEY + "; b=$(cat " + SAVER_BEFORE + " 2>/dev/null); case \"$b\" in ''|null|"
                + PACKAGE + "/*) am start -n " + PACKAGE + "/.MainActivity >/dev/null ;; *) am start -n " + PACKAGE
                + "/.MainActivity --es saver_before \"$b\" >/dev/null ;; esac; sleep 2; input keyevent " + NOTHING_KEY,
            null));

    /** The carried Awan TV, copied out to the cache (to stream it with its size), checked to be a whole APK. */
    static File bundled(Context c) throws IOException {
        File apk = new File(c.getCacheDir(), ASSET);
        File part = new File(c.getCacheDir(), ASSET + ".part");
        try (InputStream in = c.getAssets().open(ASSET); OutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        try (RandomAccessFile f = new RandomAccessFile(part, "r")) {
            if (f.length() < 1024 || f.readInt() != 0x504b0304) throw new IOException("The carried Awan TV is not an APK");
        }
        if (apk.exists() && !apk.delete()) throw new IOException("Can't replace the old copy");
        if (!part.renameTo(apk)) throw new IOException("Can't copy Awan TV out");
        return apk;
    }

    /** The carried Awan TV's version name ("1.0.0"), or "" if it can't be read. */
    static String version(Context c, File apk) {
        PackageInfo info = c.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
        return info == null || info.versionName == null ? "" : info.versionName;
    }
}
