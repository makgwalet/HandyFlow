package za.co.handyflow.platform.legalpractice.application.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** What a matter document may be: its name, type, size and category, and the hash that proves a file is what was received. No database. */
public final class DocRules {

    private DocRules() { }

    public static final long MAX_BYTES = 20L * 1024 * 1024;   // the evidence store's own limit
    public static final int MIN_REASON = 10;
    public static final Set<String> CATEGORIES = Set.of("CORRESPONDENCE", "PLEADING", "COURT_ORDER", "CONTRACT", "ADVICE", "EVIDENCE", "FILE_NOTE", "OTHER");

    /** Allowed extensions and the type each is served as. Anything else (scripts, programs, archives) is refused. */
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("eml", "message/rfc822"),
            Map.entry("msg", "application/vnd.ms-outlook"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"));

    public static String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String contentType(String name) { return TYPES.getOrDefault(extension(name), "application/octet-stream"); }

    /** The file name with any folder part, control characters and quotes removed, kept to 200 characters, never empty. */
    public static String safeName(String raw) {
        String s = raw == null ? "" : raw.replace('\\', '/');
        s = s.substring(s.lastIndexOf('/') + 1);
        s = s.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
        if (s.length() > 200) {
            String ext = extension(s);
            s = s.substring(0, 200 - (ext.isEmpty() ? 0 : ext.length() + 1)) + (ext.isEmpty() ? "" : "." + ext);
        }
        return s.isEmpty() ? "document" : s;
    }

    /** Why this file cannot be accepted, or null. {@code head} is the first few bytes of the file. */
    public static String fileProblem(String name, long size, byte[] head) {
        if (size <= 0) return "The file is empty";
        if (size > MAX_BYTES) return "The file is over " + (MAX_BYTES / (1024 * 1024)) + " MB";
        String ext = extension(name);
        if (!TYPES.containsKey(ext)) return "Files of this kind are not accepted. Use PDF, Word, Excel, PowerPoint, text, e-mail or image files";
        if (!matchesType(ext, head == null ? new byte[0] : head)) return "The file does not look like a ." + ext + " file";
        return null;
    }

    private static boolean matchesType(String ext, byte[] h) {
        return switch (ext) {
            case "pdf" -> starts(h, "%PDF");
            case "docx", "xlsx", "pptx" -> starts(h, "PK");
            case "doc", "xls", "ppt", "msg" -> h.length >= 4 && (h[0] & 0xFF) == 0xD0 && (h[1] & 0xFF) == 0xCF && (h[2] & 0xFF) == 0x11 && (h[3] & 0xFF) == 0xE0;
            case "rtf" -> starts(h, "{\\rtf");
            case "png" -> h.length >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case "jpg", "jpeg" -> h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "txt", "csv", "eml" -> noNulls(h);
            default -> false;
        };
    }

    private static boolean starts(byte[] h, String prefix) {
        byte[] p = prefix.getBytes(StandardCharsets.ISO_8859_1);
        if (h.length < p.length) return false;
        for (int i = 0; i < p.length; i++) if (h[i] != p[i]) return false;
        return true;
    }

    private static boolean noNulls(byte[] h) { for (byte b : h) if (b == 0) return false; return true; }

    public static String titleProblem(String title) {
        return title == null || title.trim().length() < 2 ? "Give the document a title" : title.trim().length() > 255 ? "The title is too long" : null;
    }

    public static String categoryProblem(String category) { return category == null || !CATEGORIES.contains(category) ? "Choose what kind of document this is" : null; }

    public static String reasonProblem(String reason) {
        return reason == null || reason.trim().length() < MIN_REASON ? "Give a reason of at least " + MIN_REASON + " characters" : null;
    }

    public static String sha256Hex(byte[] content) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
