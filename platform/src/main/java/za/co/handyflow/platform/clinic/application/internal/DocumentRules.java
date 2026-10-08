package za.co.handyflow.platform.clinic.application.internal;

import java.time.LocalDate;
import java.util.Set;

/** What may be uploaded to a patient's documents, and how its details are cleaned. Pure. */
final class DocumentRules {
    private DocumentRules() {}

    /** The types a person can choose when uploading. SICK_NOTE and REFERRAL are only ever written by the system when one is issued. */
    static final Set<String> UPLOAD_TYPES = Set.of("OUTSIDE_REPORT", "IMAGING", "LETTER", "PAPER_NOTES", "CONSENT_FORM", "OTHER");
    static final Set<String> ISSUED_TYPES = Set.of("SICK_NOTE", "REFERRAL");
    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int TITLE_MAX = 200, NOTES_MAX = 1000, REASON_MAX = 300;

    record Clean(String type, String title, LocalDate date, String notes, String fileName, String contentType) {}

    static Clean upload(String type, String title, LocalDate date, String notes, String fileName, String contentType,
                        byte[] bytes, LocalDate today) {
        String t = type == null ? "" : type.trim().toUpperCase().replace(' ', '_');
        if (!UPLOAD_TYPES.contains(t)) throw new IllegalArgumentException("Choose what kind of document this is");
        String ttl = text(title, TITLE_MAX, "Title");
        if (ttl == null) throw new IllegalArgumentException("Give the document a title");
        if (date == null) throw new IllegalArgumentException("Enter the date on the document");
        if (date.isAfter(today)) throw new IllegalArgumentException("The date on the document cannot be in the future");
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Choose a file to upload");
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("The file is larger than 10 MB");
        String ct = detect(bytes);
        if (ct == null) throw new IllegalArgumentException("Only PDF, JPEG and PNG files can be uploaded");
        String declared = contentType == null ? "" : contentType.toLowerCase().trim();
        if (!declared.isEmpty() && !declared.equals("application/octet-stream") && !declared.equals(ct) && !(declared.equals("image/jpg") && ct.equals("image/jpeg")))
            throw new IllegalArgumentException("The file is not the type it says it is");
        return new Clean(t, ttl, date, text(notes, NOTES_MAX, "Notes"), safeName(fileName, ct), ct);
    }

    /** The real type from the first bytes, so a renamed file cannot pass: PDF, JPEG or PNG, else null. */
    static String detect(byte[] b) {
        if (b == null || b.length < 4) return null;
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        return null;
    }

    /** A file name with no folders or odd characters, ending in the extension that matches the real type. */
    static String safeName(String name, String contentType) {
        String ext = switch (contentType) { case "application/pdf" -> ".pdf"; case "image/png" -> ".png"; default -> ".jpg"; };
        String base = name == null ? "" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.replaceAll("[^A-Za-z0-9 _.-]", "").trim();
        if (base.isEmpty()) base = "document";
        if (base.length() > 100) base = base.substring(0, 100);
        return base + ext;
    }

    static String reason(String reason) {
        String r = text(reason, REASON_MAX, "Reason");
        if (r == null) throw new IllegalArgumentException("Give a reason for removing the document");
        return r;
    }

    static String text(String v, int max, String field) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim();
        if (s.length() > max) throw new IllegalArgumentException(field + " is longer than " + max + " characters");
        return s;
    }
}
