package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;

/**
 * Rules for learnership agreements. An agreement is a record of the paper agreement between the learner, the employer and the provider. The registration
 * reference is kept exactly as staff entered it and is never verified here, so nothing is ever called registered or approved because a reference was typed.
 */
public final class LearnershipRules {

    private LearnershipRules() {
    }

    public static final String DRAFT = "DRAFT";
    public static final String SIGNED = "SIGNED";
    public static final String ENDED = "ENDED";
    public static final String CANCELLED = "CANCELLED";

    public static String agreementProblem(String enrolmentStatus, String employer, LocalDate start, LocalDate end, LocalDate enrolmentStart, boolean liveExists) {
        if (!WorkplaceRules.enrolmentOpen(enrolmentStatus)) {
            return "A learnership agreement can only be added while the enrolment is enrolled or in progress";
        }
        String details = detailsProblem(employer, start, end, enrolmentStart);
        if (details != null) {
            return details;
        }
        return liveExists ? "This enrolment already has a live learnership agreement. Cancel it first" : null;
    }

    public static String detailsProblem(String employer, LocalDate start, LocalDate end, LocalDate enrolmentStart) {
        if (!CohortRules.longEnough(employer, 2)) {
            return "Give the name of the employer";
        }
        if (start == null) {
            return "Enter the planned start day";
        }
        if (end != null && end.isBefore(start)) {
            return "The end day cannot be before the start day";
        }
        if (enrolmentStart != null && start.isBefore(enrolmentStart.minusYears(1))) {
            return "The start day is more than a year before the enrolment started";
        }
        return null;
    }

    public static String editProblem(String status) {
        return DRAFT.equals(status) ? null : "Only a draft agreement can be changed. A signed agreement is a record of what was signed";
    }

    public static String signProblem(String status, LocalDate learner, LocalDate employer, LocalDate provider, LocalDate today) {
        if (!DRAFT.equals(status)) {
            return "Only a draft agreement can be recorded as signed";
        }
        if (learner == null || employer == null || provider == null) {
            return "Enter the day each of the learner, the employer and the provider signed";
        }
        for (LocalDate d : new LocalDate[]{learner, employer, provider}) {
            if (d.isAfter(today)) {
                return "A signing day cannot be in the future";
            }
        }
        return null;
    }

    public static String registrationProblem(String status, boolean alreadyRecorded, String reference, LocalDate on, LocalDate today) {
        if (!SIGNED.equals(status)) {
            return "A registration reference can only be recorded on a signed agreement";
        }
        if (alreadyRecorded) {
            return "A registration reference has already been recorded";
        }
        if (!CohortRules.longEnough(reference, 3)) {
            return "Enter the reference exactly as it appears on the document";
        }
        if (on == null) {
            return "Enter the day the reference was recorded";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        return null;
    }

    public static String endProblem(String status, LocalDate on, LocalDate latestSigned, LocalDate today, String reason) {
        if (!SIGNED.equals(status)) {
            return "Only a signed agreement can be ended";
        }
        if (on == null) {
            return "Enter the day the agreement ended";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (latestSigned != null && on.isBefore(latestSigned)) {
            return "It cannot end before it was signed";
        }
        return CohortRules.reasonProblem(reason);
    }

    public static String cancelProblem(String status, boolean registrationRecorded, String reason) {
        if (!DRAFT.equals(status) && !SIGNED.equals(status)) {
            return "A " + status.toLowerCase() + " agreement cannot be cancelled";
        }
        if (registrationRecorded) {
            return "A registration reference has been recorded. End the agreement with a reason instead";
        }
        return CohortRules.reasonProblem(reason);
    }

    public static LocalDate latest(LocalDate a, LocalDate b, LocalDate c) {
        LocalDate m = a;
        for (LocalDate d : new LocalDate[]{b, c}) {
            if (d != null && (m == null || d.isAfter(m))) {
                m = d;
            }
        }
        return m;
    }

    /** A plain line. It records what was signed and entered, and never says the learnership is registered or approved. */
    public static String summary(String status, LocalDate learner, LocalDate employer, LocalDate provider, String registrationRef) {
        String base = switch (status) {
            case DRAFT -> "Draft. Not yet recorded as signed.";
            case SIGNED, ENDED -> "Recorded as signed by the learner (" + learner + "), the employer (" + employer + ") and the provider (" + provider + ").";
            default -> "Cancelled.";
        };
        String reg = registrationRef == null ? "" : " Registration reference as entered: " + registrationRef + ". It has not been checked against any register.";
        return base + " This is a record of the paper agreement. It does not confirm that the learnership is registered or approved." + reg;
    }
}
