package app.awanpro.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The safety net (1.13.0): an error nothing caught ends the app, as before, but first it is written down and, when one
 * of Awan TV's screens was in front, {@link CrashActivity} opens in its own process in place of Android's "keeps
 * stopping". The idea is CustomActivityOnCrash's (Ereza, Apache 2.0), written here without the library.
 *
 * The danger on a TV is a loop: HomeGuard opens Home whenever the TV's own home comes up, so a Home that stops as it
 * opens would stop again and again. Two stops within {@link #REPEAT_MS} hold Home off for {@link #HOLD_MS}: HomeGuard
 * leaves the TV's own home up meanwhile.
 *
 * Kept in files, not prefs: the crash screen runs in another process, and each process keeps its own copy of the
 * prefs in memory, so a change made in one would not be seen by the other.
 */
final class Crash {
    private Crash() {}

    private static final String TAG = "AwanCrash";
    static final long REPEAT_MS = 2 * 60_000L, HOLD_MS = 30 * 60_000L;
    private static final String LAST = "crash.txt", HOLD = "crash_hold";
    /** When one of Awan TV's screens last began (uptime): a screen that stops while being built is not in front yet. */
    static volatile long screenStarted = -1;

    /** Once per process (AwanApp), but not in the crash screen's own: a stop there is Android's to show. */
    static void install(Context c) {
        Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        if (before instanceof Net) return;
        Thread.setDefaultUncaughtExceptionHandler(new Net(app, before));
    }

    private static final class Net implements Thread.UncaughtExceptionHandler {
        private final Context app;
        private final Thread.UncaughtExceptionHandler before;

        Net(Context app, Thread.UncaughtExceptionHandler before) {
            this.app = app;
            this.before = before;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable e) {
            try {
                long now = System.currentTimeMillis();
                long prev = lastAt(app);
                boolean repeat = prev > 0 && now >= prev && now - prev < REPEAT_MS;
                boolean starting = screenStarted >= 0 && SystemClock.uptimeMillis() - screenStarted < 10_000;
                String front = Screen.frontName();
                String where = front != null ? front : starting ? "starting a screen" : "background (" + thread.getName() + ")";
                write(app, LAST, now + "\n" + summary(app, e, where, now));
                if (repeat) write(app, HOLD, String.valueOf(now + HOLD_MS));
                Log.e(TAG, "Awan TV stopped (" + where + (repeat ? ", again: Home held" : "") + ")", e);
                // Shown only where someone is at the remote: not over another app for a stop in the background.
                if (front != null || starting) {
                    app.startActivity(new Intent(app, CrashActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(CrashActivity.REPEAT, repeat));
                }
            } catch (Throwable broken) {
                // The net itself failed: Android's own handling, as without it.
                if (before != null) {
                    before.uncaughtException(thread, e);
                    return;
                }
            }
            Process.killProcess(Process.myPid());
            System.exit(10);
        }
    }

    /** The lines to report: version, Android, model, when and where, the error and its first frames. Nothing personal. */
    private static String summary(Context c, Throwable e, String where, long now) {
        StringBuilder b = new StringBuilder();
        b.append("Awan TV ").append(UpdateNotice.installed(c)).append(" · Android ").append(Build.VERSION.RELEASE)
            .append(" · ").append(Build.MODEL).append('\n');
        b.append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date(now))).append(" · ").append(where);
        Throwable root = e;
        for (int i = 0; i < 10 && root.getCause() != null && root.getCause() != root; i++) root = root.getCause();
        b.append('\n').append(clean(e.toString()));
        if (root != e) b.append('\n').append("Caused by: ").append(clean(root.toString()));
        StackTraceElement[] frames = root.getStackTrace();
        // The first four frames, and Awan TV's own first one when they are all Android's.
        int n = Math.min(4, frames.length);
        boolean ours = false;
        for (int i = 0; i < n; i++) {
            b.append("\n  at ").append(frames[i]);
            ours |= frames[i].getClassName().startsWith("app.awanpro.tv");
        }
        for (int i = n; !ours && i < frames.length; i++) {
            if (!frames[i].getClassName().startsWith("app.awanpro.tv")) continue;
            b.append("\n  …\n  at ").append(frames[i]);
            ours = true;
        }
        return b.toString();
    }

    /** One line, short, with no network address in it (a connection error may name one). */
    private static String clean(String s) {
        s = s.replaceAll("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b", "<address>").replace('\n', ' ');
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /** The last stop: [when (ms), its lines], or null when none was written. */
    static String[] last(Context c) {
        try {
            String all = new String(Files.readAllBytes(new File(c.getFilesDir(), LAST).toPath()), StandardCharsets.UTF_8);
            int nl = all.indexOf('\n');
            if (nl < 0) return null;
            return new String[] {all.substring(0, nl), all.substring(nl + 1)};
        } catch (Exception e) {
            return null;
        }
    }

    static long lastAt(Context c) {
        String[] l = last(c);
        if (l == null) return 0;
        try {
            return Long.parseLong(l[0].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Whether HomeGuard leaves the TV's own home up now (after repeated stops, or the viewer chose it). */
    static boolean holding(Context c) {
        try {
            File f = new File(c.getFilesDir(), HOLD);
            if (!f.exists()) return false;
            long until = Long.parseLong(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
            long now = System.currentTimeMillis();
            // A clock set forward or back must not hold Home off for good.
            return now < until && until - now <= HOLD_MS;
        } catch (Exception e) {
            return false;
        }
    }

    /** Home held off for {@link #HOLD_MS} from now: the viewer chose the TV's own home on the crash screen. */
    static void hold(Context c) {
        write(c, HOLD, String.valueOf(System.currentTimeMillis() + HOLD_MS));
    }

    /** Restart chosen: HomeGuard opens Home again at once. */
    static void release(Context c) {
        //noinspection ResultOfMethodCallIgnored
        new File(c.getFilesDir(), HOLD).delete();
    }

    private static void write(Context c, String name, String text) {
        File f = new File(c.getFilesDir(), name);
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
            os.getFD().sync();
        } catch (Exception e) {
            Log.w(TAG, "could not write " + name, e);
        }
    }
}
