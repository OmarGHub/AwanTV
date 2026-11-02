package app.awanpro.tv.installer;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

/**
 * The phone's side of ADB: one RSA key kept in the app's files (so the TV remembers this phone after "Always allow"
 * or pairing), shell commands with their exit code, and streaming an APK into {@code cmd package install}.
 */
final class Adb extends AbsAdbConnectionManager {
    /** Progress while the APK goes to the TV, from 0 to 1. */
    interface Progress { void on(float done); }

    private final PrivateKey key;
    private final Certificate cert;

    Adb(Context c) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        // Long enough to find the remote and answer "Allow debugging?" on the TV.
        setTimeout(90, TimeUnit.SECONDS);
        File keyFile = new File(c.getFilesDir(), "adb.key");
        File certFile = new File(c.getFilesDir(), "adb.crt");
        if (keyFile.exists() && certFile.exists()) {
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(keyFile.toPath())));
            try (InputStream in = new FileInputStream(certFile)) {
                cert = CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
        } else {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair pair = g.generateKeyPair();
            key = pair.getPrivate();
            cert = Cert.selfSigned(pair, "Awan TV Installer");
            Files.write(keyFile.toPath(), key.getEncoded());
            Files.write(certFile.toPath(), cert.getEncoded());
        }
    }

    @Override protected PrivateKey getPrivateKey() { return key; }
    @Override protected Certificate getCertificate() { return cert; }
    /** What the TV shows in "Allow debugging?" and its paired devices list. */
    @Override protected String getDeviceName() { return "Awan TV Installer (" + Build.MODEL + ")"; }

    /** Result of one shell command. */
    static final class Result {
        final int code;
        final String out;
        Result(int code, String out) { this.code = code; this.out = out; }
        boolean ok() { return code == 0; }
    }

    private static final String RC = "AWAN_RC=";

    /** Runs a command in the TV's shell and returns its output and exit code. */
    Result shell(String command) throws IOException, InterruptedException {
        String all = read(openStream("shell:" + command + "; echo " + RC + "$?"));
        int at = all.lastIndexOf(RC);
        if (at < 0) return new Result(-1, all.trim());
        int code;
        try {
            code = Integer.parseInt(all.substring(at + RC.length()).trim());
        } catch (NumberFormatException e) {
            code = -1;
        }
        return new Result(code, all.substring(0, at).trim());
    }

    /**
     * Streams the APK into {@code cmd package install -r -S <size>} and returns what the package manager said:
     * "Success", or "Failure [INSTALL_FAILED_…]".
     */
    String install(File apk, Progress progress) throws IOException, InterruptedException {
        long size = apk.length();
        AdbStream stream = openStream("exec:cmd package install -r -S " + size);
        // Not closed: closing the output side closes the whole stream before the answer arrives.
        OutputStream out = stream.openOutputStream();
        // One ADB packet per write: the library splits a bigger write into packets without waiting for the TV's
        // OKAY between them, which adbd may answer by closing the stream.
        int packet;
        try {
            packet = getAdbConnection().getMaxData();
        } catch (Exception e) {
            packet = 4096;
        }
        byte[] buf = new byte[Math.max(1024, Math.min(packet, 256 * 1024))];
        long sent = 0;
        try (InputStream in = new FileInputStream(apk)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                sent += n;
                progress.on((float) sent / size);
            }
        }
        out.flush();
        return read(stream).trim();
    }

    /** A command's raw output (exec: has no terminal, so binary comes through untouched): icons, for one. */
    byte[] bytes(String command) throws IOException, InterruptedException {
        return readBytes(openStream("exec:" + command));
    }

    /** A long-running command whose output is read as it comes; close the stream to stop it. */
    AdbStream stream(String command) throws IOException, InterruptedException {
        return openStream("shell:" + command);
    }

    /** Everything the stream sends until the TV closes it. */
    private static String read(AdbStream stream) throws IOException {
        return new String(readBytes(stream), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(AdbStream stream) throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        try (InputStream in = stream.openInputStream()) {
            int n;
            // Once the peer has closed, nothing more arrives: stop when what came is read (see peerClosed).
            while (!(peerClosed(stream) && in.available() == 0) && (n = in.read(buf)) >= 0) all.write(buf, 0, n);
        } catch (IOException closed) {
            // The library reports a peer-closed stream as "Stream closed." rather than -1; what came before stands.
        } finally {
            if (!stream.isClosed()) stream.close();
        }
        return all.toByteArray();
    }

    private static final Field PENDING_CLOSE = pendingCloseField();

    private static Field pendingCloseField() {
        try {
            Field f = AdbStream.class.getDeclaredField("mPendingClose");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * Whether the TV has closed the stream. libadb 3.1.1 only marks that "pending" when data is still queued, and
     * its read() then waits for a close that never comes once the queue is empty: a quick command (getprop) whose
     * output and close arrive together hung the installer for good. So the reader checks the mark itself.
     */
    private static boolean peerClosed(AdbStream stream) {
        try {
            return PENDING_CLOSE != null && PENDING_CLOSE.getBoolean(stream);
        } catch (IllegalAccessException e) {
            return false;
        }
    }
}
