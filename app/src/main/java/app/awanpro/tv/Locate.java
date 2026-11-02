package app.awanpro.tv;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

/**
 * Where the TV is, once, for the first run's "Choose your location": from Android's own location service (TVs have no
 * GPS; their place comes from the Wi-Fi networks around them, through the network or fused provider). A last fix
 * less than a day old answers at once; otherwise one fresh fix is asked for, with a time limit. Null when there is no
 * permission (only an install through our tools grants it; Awan TV never asks), location is off, or nothing came in
 * time. The place is only matched against the city list inside the app; it is not sent anywhere or kept.
 */
final class Locate {
    private Locate() {}

    interface Done { void found(Location where); }

    private static final long FRESH_MS = 24 * 3600_000L;

    /** Answers on the main thread, exactly once. */
    static void once(Context c, long timeoutMs, Done done) {
        Handler main = new Handler(Looper.getMainLooper());
        boolean[] over = {false};
        Done once = l -> main.post(() -> {
            if (over[0]) return;
            over[0] = true;
            done.found(l);
        });
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        boolean allowed = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (lm == null || !allowed || (Build.VERSION.SDK_INT >= 28 && !lm.isLocationEnabled())) {
            once.found(null);
            return;
        }
        try {
            List<String> on = lm.getProviders(true);
            Location best = null;
            for (String p : on) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && System.currentTimeMillis() - l.getTime() < FRESH_MS
                        && (best == null || l.getAccuracy() < best.getAccuracy())) best = l;
            }
            if (best != null) {
                once.found(best);
                return;
            }
            String provider = on.contains("fused") ? "fused" : on.contains(LocationManager.NETWORK_PROVIDER)
                ? LocationManager.NETWORK_PROVIDER : on.contains(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
            if (provider == null) {
                once.found(null);
                return;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                CancellationSignal stop = new CancellationSignal();
                lm.getCurrentLocation(provider, stop, c.getMainExecutor(), once::found);
                main.postDelayed(() -> {
                    stop.cancel();
                    once.found(null);
                }, timeoutMs);
            } else {
                LocationListener listener = new LocationListener() {
                    @Override public void onLocationChanged(Location l) { once.found(l); }
                    @Override public void onStatusChanged(String p, int s, Bundle b) {}
                    @Override public void onProviderEnabled(String p) {}
                    @Override public void onProviderDisabled(String p) {}
                };
                requestSingle(lm, provider, listener);
                main.postDelayed(() -> {
                    lm.removeUpdates(listener);
                    once.found(null);
                }, timeoutMs);
            }
        } catch (RuntimeException e) {
            // A provider that went away, or a permission taken back meanwhile: the choice still opens.
            Log.i(HomeGuard.TAG, "location: " + e);
            once.found(null);
        }
    }

    @SuppressWarnings("deprecation") // requestSingleUpdate: Android 8 to 10 have no getCurrentLocation.
    private static void requestSingle(LocationManager lm, String provider, LocationListener l) {
        lm.requestSingleUpdate(provider, l, Looper.getMainLooper());
    }
}
