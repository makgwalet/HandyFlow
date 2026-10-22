package za.co.handyflow.platform.hr.domain.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Files attached to an employee relations case: statements, emails, photographs, recordings. Pure. Which files are accepted, how a file is
 * checked against what its name says it is, and the words. The file type stored and served is decided here from the extension, never taken
 * from the browser, so a page or script cannot be uploaded as evidence and served back.
 */
public final class ErEvidenceRules {

    /** Restricted source module: only the employee relations screens can list or open these files (see EvidenceFacade). */
    public static final String SOURCE = "restricted-hr-er";
    public static final String ENTITY = "ER_CASE";

    public static final long MAX_BYTES = 15L * 1024 * 1024;
    public static final int MAX_PER_CASE = 50;
    public static final int MAX_NAME = 120;
    /** How many leading bytes of the file the check needs. */
    public static final int HEAD_BYTES = 512;

    public static final List<String> KINDS = List.of("STATEMENT", "DOCUMENT", "EMAIL", "PHOTO", "RECORDING", "OTHER");

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("txt", "text/plain"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("wav", "audio/wav"));

    private ErEvidenceRules() {}

    public static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** The type the file is stored and served as, or null when the extension is not accepted. */
    public static String contentType(String fileName) { return TYPES.get(extension(fileName)); }

    public static String acceptedList() { return "PDF, PNG, JPG, DOCX, XLSX, TXT, MP3, M4A or WAV"; }

    /** The name without any folder part or control characters, cut to a sensible length (keeping the extension). */
    public static String safeName(String fileName) {
        String n = fileName == null ? "" : fileName.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>:|?*]", "").trim();
        if (n.length() <= MAX_NAME) return n;
        String ext = extension(n);
        String tail = ext.isEmpty() ? "" : "." + ext;
        return n.substring(0, MAX_NAME - tail.length()) + tail;
    }

    /**
     * Null when the file may be added. {@code head} is the first bytes of the file; {@code active} is how many files the case already holds.
     */
    public static String uploadProblem(String caseStatus, String kind, String fileName, long size, byte[] head, int active) {
        if (!ErRules.isOpen(caseStatus)) return "This case is closed. Files cannot be added to it.";
        if (kind == null || !KINDS.contains(kind)) return "Say what kind of evidence this is.";
        String name = safeName(fileName);
        if (name.isEmpty()) return "The file has no name.";
        if (size <= 0) return "The file is empty.";
        if (size > MAX_BYTES) return "The file is too large (" + (MAX_BYTES / (1024 * 1024)) + " MB at most).";
        if (contentType(name) == null) return "That type of file is not accepted. Use " + acceptedList() + ".";
        if (!looksLike(extension(name), head)) return "The file does not look like a ." + extension(name) + " file.";
        if (active >= MAX_PER_CASE) return "A case can hold at most " + MAX_PER_CASE + " files.";
        return null;
    }

    public static String removeProblem(String caseStatus, String reason) {
        if (!ErRules.isOpen(caseStatus)) return "This case is closed. Files cannot be removed from it.";
        if (reason == null || reason.isBlank()) return "Give the reason for removing the file.";
        if (reason.trim().length() > 300) return "The reason is too long (300 characters at most).";
        return null;
    }

    /** A cheap check that the leading bytes fit the extension. Text must contain no NUL bytes. */
    public static boolean looksLike(String ext, byte[] head) {
        if (head == null || head.length == 0) return false;
        return switch (ext) {
            case "pdf" -> startsWith(head, 0, '%', 'P', 'D', 'F', '-');
            case "png" -> startsWith(head, 0, 0x89, 'P', 'N', 'G');
            case "jpg", "jpeg" -> startsWith(head, 0, 0xFF, 0xD8, 0xFF);
            case "docx", "xlsx" -> startsWith(head, 0, 'P', 'K', 0x03, 0x04);
            case "wav" -> startsWith(head, 0, 'R', 'I', 'F', 'F');
            case "m4a" -> startsWith(head, 4, 'f', 't', 'y', 'p');
            case "mp3" -> startsWith(head, 0, 'I', 'D', '3') || (head.length > 1 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0);
            case "txt" -> { for (byte b : head) if (b == 0) yield false; yield true; }
            default -> false;
        };
    }

    private static boolean startsWith(byte[] b, int at, int... expected) {
        if (b.length < at + expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((b[at + i] & 0xFF) != expected[i]) return false;
        return true;
    }

    public static String label(String kind) {
        return switch (kind == null ? "" : kind) {
            case "STATEMENT" -> "Statement";
            case "DOCUMENT" -> "Document";
            case "EMAIL" -> "Email";
            case "PHOTO" -> "Photograph";
            case "RECORDING" -> "Recording";
            case "OTHER" -> "Other";
            default -> String.valueOf(kind);
        };
    }

    /** The timeline already says "Evidence added", so the note carries only the kind and the file name. */
    public static String addedNote(String kind, String fileName) { return label(kind) + " – " + safeName(fileName); }

    public static String removedNote(String fileName, String reason) { return safeName(fileName) + " – " + reason.trim(); }
}
