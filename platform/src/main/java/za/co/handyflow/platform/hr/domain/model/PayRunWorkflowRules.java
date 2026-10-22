package za.co.handyflow.platform.hr.domain.model;

import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

/**
 * Who may move a pay run on, and from which state. Pure, so it runs without a database.
 * <p>
 * DRAFT, then PREPARED (calculated, waiting for a second person), APPROVED, then FINALISED (locked). A run can be sent back
 * to DRAFT while it is PREPARED or APPROVED. COMPLETED is what runs were called before approval existed: they were already
 * visible to employees, so they stay visible, can still be approved and finalised, and can never be recalculated.
 */
public final class PayRunWorkflowRules {

    public static final String DRAFT = "DRAFT", PROCESSING = "PROCESSING", PREPARED = "PREPARED", APPROVED = "APPROVED",
            FINALISED = "FINALISED", CANCELLED = "CANCELLED", COMPLETED = "COMPLETED";

    private PayRunWorkflowRules() {}

    /** Payslips of these runs may be shown to employees. Others are still being checked. */
    public static boolean isIssued(String status) {
        return FINALISED.equals(status) || COMPLETED.equals(status);
    }

    public static boolean isLocked(String status) {
        return FINALISED.equals(status) || CANCELLED.equals(status);
    }

    /** A run can be (re)calculated while it is a draft or waiting for approval. Issued payslips are never rewritten. */
    public static void checkCanPrepare(String status) {
        if (!DRAFT.equals(status) && !PREPARED.equals(status))
            throw new IllegalStateException(describe(status, "can only be calculated while it is a draft or waiting for approval"));
    }

    /**
     * @param preparedBy who calculated the run; null for runs from before approval existed
     * @param allowSelf  the tenant has chosen to let one person prepare and approve
     * @return true when the approver is the same person who prepared the run, so the history can say so
     */
    public static boolean checkCanApprove(String status, UUID preparedBy, UUID actor, boolean allowSelf, boolean hasBlockingExceptions) {
        if (!PREPARED.equals(status) && !COMPLETED.equals(status))
            throw new IllegalStateException(describe(status, "can only be approved once it has been calculated and is waiting for approval"));
        requireActor(actor);
        boolean same = preparedBy != null && preparedBy.equals(actor);
        if (same && !allowSelf)
            throw new AccessDeniedException("The person who calculated a pay run cannot approve it. Ask another approver.");
        if (hasBlockingExceptions)
            throw new IllegalStateException("This run has errors that must be fixed before it can be approved. Open the review to see them.");
        return same;
    }

    public static void checkCanReject(String status, String note) {
        if (!PREPARED.equals(status) && !APPROVED.equals(status))
            throw new IllegalStateException(describe(status, "can only be sent back while it is waiting for approval or approved"));
        if (note == null || note.isBlank())
            throw new IllegalArgumentException("Give a reason for sending the run back.");
    }

    /** @return true when the person finalising also prepared the run */
    public static boolean checkCanFinalise(String status, UUID preparedBy, UUID actor, boolean allowSelf) {
        if (!APPROVED.equals(status))
            throw new IllegalStateException(describe(status, "can only be finalised after it has been approved"));
        requireActor(actor);
        boolean same = preparedBy != null && preparedBy.equals(actor);
        if (same && !allowSelf)
            throw new AccessDeniedException("The person who calculated a pay run cannot finalise it. Ask another approver.");
        return same;
    }

    public static void checkCanCancel(String status, String reason) {
        if (!DRAFT.equals(status))
            throw new IllegalStateException(describe(status, "can only be cancelled while it is a draft. Send it back first if it is waiting for approval"));
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Give a reason for cancelling the run.");
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }

    private static String describe(String status, String rule) {
        String name = switch (status == null ? "" : status) {
            case FINALISED -> "A finalised pay run is locked and ";
            case CANCELLED -> "A cancelled pay run ";
            default -> "A pay run in status " + status + " ";
        };
        return name + rule + ".";
    }
}
