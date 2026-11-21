package za.co.handyflow.platform.security.application.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** HMAC for checkpoint QR payloads. Compares in constant time. */
final class QrSignature {

    private QrSignature() {}

    static String sign(String secret, String payload) throws java.security.GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    /** False for a missing secret or signature as well as a wrong one. */
    static boolean matches(String secret, String payload, String signature) {
        if (secret == null || secret.isBlank() || signature == null) return false;
        try {
            return MessageDigest.isEqual(
                    sign(secret, payload).getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            return false;
        }
    }
}
