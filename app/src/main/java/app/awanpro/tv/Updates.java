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

/**
 * Where Awan TV lives, and whether a newer version is out.
 *
 * <p>Settings > About > New versions (on by default) lets Home ask GitHub for the latest release at most once a
 * day, on Home's minute tick (no timer or service of its own): one small request, nothing about the TV sent. A newer
 * version shows in Settings > About under Source and updates. Nothing is downloaded or installed: updating stays with
 * the installer, which carries the release. Off, nothing is asked. With no releases yet (or the repository still
 * private) GitHub answers 404 and nothing shows. Settings > About > Check for updates now asks at once
 * ({@link #checkNow}), on or off.
 */
final class Updates {
    private Updates() {}

    /** Shown in Settings > About, and in its QR codes (tools/AwanTV-build-qr.py: change both together). */
    static final String SITE = "https://awanpro.app";
    static final String PAGE = "https://github.com/OmarGHub/AwanTV";
    private static final String LATEST = "https://api.github.com/repos/OmarGHub/AwanTV/releases/latest";

    private static final long EVERY_MS = 24 * 3600_000L, RETRY_MS = 3 * 3600_000L;
    private static final int TIMEOUT_MS = 10_000;
    private static volatile boolean fetching;

    /** An address as shown on screen: without "https://", short enough to read and type. */
    static String shown(String url) {
        return url.replaceFirst("^https?://", "");
    }

    /** The newer version out on GitHub ("1.1.0"), or null: none, off, or not asked yet. */
    static String newer(Context c, String current) {
        if (!Prefs.updateCheck(c)) return null;
        String latest = Prefs.of(c).getString("upd_latest", "");
        return !latest.isEmpty() && compare(latest, current) > 0 ? latest : null;
    }

    /** Asks GitHub when the last answer is a day old (three hours after a failed try). Home's minute tick. */
    static void refresh(Context c) {
        if (!Prefs.updateCheck(c) || fetching) return;
        SharedPreferences p = Prefs.of(c);
        long now = System.currentTimeMillis();
        long ok = p.getLong("upd_at", 0), tried = p.getLong("upd_try", 0);
        // A clock set back (a TV without the time yet) must not stop the checks for good.
        if (ok > now) ok = 0;
        if (tried > now) tried = 0;
        if (now - ok < EVERY_MS || now - tried < RETRY_MS) return;
        p.edit().putLong("upd_try", now).apply();
        fetching = true;
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                read(app);
            } catch (Exception e) {
                Log.i(HomeGuard.TAG, "new versions: " + e);
            } finally {
                fetching = false;
            }
        }, "awan-updates").start();
    }

    /** The answer to {@link #checkNow}: the latest version out ("1.0.1", "" when there is none), or null if unreachable. */
    interface Checked {
        void done(String latest);
    }

    /** Asks GitHub now, whatever the daily setting and timer say; the answer comes on the main thread. */
    static void checkNow(Context c, Checked done) {
        Context app = c.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String latest;
            try {
                latest = read(app);
            } catch (Exception e) {
                Log.i(HomeGuard.TAG, "check for updates: " + e);
                latest = null;
            }
            String answer = latest;
            main.post(() -> done.done(answer));
        }, "awan-updates-now").start();
    }

    /** One question to GitHub; the answer kept for {@link #newer}. Returns the latest tag ("" when none). */
    private static String read(Context c) throws Exception {
        HttpURLConnection http = (HttpURLConnection) new URL(LATEST).openConnection();
        http.setConnectTimeout(TIMEOUT_MS);
        http.setReadTimeout(TIMEOUT_MS);
        // GitHub asks every caller for a User-Agent.
        http.setRequestProperty("User-Agent", "AwanTV (" + SITE + ")");
        http.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int code = http.getResponseCode();
            String tag = "";
            if (code == 200) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                try (InputStream in = http.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0 && body.size() < 256 * 1024) body.write(buf, 0, n);
                }
                tag = new JSONObject(body.toString("UTF-8")).optString("tag_name", "").trim().replaceFirst("^[vV]", "");
            } else if (code != 404) {
                throw new java.io.IOException("HTTP " + code);
            }
            Prefs.of(c).edit().putString("upd_latest", tag).putLong("upd_at", System.currentTimeMillis()).apply();
            return tag;
        } finally {
            http.disconnect();
        }
    }

    /** Versions by their numbers ("1.10.0" after "1.9.2"); anything after a number ("1.1.0-beta") is ignored. */
    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int d = Integer.compare(number(x, i), number(y, i));
            if (d != 0) return d;
        }
        return 0;
    }

    private static int number(String[] parts, int i) {
        if (i >= parts.length) return 0;
        String digits = parts[i].replaceFirst("^(\\d*).*$", "$1");
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
