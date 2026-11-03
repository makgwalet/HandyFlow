package za.co.handyflow.platform.projects.application.internal.document;

import java.util.Locale;
import java.util.Set;

/**
 * Small pure rules for the document register — no Spring, no database.
 *
 * NUMBERING: a document (a "series") has one document number; each upload under that number is a
 * new revision. The prefix per type must match the SQL backfill in V_projects_document_control.sql.
 *
 * FILE TYPES: documents are stored as references (a URL or storage key), not bytes, so the only
 * thing checkable here is the NAME. Executable and script extensions are refused because the
 * register is shared with clients and contractors who will open these files. This is a floor, not
 * malware scanning — scanning belongs in the storage / evidence layer.
 */
public final class DocumentRules {

    private DocumentRules() {}

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "msi", "scr", "sh", "js", "jse", "vbs", "vbe", "wsf",
            "ps1", "jar", "dll", "apk", "app", "bin", "cpl", "hta", "lnk", "reg");

    public static final int MAX_NUMBER_LENGTH = 50;

    public static String prefixFor(String documentType) {
        if (documentType == null) return "GEN";
        return switch (documentType.toUpperCase(Locale.ROOT)) {
            case "DRAWING"   -> "DWG";
            case "RFI"       -> "RFI";
            case "SUBMITTAL" -> "SUB";
            case "CONTRACT"  -> "CTR";
            case "REPORT"    -> "RPT";
            case "PHOTO"     -> "PHO";
            default          -> "GEN";
        };
    }

    /** "DWG-A-101" → as given (trimmed, inner whitespace collapsed); blank → null; too long → IllegalArgumentException. */
    public static String normalizeNumber(String raw) {
        if (raw == null) return null;
        String n = raw.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) return null;
        if (n.length() > MAX_NUMBER_LENGTH) {
            throw new IllegalArgumentException("Document number must not exceed " + MAX_NUMBER_LENGTH + " characters");
        }
        return n;
    }

    /** Formats an auto-assigned number: prefix + zero-padded sequence, e.g. DWG-007. */
    public static String autoNumber(String documentType, int sequence) {
        return prefixFor(documentType) + "-" + String.format("%03d", sequence);
    }

    /**
     * True if a file NAME — or the last path segment of a URL — ends in a blocked extension.
     * Query strings and fragments are ignored; only the LAST extension counts, so
     * "report.pdf.exe" is blocked and "archive.exe.pdf" is not.
     */
    public static boolean hasBlockedExtension(String nameOrUrl) {
        if (nameOrUrl == null || nameOrUrl.isBlank()) return false;
        String s = nameOrUrl.trim();
        int cut = s.length();
        for (char c : new char[]{'?', '#'}) {
            int i = s.indexOf(c);
            if (i >= 0 && i < cut) cut = i;
        }
        s = s.substring(0, cut);
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        String last = s.substring(slash + 1);
        int dot = last.lastIndexOf('.');
        if (dot < 0 || dot == last.length() - 1) return false;
        return BLOCKED_EXTENSIONS.contains(last.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}