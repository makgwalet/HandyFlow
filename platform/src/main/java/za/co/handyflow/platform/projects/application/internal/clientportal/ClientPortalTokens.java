package za.co.handyflow.platform.projects.application.internal.clientportal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Invite tokens: 256 random bits as lowercase hex. Only the SHA-256 of a token is stored. */
final class ClientPortalTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{64}$");

    private ClientPortalTokens() {}

    static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    static boolean wellFormed(String token) { return token != null && FORMAT.matcher(token).matches(); }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** How client names are compared: trimmed, any case. */
    static String normalise(String name) { return name == null ? null : name.trim().toLowerCase(Locale.ROOT); }
}
