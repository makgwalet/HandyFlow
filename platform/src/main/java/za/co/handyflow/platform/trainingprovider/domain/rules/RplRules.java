package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Rules for recognition of prior learning. An application carries evidence, each piece reviewed. A decision needs a written basis and the assessor's
 * name, and is confirmed by a second person, or by the decider with a written reason. An RPL decision is its own record: it does not feed readiness,
 * completion or any certificate, and no evidence count or score decides it.
 */
public final class RplRules {

    private RplRules() {
    }

    public static final String OPEN = "OPEN";
    public static final String DECIDED = "DECIDED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String RECOGNISED = "RECOGNISED";
    public static final String PARTLY_RECOGNISED = "PARTLY_RECOGNISED";
    public static final String NOT_RECOGNISED = "NOT_RECOGNISED";
    public static final Set<String> OUTCOMES = Set.of(RECOGNISED, PARTLY_RECOGNISED, NOT_RECOGNISED);
    public static final String RECEIVED = "RECEIVED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String QUERIED = "QUERIED";
    public static final Set<String> KINDS = Set.of("DOCUMENT", "REFERENCE", "WORK_SAMPLE", "INTERVIEW", "OTHER");

    public static String applicationProblem(String enrolmentStatus, LocalDate enrolmentStart, String claim, LocalDate appliedOn, LocalDate today, boolean moduleOk, boolean liveExists) {
        if (!WorkplaceRules.enrolmentOpen(enrolmentStatus)) {
            return "An RPL application can only be made while the enrolment is enrolled or in progress";
        }
        if (!CohortRules.longEnough(claim, 10)) {
            return "Say what prior learning is being claimed, in at least 10 characters";
        }
        if (appliedOn == null) {
            return "Enter the day of the application";
        }
        if (appliedOn.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (enrolmentStart != null && appliedOn.isBefore(enrolmentStart)) {
            return "The day cannot be before the enrolment started";
        }
        if (!moduleOk) {
            return "That module belongs to a different programme version";
        }
        if (liveExists) {
            return "There is already a live RPL application for this enrolment and module. Withdraw it first";
        }
        return null;
    }

    public static String evidenceProblem(String applicationStatus, String kind, String description, LocalDate receivedOn, LocalDate appliedOn, LocalDate today) {
        if (!OPEN.equals(applicationStatus)) {
            return "Evidence can only be added while the application is open";
        }
        if (kind == null || !KINDS.contains(kind)) {
            return "Choose the kind of evidence";
        }
        if (!CohortRules.longEnough(description, 10)) {
            return "Describe the evidence, in at least 10 characters";
        }
        if (receivedOn == null) {
            return "Enter the day the evidence was received";
        }
        if (receivedOn.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (appliedOn != null && receivedOn.isBefore(appliedOn)) {
            return "The evidence cannot be received before the application";
        }
        return null;
    }

    public static String evidenceWithdrawProblem(String applicationStatus, String status, String reason) {
        if (!OPEN.equals(applicationStatus)) {
            return "Evidence can only be withdrawn while the application is open";
        }
        if (!RECEIVED.equals(status)) {
            return "This evidence has already been withdrawn";
        }
        return CohortRules.reasonProblem(reason);
    }

    public static String reviewProblem(String applicationStatus, String evidenceStatus, boolean reviewed, String review, String note, LocalDate on, LocalDate receivedOn, LocalDate today) {
        if (!OPEN.equals(applicationStatus)) {
            return "Evidence can only be reviewed while the application is open";
        }
        if (!RECEIVED.equals(evidenceStatus)) {
            return "Withdrawn evidence cannot be reviewed";
        }
        if (reviewed) {
            return "This evidence has already been reviewed";
        }
        if (!ACCEPTED.equals(review) && !QUERIED.equals(review)) {
            return "Choose accepted or queried";
        }
        if (!CohortRules.longEnough(note, 10)) {
            return "Write the reason for the review, in at least 10 characters";
        }
        if (on == null) {
            return "Enter the day of the review";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (receivedOn != null && on.isBefore(receivedOn)) {
            return "The review cannot be before the evidence was received";
        }
        return null;
    }

    /** What the decision needs from the evidence: at least one live item, every live item reviewed, and recognition only with accepted evidence. */
    public static String decisionProblem(String status, int liveEvidence, int unreviewed, int accepted, String outcome, String basis, String assessor, LocalDate on,
                                         LocalDate appliedOn, LocalDate today, String singleReason, boolean confirmAlone) {
        if (!OPEN.equals(status)) {
            return "Only an open application can be decided";
        }
        if (liveEvidence == 0) {
            return "There is no evidence on this application yet";
        }
        if (unreviewed > 0) {
            return unreviewed + (unreviewed == 1 ? " piece of evidence has" : " pieces of evidence have") + " not been reviewed yet";
        }
        if (outcome == null || !OUTCOMES.contains(outcome)) {
            return "Choose recognised, partly recognised or not recognised";
        }
        if (!NOT_RECOGNISED.equals(outcome) && accepted == 0) {
            return "Recognition needs at least one piece of accepted evidence";
        }
        if (!CohortRules.longEnough(basis, 10)) {
            return "Write the basis for the decision, in at least 10 characters";
        }
        if (!CohortRules.longEnough(assessor, 2)) {
            return "Give the name of the assessor";
        }
        if (on == null) {
            return "Enter the day of the decision";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (appliedOn != null && on.isBefore(appliedOn)) {
            return "The decision cannot be before the application";
        }
        if (confirmAlone && !CohortRules.longEnough(singleReason, 10)) {
            return "To confirm without a second person, say why in at least 10 characters";
        }
        return null;
    }

    public static String confirmProblem(String status, UUID decidedBy, UUID confirmer, LocalDate on, LocalDate decidedOn, LocalDate today) {
        if (!DECIDED.equals(status)) {
            return "Only a decision waiting for confirmation can be confirmed";
        }
        if (confirmer == null || confirmer.equals(decidedBy)) {
            return "A second person must confirm. The person who recorded the decision cannot";
        }
        if (on == null) {
            return "Enter the day of the confirmation";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (decidedOn != null && on.isBefore(decidedOn)) {
            return "It cannot be confirmed before it was decided";
        }
        return null;
    }

    public static String withdrawProblem(String status, String reason) {
        return WITHDRAWN.equals(status) ? "This application has already been withdrawn" : CohortRules.reasonProblem(reason);
    }

    public static String stateLabel(String status, String outcome, boolean confirmedAlone) {
        return switch (status) {
            case OPEN -> "Open. Evidence is being gathered and reviewed";
            case DECIDED -> "Decided as " + outcomeLabel(outcome) + ". Waiting for a second person to confirm";
            case CONFIRMED -> "Confirmed as " + outcomeLabel(outcome) + (confirmedAlone ? " by the decider, with a written reason" : " by a second person")
                    + ". This is an RPL record only. It is not a completion or a certificate";
            default -> "Withdrawn";
        };
    }

    private static String outcomeLabel(String outcome) {
        return outcome == null ? "—" : outcome.toLowerCase().replace('_', ' ');
    }
}
