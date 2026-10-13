package za.co.handyflow.platform.clinic.application.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What an addendum is and who has to be told about it (patch 0187). Pure. */
final class AddendumRules {
    private AddendumRules() {}

    static final String OTHER = "OTHER";
    static final String LATE_MEDICINE = "LATE_MEDICINE";

    /** Kinds a person may choose, in the order the screen lists them. LATE_MEDICINE is only ever written by the system. */
    static final List<String> CHOOSABLE = List.of("CORRECTION", "ADDITIONAL_INFO", "RESULT", "LATE_ENTRY", OTHER);

    private static final Map<String, String> LABEL = Map.of(
            "CORRECTION", "Correction", "ADDITIONAL_INFO", "Additional information", "RESULT", "Result received",
            "LATE_ENTRY", "Late entry", LATE_MEDICINE, "Medicine added after signing", OTHER, "Note");

    /** The kind a person chose; blank means a plain note. Anything else, including the system's own kind, is refused. */
    static String kind(String raw) {
        if (raw == null || raw.isBlank()) return OTHER;
        String k = raw.trim().toUpperCase();
        if (!CHOOSABLE.contains(k)) throw new IllegalArgumentException("Unknown addendum type: " + raw);
        return k;
    }

    static String label(String kind) { return LABEL.getOrDefault(kind == null ? OTHER : kind, "Note"); }

    /** The person who signed the visit is told when someone else adds to it. */
    static boolean tellSigner(UUID signedBy, UUID author) { return signedBy != null && !signedBy.equals(author); }

    static String noticeTitle(String kind) { return "Addendum added to a visit you signed: " + label(kind); }

    static String noticeDetail(String authorName, String text) {
        String who = authorName == null || authorName.isBlank() ? "Someone" : authorName.trim();
        String t = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        String line = who + " wrote: " + t;
        return line.length() <= 300 ? line : line.substring(0, 297) + "...";
    }
}
