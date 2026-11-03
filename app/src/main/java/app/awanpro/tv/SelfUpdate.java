package app.awanpro.tv;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Update now (UpdateNotice, Settings > About's window): the release's AwanTV.apk, downloaded from GitHub only when
 * the viewer presses it, streamed into an Android install session (as Afnon's feat/in-app-updates). Android itself
 * refuses an APK signed with another key, cut short, or older than this one, so no checksum of our own is needed.
 * Android 12+ may install it with no dialog (USER_ACTION_NOT_REQUIRED, an app updating itself); otherwise, or where
 * the TV wants it, Android's own "Install?" shows, one OK. Needs the install appop, granted by the phone installer and
 * AwanTV-install (1.7.0); without it {@link #allowed} is false and Update now is not offered: Android would open a
 * permission screen most TVs can't leave with the remote.
 *
 * <p>One download at a time, on a plain thread; its state lives here so Home shows it again when it comes back.
 * Installing replaces the app, which ends this process; the new Home starts by itself.
 */
final class SelfUpdate {
    private SelfUpdate() {}

    static final int IDLE = 0, DOWNLOADING = 1, INSTALLING = 2, FAILED = 3;
    private static final String TAG = "AwanUpdate";
    private static final String DOWNLOAD = Updates.PAGE + "/releases/download/v%s/AwanTV.apk";

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile int state = IDLE, percent;
    private static volatile boolean cancelled;
    private static String version;
    private static Runnable listener;
    /**
     * Android's "Update this app?" for the session waiting on it. Started at once from {@link Result}; but Android 14
     * blocks an activity start while no window of the app is visible (the viewer went into another app meanwhile),
     * so the window offers it again (Continue the installation) when Home comes back.
     */
    private static volatile Intent confirm;

    static boolean confirmWaiting() { return confirm != null && state == INSTALLING; }

    /** Continue the installation: Android's question again. */
    static void confirm(Context c) {
        Intent i = confirm;
        if (i == null) return;
        try {
            c.startActivity(new Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Log.i(TAG, "confirm: " + e);
        }
    }

    /** Test builds only (SettingsTransfer.TEST_UPDATE): a real release's version downloaded instead, e.g. an older one. */
    static volatile String testDownload;

    static int state() { return state; }
    static int percent() { return percent; }
    /** The version being downloaded or installed, or that last failed. */
    static String version() { return version; }
    static boolean busy() { return state == DOWNLOADING || state == INSTALLING; }

    /** The window's redraw while it is shown; null when it isn't. Called on the main thread. */
    static void listen(Runnable r) { listener = r; }

    private static void set(int s) {
        main.post(() -> {
            state = s;
            if (listener != null) listener.run();
        });
    }

    /** Whether this TV lets Awan TV install its update (the install appop from setup). */
    static boolean allowed(Context c) {
        try {
            return c.getPackageManager().canRequestPackageInstalls();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Update now: downloads that version's AwanTV.apk into an install session. */
    static void start(Context c, String v) {
        if (busy() || v == null) return;
        Context app = c.getApplicationContext();
        if (!allowed(app)) return;
        version = v;
        confirm = null;
        cancelled = false;
        percent = 0;
        state = DOWNLOADING;
        if (listener != null) listener.run();
        new Thread(() -> run(app, v), "awan-self-update").start();
    }

    /** A failure seen and closed: Update now is offered again. */
    static void reset() {
        if (state == FAILED) state = IDLE;
    }

    /** Cancel while downloading: the session is abandoned, nothing installed. */
    static void cancel() {
        if (state != DOWNLOADING) return;
        cancelled = true;
        state = IDLE;
        if (listener != null) listener.run();
    }

    private static void run(Context c, String v) {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        int id = -1;
        try {
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(c.getPackageName());
            p.setInstallReason(PackageManager.INSTALL_REASON_USER);
            if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            id = pi.createSession(p);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                String from = testDownload != null ? testDownload : v;
                download(s, String.format(DOWNLOAD, from));
                if (cancelled) throw new IOException("cancelled");
                set(INSTALLING);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent done = PendingIntent.getBroadcast(c, id, new Intent(c, Result.class), flags);
                s.commit(done.getIntentSender());
            }
        } catch (IOException | RuntimeException e) {
            Log.i(TAG, "update " + v + ": " + e);
            if (id >= 0) {
                try {
                    pi.abandonSession(id);
                } catch (RuntimeException ignored) {
                    // Already gone.
                }
            }
            if (!cancelled) set(FAILED);
        }
    }

    /** Streams the APK into the session, with the window's percentage; stops when cancelled. */
    private static void download(PackageInstaller.Session s, String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", "AwanTV (" + Updates.SITE + ")");
        try {
            if (c.getResponseCode() != 200) throw new IOException("GitHub answered " + c.getResponseCode());
            long total = c.getContentLengthLong(), done = 0;
            int shown = -1;
            try (InputStream in = c.getInputStream(); OutputStream out = s.openWrite("AwanTV.apk", 0, total)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled) throw new IOException("cancelled");
                    out.write(buf, 0, n);
                    done += n;
                    int pct = total > 0 ? (int) (done * 100 / total) : 0;
                    if (pct != shown) {
                        shown = pct;
                        main.post(() -> {
                            if (state != DOWNLOADING) return;
                            percent = pct;
                            if (listener != null) listener.run();
                        });
                    }
                }
                s.fsync(out);
            }
            if (total > 0 && done != total) throw new IOException("download cut short");
        } finally {
            c.disconnect();
        }
    }

    /** Android's answer to the install. On success the process is already being replaced. */
    public static final class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                    // Android's own "Install?": one OK.
                    Intent ask = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (ask == null) break;
                    confirm = ask;
                    set(INSTALLING);   // the window offers Continue the installation from now on
                    try {
                        c.startActivity(new Intent(ask).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException ignored) {
                        // Shown again from the window.
                    }
                    return;
                }
                case PackageInstaller.STATUS_SUCCESS:
                    confirm = null;
                    return;
                case PackageInstaller.STATUS_FAILURE_ABORTED:
                    // Cancelled in Android's dialog: Update now is offered again.
                    confirm = null;
                    set(IDLE);
                    return;
                default:
                    break;
            }
            Log.i(TAG, "install refused: " + status + " " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            confirm = null;
            set(FAILED);
        }
    }
}
