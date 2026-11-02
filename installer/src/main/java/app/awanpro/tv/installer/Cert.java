package app.awanpro.tv.installer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A minimal self-signed X.509 certificate for the phone's ADB key, written in DER by hand so the installer needs no
 * certificate library. Wireless debugging only checks that the certificate's public key is the one the TV saved
 * when pairing; the other fields are just valid filler.
 */
final class Cert {
    private Cert() {}

    // 1.2.840.113549.1.1.11, sha256WithRSAEncryption
    private static final byte[] SHA256_RSA = {0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x0b};
    // 2.5.4.3, commonName
    private static final byte[] CN = {0x55, 0x04, 0x03};

    static Certificate selfSigned(KeyPair keys, String commonName) throws Exception {
        byte[] algorithm = seq(tlv(0x06, SHA256_RSA), tlv(0x05, new byte[0]));
        byte[] name = seq(tlv(0x31, seq(tlv(0x06, CN), tlv(0x0c, commonName.getBytes(StandardCharsets.UTF_8)))));
        Date from = new Date(System.currentTimeMillis() - 86_400_000L);
        Date to = new Date(from.getTime() + 30L * 365 * 86_400_000L);
        byte[] tbs = seq(
                tlv(0xa0, tlv(0x02, new byte[]{2})),                       // version 3
                tlv(0x02, BigInteger.valueOf(System.currentTimeMillis()).toByteArray()),
                algorithm,
                name,                                                      // issuer
                seq(time(from), time(to)),
                name,                                                      // subject
                keys.getPublic().getEncoded());                            // SubjectPublicKeyInfo
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(keys.getPrivate());
        s.update(tbs);
        byte[] signature = s.sign();
        byte[] bits = new byte[signature.length + 1];                      // no unused bits
        System.arraycopy(signature, 0, bits, 1, signature.length);
        byte[] der = seq(tbs, algorithm, tlv(0x03, bits));
        return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    /** GeneralizedTime, which is valid for any year (UTCTime stops at 2049). */
    private static byte[] time(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(0x18, f.format(d).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] seq(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return tlv(0x30, out.toByteArray());
    }

    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int n = value.length;
        if (n < 0x80) {
            out.write(n);
        } else {
            int bytes = n > 0xffffff ? 4 : n > 0xffff ? 3 : n > 0xff ? 2 : 1;
            out.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) out.write(n >>> (8 * i));
        }
        out.write(value, 0, n);
        return out.toByteArray();
    }
}
