package za.co.handyflow.platform.clinic.application.internal;

import java.util.Map;

/** Document numbers: prefix, year, running number (patch 0193). Pure. */
public final class DocumentNumberRules {

    private static final Map<String, String> PREFIX = Map.of(
            "SICK_NOTE", "SN", "REFERRAL", "RF", "PRESCRIPTION", "RX", "MEDICAL_REPORT", "MR",
            "LETTER", "LT", "CONSENT_FORM", "CF");
    private static final Map<String, String> LABEL = Map.of(
            "SICK_NOTE", "Sick note", "REFERRAL", "Referral letter", "PRESCRIPTION", "Prescription", "MEDICAL_REPORT", "Medical report",
            "LETTER", "Letter", "CONSENT_FORM", "Consent form");

    private DocumentNumberRules() {}

    public static boolean known(String type) {
        return type != null && PREFIX.containsKey(type);
    }

    public static String label(String type) {
        return LABEL.getOrDefault(type, "Document");
    }

    /** {@code LT-2026-000042}. The year is the South African calendar year, so a document does not change number at midnight UTC. */
    public static String format(String type, int year, int sequence) {
        if (!known(type)) throw new IllegalArgumentException("Unknown document type: " + type);
        if (sequence < 1) throw new IllegalArgumentException("A document number starts at 1");
        return PREFIX.get(type) + "-" + year + "-" + String.format("%06d", sequence);
    }

    /** The line printed at the foot of every issued document. Short enough to share a line with the page number. */
    public static String footer(String number, String code, String verifyPage) {
        StringBuilder sb = new StringBuilder("Document ").append(number);
        if (code != null && !code.isBlank()) sb.append(" · Verify code ").append(code);
        if (verifyPage != null && !verifyPage.isBlank()) sb.append(" at ").append(verifyPage.replaceFirst("^https?://", ""));
        return sb.toString();
    }
}
