package app.awanpro.tv;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.Window;

import java.util.Date;

/**
 * The screensaver, as the TV's system screensaver so it starts from any app after the idle timeout. Not interactive:
 * any key wakes the TV back to what was on.
 *
 * <p>The screen itself is {@link SaverScreen}, shared with "Screensaver now" ({@link SaverActivity}).
 * With the screensaver turned off (Settings > Awan TV > Runs) the TV's screensaver goes back to the one Awan TV
 * replaced ({@link Saver}); where Awan TV may not change it, this shows a plain black screen instead.
 */
public class AwanDream extends DreamService {

    /** True while the screensaver is showing; the reminders wait for it, and it steps aside for the call. */
    static boolean running;
    /** The screensaver showing now, for Home's button on an HDMI source box (HomeGuard); else null. */
    static AwanDream current;

    private SaverScreen screen;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** The black screen's only job: wake the TV when the call to prayer is due, so it comes up over what was on. */
    private final Runnable watchCall = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            if (Reminders.dueCall(AwanDream.this, new PrayerDay(AwanDream.this, now), now) >= 0) {
                running = false;
                wakeUp();
                return;
            }
            handler.postDelayed(this, 5000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        if (!Prefs.saver(this)) {
            Saver.giveBack(this);
            View black = new View(this);
            black.setBackgroundColor(Color.BLACK);
            setContentView(black);
            screen = null;
            // Still steps aside for the call to prayer, as the screensaver's own screen does.
            running = true;
            handler.post(watchCall);
            return;
        }
        screen = new SaverScreen(this, new SaverScreen.Host() {
            @Override
            public void wake() {
                wakeUp();
            }

            @Override
            public Window window() {
                return getWindow();
            }

            @Override
            public boolean isDream() {
                return true;
            }
        });
        setContentView(screen.view());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        current = this;
        if (screen != null) screen.start();
    }

    @Override
    public void onDreamingStopped() {
        if (current == this) current = null;
        if (screen != null) screen.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(watchCall);
        if (screen != null) screen.stop();
        running = false;
        if (current == this) current = null;
        super.onDetachedFromWindow();
    }
}
