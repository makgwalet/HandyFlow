package za.co.handyflow.platform.debtcollection.application.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Webhook signing, shared by the receiver and by whoever calls it. Pure.
 * <p>
 * SIGNATURE SPEC (what a sender must implement):
 * <pre>
 *   X-HandyFlow-Timestamp: &lt;unix epoch seconds&gt;
 *   X-HandyFlow-Signature: v1=&lt;lowercase hex of HMAC-SHA256(secret, timestamp + "." + rawRequestBody)&gt;
 * </pre>
 * The body is signed byte-for-byte as sent. Requests whose timestamp is outside the tolerance window are rejected
 * (replay protection); the comparison is constant-time. The per-endpoint secret is HMAC-SHA256(masterKey,
 * "wh:" + endpointId + ":" + secretVersion), hex — derived, never stored.
 */
public final class WebhookSigner {

    private static final String ALGO = "HmacSHA256";

    private WebhookSigner() {}

    public static String deriveSecret(String masterKey, UUID endpointId, int secretVersion) {
        return hmacHex(masterKey.getBytes(StandardCharsets.UTF_8), ("wh:" + endpointId + ":" + secretVersion).getBytes(StandardCharsets.UTF_8));
    }

    public static String sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            return "v1=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    public static boolean verify(String secret, String timestamp, byte[] body, String signatureHeader,
                                 long nowEpochSeconds, long toleranceSeconds) {
        if (secret == null || timestamp == null || signatureHeader == null || body == null) return false;
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(nowEpochSeconds - ts) > toleranceSeconds) return false;
        byte[] expected = sign(secret, timestamp.trim(), body).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signatureHeader.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hmacHex(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(key, ALGO));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
