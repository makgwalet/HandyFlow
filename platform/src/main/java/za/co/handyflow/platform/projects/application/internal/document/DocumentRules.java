package za.co.handyflow.platform.projects.application.internal.document;

import java.util.Locale;
import java.util.Set;

/**
 * Small pure rules for the document register — no Spring, no database.
 *
 * NUMBERING: a document (a "series") has one document number; each upload under that number is a
 * new revision. The prefix per type must match the SQL backfill in V_projects_document_control.sql.
 *
 * FILES AND LINKS: a register entry is either a LINK (a URL or storage reference) or a STORED FILE.
 * For links only the NAME can be checked; for stored files the first bytes are checked too
 * (looksExecutable), and DocumentService runs the shared malware-scan hook before the file is kept.
 * Executable and script extensions are refused either way because the register is shared with
 * clients and contractors who will open these files. Names, links, content types and hashes are
 * handled here so every path (staff download, portal download) treats them the same way.
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

    // ── Links ────────────────────────────────────────────────────────────────

    /**
     * True if a document reference is safe to render as a link: http(s), or a relative reference /
     * storage key with no scheme. Everything else (javascript:, data:, vbscript:, file:, ftp:, a
     * Windows path such as C:\x) is refused.
     *
     * Browsers ignore tabs, newlines and other control characters inside a URL, so
     * "java\tscript:alert(1)" IS a javascript: URL. They are removed before the scheme is read; a
     * pattern on the raw text (as the request DTO has) cannot see that.
     */
    public static boolean isSafeUrl(String url) {
        if (url == null) return false;
        StringBuilder sb = new StringBuilder(url.length());
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c > 0x20 && c != 0x7f) sb.append(c);
        }
        String s = sb.toString();
        if (s.isEmpty()) return false;
        int colon = s.indexOf(':');
        if (colon < 0) return true;                       // relative reference or storage key
        int stop = s.length();
        for (char c : new char[]{'/', '?', '#'}) {
            int i = s.indexOf(c);
            if (i >= 0 && i < stop) stop = i;
        }
        if (stop < colon) return true;                    // the colon is inside a path or query, not a scheme
        String scheme = s.substring(0, colon).toLowerCase(Locale.ROOT);
        return scheme.equals("http") || scheme.equals("https");
    }

    // ── Stored files ─────────────────────────────────────────────────────────

    /**
     * A name that is safe to put in a Content-Disposition header and on disk: no directory part, no
     * control characters, quotes, separators or wildcards, no leading dots, at most 150 characters
     * (the extension is kept). Never empty.
     */
    public static String safeFileName(String name) {
        if (name == null) return "document";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String n = name.substring(slash + 1);
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            boolean bad = c < 0x20 || c == 0x7f || "\"\\/:*?<>|;".indexOf(c) >= 0;
            sb.append(bad ? '_' : c);
        }
        String r = sb.toString().trim();
        while (r.startsWith(".")) r = r.substring(1);
        if (r.length() > 150) {
            int dot = r.lastIndexOf('.');
            String ext = (dot > 0 && r.length() - dot <= 12) ? r.substring(dot) : "";
            r = r.substring(0, 150 - ext.length()) + ext;
        }
        return r.isEmpty() ? "document" : r;
    }

    /**
     * True if the first bytes of a file show a program rather than a document: Windows/DOS (MZ),
     * Linux (ELF), a shebang script, a Java class, or a Mach-O binary. The register is shared with
     * clients and contractors, so such a file is refused whatever its name says.
     */
    public static boolean looksExecutable(byte[] head) {
        if (head == null || head.length < 2) return false;
        int b0 = head[0] & 0xFF, b1 = head[1] & 0xFF;
        if (b0 == 'M' && b1 == 'Z') return true;
        if (b0 == '#' && b1 == '!') return true;
        if (head.length < 4) return false;
        int b2 = head[2] & 0xFF, b3 = head[3] & 0xFF;
        if (b0 == 0x7F && b1 == 'E' && b2 == 'L' && b3 == 'F') return true;
        if (b0 == 0xCA && b1 == 0xFE && b2 == 0xBA && b3 == 0xBE) return true;
        if (b0 == 0xFE && b1 == 0xED && b2 == 0xFA && (b3 == 0xCE || b3 == 0xCF)) return true;
        return (b0 == 0xCE || b0 == 0xCF) && b1 == 0xFA && b2 == 0xED && b3 == 0xFE;
    }

    /**
     * The Content-Type to send when a stored file is downloaded. Anything a browser could run or
     * render as a page (HTML, SVG, XML, JavaScript) is sent as an opaque download, and a missing or
     * malformed type becomes application/octet-stream. The caller also sends
     * Content-Disposition: attachment and X-Content-Type-Options: nosniff.
     */
    public static String downloadContentType(String stored) {
        final String opaque = "application/octet-stream";
        if (stored == null || stored.isBlank()) return opaque;
        String t = stored.trim().toLowerCase(Locale.ROOT);
        int semi = t.indexOf(';');
        if (semi >= 0) t = t.substring(0, semi).trim();
        if (!t.matches("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*")) return opaque;
        if (t.contains("html") || t.contains("svg") || t.contains("xml") || t.contains("javascript")
                || t.contains("ecmascript") || t.equals("text/css")) return opaque;
        return t;
    }

    /** Lower-case hex SHA-256 of the bytes. */
    public static String sha256(byte[] content) {
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