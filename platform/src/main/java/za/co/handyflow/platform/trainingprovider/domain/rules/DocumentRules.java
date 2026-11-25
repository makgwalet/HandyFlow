package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Set;

/**
 * Rules and fixed wording for the provider's own learner documents. They are the provider's records, never an authority's certificate. The wording
 * never says a learner is competent, certified, accredited, registered or qualified, and always carries the disclaimer below. A completion letter
 * can only come from a standing "completed" outcome.
 */
public final class DocumentRules {

    private DocumentRules() {
    }

    public static final String COMPLETION_LETTER = "COMPLETION_LETTER";
    public static final String PARTICIPATION_STATEMENT = "PARTICIPATION_STATEMENT";
    public static final Set<String> TYPES = Set.of(COMPLETION_LETTER, PARTICIPATION_STATEMENT);
    public static final String ISSUED = "ISSUED";
    public static final String VOID = "VOID";
    public static final Set<String> METHODS = Set.of("IN_PERSON", "EMAIL", "COLLECTED", "POST");

    public static final String DISCLAIMER = "This document is issued by the training provider from its own records. It is not a certificate, statement of results or "
            + "record of learner achievement issued by the QCTO, SAQA or any other authority, and it does not show that the holder is certified, registered or "
            + "recognised by any body.";

    public static String title(String type) {
        return COMPLETION_LETTER.equals(type) ? "Letter confirming completion of a programme" : "Statement of participation";
    }

    /** The body of the document. Plain statements of what the provider recorded. */
    public static String body(String type, String learnerName, String programmeTitle, Integer versionNo, LocalDate outcomeOn) {
        String programme = programmeTitle + (versionNo == null ? "" : " (version " + versionNo + ")");
        if (COMPLETION_LETTER.equals(type)) {
            return "This letter confirms that " + learnerName + " was enrolled on " + programme + " and that the training provider recorded the learner as having completed "
                    + "the programme on " + outcomeOn + ". This is the provider's own record of that outcome.";
        }
        return "This statement confirms that " + learnerName + " was enrolled on " + programme + ". It records participation only. It does not state that the learner "
                + "completed the programme or that any assessment outcome was reached.";
    }

    /** Why a document cannot be issued, or null. */
    public static String issueProblem(String type, String enrolmentStatus, boolean hasLiveOfType, String completionOutcome, String completionStatus) {
        if (type == null || !TYPES.contains(type)) {
            return "Choose a completion letter or a participation statement";
        }
        if (hasLiveOfType) {
            return "This enrolment already has a document of that type. Void it with a reason before issuing a replacement";
        }
        if (EnrolmentRules.CANCELLED.equals(enrolmentStatus)) {
            return "This enrolment was cancelled";
        }
        if (COMPLETION_LETTER.equals(type) && !(CompletionRules.COMPLETED.equals(completionOutcome) && CompletionRules.RECORDED.equals(completionStatus))) {
            return "A completion letter can only be issued from a standing completed outcome. Record the outcome first";
        }
        return null;
    }

    public static String handOverProblem(String status, boolean alreadyHandedOver, LocalDate on, LocalDate issuedOn, LocalDate today, String to, String method) {
        if (VOID.equals(status)) {
            return "A void document cannot be handed over";
        }
        if (alreadyHandedOver) {
            return "This document has already been handed over";
        }
        if (on == null) {
            return "Enter the day it was handed over";
        }
        if (on.isAfter(today)) {
            return "The hand-over day cannot be in the future";
        }
        if (on.isBefore(issuedOn)) {
            return "The hand-over day cannot be before the document was issued";
        }
        if (!CohortRules.longEnough(to, 2)) {
            return "Say who it was handed over to";
        }
        if (method == null || !METHODS.contains(method)) {
            return "Choose how it was handed over";
        }
        return null;
    }

    public static String voidProblem(String status, String reason) {
        if (VOID.equals(status)) {
            return "This document is already void";
        }
        return CohortRules.reasonProblem(reason);
    }
}
