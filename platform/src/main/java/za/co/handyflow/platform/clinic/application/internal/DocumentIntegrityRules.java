package za.co.handyflow.platform.clinic.application.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;

/** Hashes and verification codes for issued documents (patch 0193, CLINIC-DEC-024/025). A hash shows a file is unchanged; it is not a signature. Pure apart from the random source. */
public final class DocumentIntegrityRules {

    /** No 0, O, 1, I or L, so a code read off paper is not mistaken. */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int CODE_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private DocumentIntegrityRules() {}

    public static String sha256(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String text) {
        return sha256(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }

    /** A new code printed as {@code ABCD-EFGH-JKMN-PQRS}. About 80 bits, so it cannot be guessed. */
    public static String newCode() {
        return newCode(RANDOM);
    }

    static String newCode(Random r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** What a person typed or scanned, reduced to the form that is hashed; null when it cannot be a code. */
    public static String normalise(String typed) {
        if (typed == null) return null;
        String s = typed.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (s.length() != CODE_LENGTH) return null;
        for (int i = 0; i < s.length(); i++) if (ALPHABET.indexOf(s.charAt(i)) < 0) return null;
        return s;
    }

    /** The value stored for a code: the hash of the normalised form, or null for something that is not a code. */
    public static String codeHash(String typed) {
        String n = normalise(typed);
        return n == null ? null : sha256(n);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
