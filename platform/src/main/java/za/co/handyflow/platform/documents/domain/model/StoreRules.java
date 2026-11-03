package za.co.handyflow.platform.documents.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The rules of the document store, kept free of Spring and JPA so they can be tested on their own: the size ceiling, the
 * hashes, the associated data that ties a sealed file to its registry row, and how one stored file is judged when it is
 * checked.
 */
public final class StoreRules {
    private StoreRules() {}

    /** Status of a registry row after its last check. */
    public enum Status { UNVERIFIED, OK, MISSING, UNREADABLE, MISMATCH }

    public record Verdict(Status status, String detail) {
        public boolean ok() { return status == Status.OK; }
        static Verdict of(Status s, String d) { return new Verdict(s, d); }
    }

    /** Hard ceiling for one stored file, whatever the caller allows. Callers keep their own lower limits. */
    public static final long DEFAULT_MAX_BYTES = 20L * 1024 * 1024;

    public static void checkSize(byte[] content, long maxBytes) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("The file is empty.");
        if (content.length > maxBytes)
            throw new IllegalArgumentException("The file is larger than " + (maxBytes / (1024 * 1024)) + " MB, the most the document store accepts.");
    }

    public static String sha256(byte[] c) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** Associated data for a sealed file: the tenant and the registry row. A sealed file moved to another row or tenant will not open. */
    public static byte[] aad(UUID tenantId, UUID objectId) {
        return ("hf-doc-store|" + tenantId + "|" + objectId).getBytes(StandardCharsets.UTF_8);
    }

    /** Owning-module code as stored: upper case, letters, digits and underscore, at most 60. */
    public static String module(String m) {
        String v = m == null ? "" : m.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        if (v.isEmpty()) throw new IllegalArgumentException("The owning module is required.");
        return v.length() > 60 ? v.substring(0, 60) : v;
    }

    public static String clip(String s, int max) {
        if (s == null) return null;
        String v = s.replaceAll("\\p{Cntrl}", " ").trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    /**
     * Judges one stored file. {@code stored} is what the storage provider returned; {@code storedSha256} the hash of those
     * exact bytes taken when they were written; {@code open} decrypts (only called when {@code encrypted}); {@code sha256}
     * the hash of the original file. Any failure to open counts as a mismatch: the bytes are no longer what was written.
     */
    public static Verdict judge(byte[] stored, String storedSha256, boolean encrypted, UnaryOperator<byte[]> open, String sha256) {
        if (stored == null) return Verdict.of(Status.MISSING, "The storage provider returned nothing");
        if (storedSha256 != null && !storedSha256.equalsIgnoreCase(sha256(stored)))
            return Verdict.of(Status.MISMATCH, "The stored bytes no longer match the hash taken when they were written");
        byte[] plain = stored;
        if (encrypted) {
            try { plain = open.apply(stored); }
            catch (RuntimeException e) { return Verdict.of(Status.MISMATCH, "The encrypted file failed its integrity check"); }
        }
        if (sha256 != null && !sha256.equalsIgnoreCase(sha256(plain)))
            return Verdict.of(Status.MISMATCH, "The file no longer matches the hash taken at upload");
        return Verdict.of(Status.OK, null);
    }

    /** A read failure is MISSING when the provider says the key is not there, otherwise UNREADABLE (a transient or provider fault). */
    public static Status readFailure(Throwable e) {
        String n = e == null ? "" : e.getClass().getSimpleName();
        String m = e == null || e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return n.equals("NoSuchFileException") || n.equals("FileNotFoundException") || m.startsWith("no file found") ? Status.MISSING : Status.UNREADABLE;
    }
}
