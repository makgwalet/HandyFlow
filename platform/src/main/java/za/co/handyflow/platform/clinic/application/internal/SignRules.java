package za.co.handyflow.platform.clinic.application.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * What a consultation needs before it can be signed (CLINIC-DEC-010, 011).
 * Symptoms (a chief complaint) and a Diagnosis (text or an ICD-10 code) are required. A clinician may sign without
 * them only by giving a reason, which is then audited. Pure: no Spring, no database.
 * Which steps are required per visit type (CLINIC-DEC-012) will change {@link #missing}; callers do not.
 */
public final class SignRules {
    private SignRules() {}

    public static final String SYMPTOMS  = "SYMPTOMS";
    public static final String DIAGNOSIS = "DIAGNOSIS";
    public static final int MAX_REASON = 500;

    private static boolean has(String s) { return s != null && !s.isBlank(); }

    /** Required steps that are not filled in, in clinical order. Empty means the consultation can be signed as it is. */
    public static List<String> missing(String chiefComplaint, String diagnosis, List<String> icd10Codes) {
        List<String> out = new ArrayList<>();
        if (!has(chiefComplaint)) out.add(SYMPTOMS);
        boolean coded = icd10Codes != null && icd10Codes.stream().anyMatch(SignRules::has);
        if (!has(diagnosis) && !coded) out.add(DIAGNOSIS);
        return out;
    }

    /** Throws when steps are missing and no usable reason was given; returns the trimmed reason (or null when nothing is missing). */
    public static String requireCompleteOrReason(List<String> missing, String overrideReason) {
        if (missing.isEmpty()) return null;
        String names = String.join(" and ", missing.stream().map(SignRules::label).toList());
        if (!has(overrideReason)) {
            throw new IllegalStateException("Cannot sign yet: " + names + " required. Complete "
                    + (missing.size() == 1 ? "it" : "them") + " or give a reason to override the requirement.");
        }
        String reason = overrideReason.trim();
        if (reason.length() > MAX_REASON) {
            throw new IllegalArgumentException("The override reason is limited to " + MAX_REASON + " characters.");
        }
        return reason;
    }

    public static String label(String step) {
        return SYMPTOMS.equals(step) ? "Symptoms" : DIAGNOSIS.equals(step) ? "Diagnosis" : step;
    }
}
