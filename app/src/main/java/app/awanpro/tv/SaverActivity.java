package app.awanpro.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

/**
 * The screensaver's screen as an app. "Screensaver now" (Home's button, Settings > Awan TV) opens it at
 * once; Home opens it by itself after the chosen minutes without a key ({@link #IDLE_SINCE}) where the TV's own
 * screensaver is not Awan TV's. Any key closes it, as with the screensaver.
 *
 * <p>Opened by Home after idle minutes, it keeps the screen on, or the TV's own screensaver would come over it at
 * once; until the TV's "turn off after" time (secure sleep_timeout) since the last key, then it lets go and the
 * TV sleeps as it is set to. While the radio plays it stays on (the radio's own dark setting applies).
 */
public class SaverActivity extends Activity {

    /** When the last key was pressed, for the screensaver Home opened after idle minutes. */
    static final String IDLE_SINCE = "idle_since";
    /** Where the TV's "turn off after" cannot be read: let go after this long without a key. */
    private static final long FALLBACK_SLEEP_MS = 4 * 3600_000L;
    /** And never sooner than this after the screensaver opened. */
    private static final long LEAST_MS = 15 * 60_000L;

    /**
     * "Screensaver now": where the TV's own screensaver is not Awan TV's, it would come over this one at the TV's
     * timeout, so this one keeps the screen on as after idle minutes on Home.
     */
    static Intent now(Context c) {
        Intent i = new Intent(c, SaverActivity.class);
        if (!Saver.isOurs(c)) i.putExtra(IDLE_SINCE, System.currentTimeMillis());
        return i;
    }

    private SaverScreen screen;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable letGo = new Runnable() {
        @Override
        public void run() {
            if (Radio.on()) {
                handler.postDelayed(this, 60_000);
                return;
            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    };
    /** A key went down here: only its release closes, not the release of the OK that opened this screen. */
    private boolean sawDown;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        screen = new SaverScreen(this, new SaverScreen.Host() {
            @Override
            public void wake() {
                finish();
            }

            @Override
            public Window window() {
                return getWindow();
            }

            @Override
            public boolean isDream() {
                return false;
            }
        });
        setContentView(screen.view());
        long idleSince = getIntent().getLongExtra(IDLE_SINCE, 0);
        if (idleSince > 0) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            long after = sleepAfter();
            if (after > 0) {
                long now = System.currentTimeMillis();
                handler.postDelayed(letGo, Math.max(idleSince + after, now + LEAST_MS) - now);
            }
        }
    }

    /** The TV's "turn off after" in ms; 0 for never (stays on), the fallback where it cannot be read. */
    private long sleepAfter() {
        try {
            int ms = Settings.Secure.getInt(getContentResolver(), "sleep_timeout", -2);
            // While the radio plays it has set "never" for the time being: the TV's own value is the one it kept.
            if (ms == -1) ms = Prefs.of(this).getInt("sleep_saved", -1);
            if (ms == -1) return 0;
            return ms > 0 ? ms : FALLBACK_SLEEP_MS;
        } catch (RuntimeException notReadable) {
            return FALLBACK_SLEEP_MS;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(letGo);
        super.onDestroy();
    }

    /** This screensaver is in front (it shows the iqamah itself; no countdown over it). */
    static boolean showing;

    @Override
    protected void onResume() {
        super.onResume();
        showing = true;
        screen.start();
        if (RemoteKeys.watch != null) RemoteKeys.watch.refreshCounter();
    }

    @Override
    protected void onPause() {
        showing = false;
        screen.stop();
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // Volume and mute stay the TV's; any other key closes it on release.
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) sawDown = true;
        else if (e.getAction() == KeyEvent.ACTION_UP && sawDown) finish();
        return true;
    }
}
