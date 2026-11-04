package app.awanpro.tv;

import android.app.Application;
import android.os.Build;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** The app's process (1.13.0): only to put the safety net ({@link Crash}) in place before any screen or service. */
public class AwanApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // The crash screen's own process (":crash") goes without: a stop there is Android's to show, never a loop.
        if (!processName().endsWith(":crash")) Crash.install(this);
    }

    private String processName() {
        if (Build.VERSION.SDK_INT >= 28) {
            String n = Application.getProcessName();
            if (n != null) return n;
        }
        try {
            return new String(Files.readAllBytes(Paths.get("/proc/self/cmdline")), StandardCharsets.UTF_8).trim()
                .replace("\u0000", "");
        } catch (Exception e) {
            return "";
        }
    }
}
