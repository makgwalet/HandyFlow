package za.co.handyflow.platform.documents.domain.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * What may be stored, and the checks around it. The type of a file is decided by what its first bytes say, not by what the upload claims,
 * and the name is cleaned. Documents about to expire are staged for alerts: due in 30 days, due in 7 days, expired.
 */
public final class DocumentRules {
    private DocumentRules() {}

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final String PHOTO = "PHOTO";

    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static final Set<String> IMAGES = Set.of(JPEG, PNG, WEBP);
    public static final Set<String> GENERAL = Set.of(PDF, JPEG, PNG, WEBP, DOCX, XLSX);

    public record Checked(String fileName, String contentType, String sha256) {}

    public static Set<String> allowedFor(String category) { return PHOTO.equals(category) ? IMAGES : GENERAL; }

    /** The type the first bytes show, or null when it is not a type we recognise. Office files are zip containers: the extension tells which. */
    public static String sniff(byte[] c, String fileName) {
        if (c == null || c.length < 12) return null;
        if (c[0] == '%' && c[1] == 'P' && c[2] == 'D' && c[3] == 'F' && c[4] == '-') return PDF;
        if ((c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF) return JPEG;
        if ((c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G') return PNG;
        if (c[0] == 'R' && c[1] == 'I' && c[2] == 'F' && c[3] == 'F' && c[8] == 'W' && c[9] == 'E' && c[10] == 'B' && c[11] == 'P') return WEBP;
        if (c[0] == 'P' && c[1] == 'K' && c[2] == 3 && c[3] == 4) {
            String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
            if (n.endsWith(".docx")) return DOCX;
            if (n.endsWith(".xlsx")) return XLSX;
        }
        return null;
    }

    public static String cleanName(String fileName) {
        String n = fileName == null ? "" : fileName.trim();
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        n = n.replaceAll("\\p{Cntrl}", "").trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) throw new IllegalArgumentException("The file needs a name.");
        return n.length() > 150 ? n.substring(n.length() - 150) : n;
    }

    /** Refuses an empty, oversize or unrecognised file, or one whose contents do not match what it says it is. */
    public static Checked check(String fileName, String declaredType, byte[] content, Set<String> allowed) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("The file is empty.");
        if (content.length > MAX_BYTES) throw new IllegalArgumentException("The file is larger than " + (MAX_BYTES / (1024 * 1024)) + " MB.");
        String name = cleanName(fileName);
        String actual = sniff(content, name);
        if (actual == null || !allowed.contains(actual))
            throw new IllegalArgumentException(allowed == IMAGES ? "Photos must be JPEG, PNG or WebP images." : "Upload a PDF, a JPEG, PNG or WebP image, or a Word (.docx) or Excel (.xlsx) file.");
        if (declaredType != null && !declaredType.isBlank()) {
            String d = declaredType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            boolean generic = d.equals("application/octet-stream");
            if (!generic && !d.equals(actual) && !(d.equals("image/jpg") && actual.equals(JPEG)))
                throw new IllegalArgumentException("The file is not what it says it is: it looks like " + actual + " but was sent as " + d + ".");
        }
        return new Checked(name, actual, sha256(content));
    }

    public static String sha256(byte[] c) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String title(String t) {
        String v = t == null ? "" : t.trim();
        if (v.isEmpty()) throw new IllegalArgumentException("Give the document a title.");
        if (v.length() > 150) throw new IllegalArgumentException("Keep the title to 150 characters.");
        return v;
    }

    public static String optional(String s, int max, String what) {
        String v = s == null ? "" : s.trim();
        if (v.length() > max) throw new IllegalArgumentException("Keep the " + what + " to " + max + " characters.");
        return v.isEmpty() ? null : v;
    }

    public static LocalDate expiry(LocalDate d, LocalDate today) {
        if (d != null && d.isAfter(today.plusYears(30))) throw new IllegalArgumentException("That expiry date is too far ahead.");
        return d;
    }
}
