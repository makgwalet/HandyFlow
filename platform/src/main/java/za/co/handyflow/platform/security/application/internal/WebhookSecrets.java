package za.co.handyflow.platform.security.application.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Camera webhook secrets are stored as "sha256:" + hex, so a database leak does not hand out
 * working secrets. Values stored before this change (no prefix) still verify, compared as-is.
 * All comparisons are constant time.
 */
final class WebhookSecrets {

    static final String PREFIX = "sha256:";

    private WebhookSecrets() {}

    static String forStorage(String rawSecret) {
        return PREFIX + hex(sha256(rawSecret));
    }

    static boolean matches(String stored, String supplied) {
        if (stored == null || stored.isBlank() || supplied == null) return false;
        byte[] expected = stored.startsWith(PREFIX)
                ? stored.substring(PREFIX.length()).getBytes(StandardCharsets.UTF_8)
                : null;
        if (expected != null) {
            return MessageDigest.isEqual(expected, hex(sha256(supplied)).getBytes(StandardCharsets.UTF_8));
        }
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
