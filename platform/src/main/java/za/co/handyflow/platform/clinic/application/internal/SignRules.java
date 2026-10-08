package za.co.handyflow.platform.clinic.application.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a consultation needs before it can be signed (CLINIC-DEC-010, 011).
 * Symptoms (a chief complaint) and a Diagnosis (text or an ICD-10 code) are required. A clinician may sign without
 * them only by giving a reason, which is then audited. Pure: no Spring, no database.
 * Which steps are required comes from the visit type (CLINIC-DEC-012, see {@link VisitStageRules}).
 */
public final class SignRules {
    private SignRules() {}

    public static final String SYMPTOMS  = "SYMPTOMS";
    public static final String DIAGNOSIS = "DIAGNOSIS";
    public static final String EXAMINATION = "EXAMINATION";
    public static final String PLAN = "PLAN";
    public static final Set<String> DEFAULT_REQUIRED = VisitStageRules.DEFAULT_REQUIRED;
    public static final int MAX_REASON = 500;

    private static boolean has(String s) { return s != null && !s.isBlank(); }

    /** Symptoms + Diagnosis only (the default for a visit type nobody has configured). */
    public static List<String> missing(String chiefComplaint, String diagnosis, List<String> icd10Codes) {
        return missing(DEFAULT_REQUIRED, chiefComplaint, false, null, diagnosis, icd10Codes, false);
    }

    /**
     * Required steps that are not filled in, in clinical order. Empty means the consultation can be signed as it is.
     * Examination counts as done with findings or any vital sign; Plan with a treatment plan or a follow-up.
     */
    public static List<String> missing(Set<String> required, String chiefComplaint, boolean hasVitals, String examination,
                                       String diagnosis, List<String> icd10Codes, boolean hasPlan) {
        List<String> out = new ArrayList<>();
        if (required.contains(SYMPTOMS) && !has(chiefComplaint)) out.add(SYMPTOMS);
        if (required.contains(EXAMINATION) && !hasVitals && !has(examination)) out.add(EXAMINATION);
        boolean coded = icd10Codes != null && icd10Codes.stream().anyMatch(SignRules::has);
        if (required.contains(DIAGNOSIS) && !has(diagnosis) && !coded) out.add(DIAGNOSIS);
        if (required.contains(PLAN) && !hasPlan) out.add(PLAN);
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
        return switch (step) {
            case SYMPTOMS -> "Symptoms";
            case EXAMINATION -> "Examination";
            case DIAGNOSIS -> "Diagnosis";
            case PLAN -> "Plan";
            default -> step;
        };
    }
}
