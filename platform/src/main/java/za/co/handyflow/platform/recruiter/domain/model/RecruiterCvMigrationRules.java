package za.co.handyflow.platform.recruiter.domain.model;

import java.util.Base64;

/**
 * CVs saved before file storage existed hold the PDF itself, as base64 text, in the applicant's record. A reference to a stored file always
 * contains "://", which base64 text never does, so the two can be told apart without asking the storage provider.
 */
public final class RecruiterCvMigrationRules {
    private RecruiterCvMigrationRules() {}

    public static final int DEFAULT_BATCH = 25, MAX_BATCH = 100;

    /** True when the value is the old kind: something there, and not a reference to a stored file. */
    public static boolean isLegacy(String stored) {
        return stored != null && !stored.isBlank() && !stored.contains("://");
    }

    /** The PDF bytes inside an old-style value (a "data:" prefix is tolerated), or null when it is not readable base64 or not a usable PDF. */
    public static byte[] legacyBytes(String stored) {
        if (!isLegacy(stored)) return null;
        String raw = stored.trim();
        int comma = raw.indexOf(',');
        String base64 = raw.startsWith("data:") && comma >= 0 ? raw.substring(comma + 1) : raw;
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return RecruiterSecurityRules.cvProblem(bytes, null) == null ? bytes : null;
    }

    /** A file name safe to hand to storage: the stored one when it is acceptable, otherwise a plain default. */
    public static String fileName(String stored) {
        return stored != null && RecruiterSecurityRules.cvProblem(new byte[]{'%', 'P', 'D', 'F', '-'}, stored) == null ? stored.trim() : "cv.pdf";
    }

    public static int batchOrDefault(Integer n) {
        if (n == null) return DEFAULT_BATCH;
        return Math.max(1, Math.min(MAX_BATCH, n));
    }
}
