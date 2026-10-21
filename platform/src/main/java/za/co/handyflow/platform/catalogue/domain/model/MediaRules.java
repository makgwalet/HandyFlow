package za.co.handyflow.platform.catalogue.domain.model;

import java.util.Map;
import java.util.Set;

/** Rules for pictures and documents on an item. No database or Spring here, so they are easy to test. */
public final class MediaRules {

    private MediaRules() {}

    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    public static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;
    public static final int MAX_IMAGES_PER_ITEM = 12;
    public static final int MAX_DOCUMENTS_PER_ITEM = 10;

    public static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final Set<String> DOCUMENT_TYPES = Set.of("application/pdf");

    public enum Kind { IMAGE, DOCUMENT }

    /** The kind a content type belongs to, or an error naming what is allowed. */
    public static Kind kindOf(String contentType) {
        String t = contentType == null ? "" : contentType.toLowerCase().trim();
        if (IMAGE_TYPES.contains(t)) return Kind.IMAGE;
        if (DOCUMENT_TYPES.contains(t)) return Kind.DOCUMENT;
        throw new IllegalArgumentException("Unsupported file type. Use a JPEG, PNG or WebP picture, or a PDF document.");
    }

    public static long maxBytes(Kind kind) { return kind == Kind.IMAGE ? MAX_IMAGE_BYTES : MAX_DOCUMENT_BYTES; }

    public static int maxCount(Kind kind) { return kind == Kind.IMAGE ? MAX_IMAGES_PER_ITEM : MAX_DOCUMENTS_PER_ITEM; }

    /** The file's first bytes must match what it says it is, so a renamed program cannot pass as a picture. */
    public static boolean signatureMatches(String contentType, byte[] h, int len) {
        if (h == null || len < 4) return false;
        return switch (contentType.toLowerCase().trim()) {
            case "application/pdf" -> h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
            case "image/jpeg" -> (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "image/png" -> (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case "image/webp" -> len >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            default -> false;
        };
    }

    /** A safe name to show and to put in a download header. It is never used to build a storage path. */
    public static String safeFileName(String original) {
        if (original == null || original.isBlank()) return "file";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.length() > 200) name = name.substring(name.length() - 200);
        return name.isEmpty() ? "file" : name;
    }

    public static String text(String s, int max, String what) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.length() > max) throw new IllegalArgumentException(what + " can be at most " + max + " characters.");
        return t;
    }

    /** Main picture when none is set yet and the file is a picture. */
    public static boolean becomesPrimary(Kind kind, boolean itemHasPrimary) { return kind == Kind.IMAGE && !itemHasPrimary; }

    public static final Map<Kind, String> LABEL = Map.of(Kind.IMAGE, "pictures", Kind.DOCUMENT, "documents");
}
