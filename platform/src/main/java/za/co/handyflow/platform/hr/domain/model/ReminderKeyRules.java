package za.co.handyflow.platform.hr.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * What makes two reminders "the same message to the same person". The key is a hash, so the table that remembers it holds no names, emails
 * or text. Pure: no database, no clock.
 */
public final class ReminderKeyRules {

    private ReminderKeyRules() {}

    /** How a person is told apart: their user, else their email address (any case), else their name. Null when nothing identifies them. */
    public static String person(UUID userId, String email, String name) {
        if (userId != null) return "u:" + userId;
        if (email != null && !email.isBlank()) return "e:" + email.trim().toLowerCase(java.util.Locale.ROOT);
        if (name != null && !name.isBlank()) return "n:" + name.trim().toLowerCase(java.util.Locale.ROOT);
        return null;
    }

    /** 64 hex characters. Anything that differs (kind, subject, person, title, text) gives a different key. */
    public static String key(String type, String subject, String person, String title, String message) {
        String joined = String.join("\u001f", nz(type), nz(subject), nz(person), nz(title), nz(message));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
