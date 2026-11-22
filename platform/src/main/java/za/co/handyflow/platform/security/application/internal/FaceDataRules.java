package za.co.handyflow.platform.security.application.internal;

import java.util.Base64;

/** Rules for a guard's face template: consent, shape, and when it must be deleted. Pure logic. */
final class FaceDataRules {

    enum Decision { STORE, SKIP_NO_CONSENT, SKIP_NONE, REJECT_INVALID }

    /** A template is a vector of 32-bit floats: 32 to 2048 of them (128 bytes to 8 KB). */
    static final int MIN_BYTES = 128;
    static final int MAX_BYTES = 8192;

    private FaceDataRules() {}

    static Decision decide(String embeddingBase64, Boolean consent) {
        if (embeddingBase64 == null || embeddingBase64.isBlank()) return Decision.SKIP_NONE;
        if (!Boolean.TRUE.equals(consent)) return Decision.SKIP_NO_CONSENT;
        return validShape(embeddingBase64) ? Decision.STORE : Decision.REJECT_INVALID;
    }

    static boolean validShape(String b64) {
        try {
            byte[] raw = Base64.getDecoder().decode(b64.trim());
            return raw.length >= MIN_BYTES && raw.length <= MAX_BYTES && raw.length % 4 == 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** A guard who has left (terminated or removed) must not keep a face template. */
    static boolean mustWipe(String status, boolean deleted) {
        return deleted || "TERMINATED".equals(status);
    }
}
