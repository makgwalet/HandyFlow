package za.co.handyflow.platform.shared.secureaccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pure rules for per-recipient access to shared documents — no Spring, no database, so the
 * security-relevant parts can be tested on their own.
 *
 * REUSE: this class lives in the shared package so Projects, Contracting, Facilities Management and any
 * other module issue and check links the same way (same token strength, same code limits, same expiry
 * bounds). A module plugs in by keeping its own recipient and access-log tables and calling these
 * rules; see PROJECTS-MODULE-BACKLOG.md ("Reusing the access engine").
 *
 * TOKEN: 256 bits from SecureRandom, URL-safe base64 (43 characters). Only its SHA-256 is stored, so a
 * copy of the database does not give anyone a working link. A fast hash is right here because the
 * token is already unguessable (it is not a password).
 *
 * ONE-TIME CODE: six digits sent by email. Stored only as a hash tied to the recipient, valid for a
 * short time, and locked after a few wrong guesses, because six digits alone could be guessed.
 *
 * SESSION: after a correct code the recipient gets a second 256-bit secret, valid for a short time.
 * Holding the link alone (for example a forwarded email) is therefore never enough to open a document.
 */
public final class RecipientAccessRules {

    private RecipientAccessRules() {}

    public static final int DEFAULT_EXPIRY_DAYS = 14;
    public static final int MIN_EXPIRY_DAYS     = 1;
    public static final int MAX_EXPIRY_DAYS     = 90;

    /** A registered collaborator's standing link lasts longer than a one-off transmittal link. */
    public static final int DEFAULT_ACCOUNT_DAYS = 365;
    public static final int MAX_ACCOUNT_DAYS     = 730;

    public static final int OTP_VALID_MINUTES   = 10;
    public static final int OTP_MAX_ATTEMPTS    = 5;
    /** A new code cannot be requested more often than this. */
    public static final int OTP_RESEND_SECONDS  = 60;
    public static final int SESSION_MINUTES     = 30;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SECRET_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Pattern OTP_FORMAT    = Pattern.compile("^[0-9]{6}$");

    // ── Secrets ──────────────────────────────────────────────────────────────

    /** A new link token or session secret. */
    public static String newSecret() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Checked BEFORE any database lookup so malformed input never reaches the database. */
    public static boolean secretFormatOk(String s) {
        return s != null && SECRET_FORMAT.matcher(s).matches();
    }

    public static String hashSecret(String secret) {
        return sha256Hex(secret);
    }

    // ── One-time code ────────────────────────────────────────────────────────

    public static String newOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public static boolean otpFormatOk(String code) {
        return code != null && OTP_FORMAT.matcher(code.trim()).matches();
    }

    /** Tied to the recipient, so the same six digits hash differently for every recipient. */
    public static String hashOtp(UUID recipientId, String code) {
        return sha256Hex(recipientId + ":" + code.trim());
    }

    /** Constant-time comparison of two hex hashes. */
    public static boolean hashesEqual(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean otpLocked(int attempts) {
        return attempts >= OTP_MAX_ATTEMPTS;
    }

    /** False while the previous code is still too recent to send another. */
    public static boolean mayResendOtp(Instant lastSent, Instant now) {
        return lastSent == null || !lastSent.plusSeconds(OTP_RESEND_SECONDS).isAfter(now);
    }

    // ── Validity ─────────────────────────────────────────────────────────────

    public static int clampExpiryDays(Integer requested) {
        if (requested == null) return DEFAULT_EXPIRY_DAYS;
        return Math.max(MIN_EXPIRY_DAYS, Math.min(MAX_EXPIRY_DAYS, requested));
    }

    public static int clampAccountDays(Integer requested) {
        if (requested == null) return DEFAULT_ACCOUNT_DAYS;
        return Math.max(MIN_EXPIRY_DAYS, Math.min(MAX_ACCOUNT_DAYS, requested));
    }

    /** A link works only before it expires and while it has not been revoked. */
    public static boolean linkUsable(Instant expiresAt, Instant revokedAt, Instant now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }

    public static boolean sessionUsable(Instant sessionExpiresAt, Instant now) {
        return sessionExpiresAt != null && sessionExpiresAt.isAfter(now);
    }

    // ── Display ──────────────────────────────────────────────────────────────

    /** "jane.doe@example.com" → "j*******@example.com". Shown to the recipient, never the full address. */
    public static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "*".repeat(Math.max(at - 1, 1)) + email.substring(at);
    }

    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);   // required on every JVM
        }
    }

    /** Lower-case hex SHA-256 of the bytes (used to re-check a released file against the hash frozen at issue). */
    public static String sha256Hex(byte[] content) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);   // required on every JVM
        }
    }
}
