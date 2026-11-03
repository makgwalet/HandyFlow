package za.co.handyflow.platform.projects.domain.enums;

/**
 * Document lifecycle.
 *
 *   CURRENT ──► FOR_REVIEW ──► APPROVED ──► SUPERSEDED   (by a newer approved revision)
 *      │             └───────► REJECTED ──► SUPERSEDED   (by a newer revision)
 *      ├──────────────────────────────────► SUPERSEDED   (a newer revision was uploaded before review)
 *      └──────────────────────────────────► VOIDED       (removed from use — only if never reviewed)
 *   DRAFT ──► FOR_REVIEW | VOIDED                         (legacy status, kept for existing rows)
 *
 * CURRENT means "uploaded, latest, not yet reviewed". SUPERSEDED and VOIDED are final.
 * An APPROVED revision stays in force until a newer revision is itself APPROVED — uploading a
 * newer revision does not silently retire an approved one.
 * Reviewed documents (APPROVED / REJECTED / SUPERSEDED) are never deleted or voided — they are
 * the record of what was decided.
 */
public enum DocumentStatus {
    DRAFT, CURRENT, FOR_REVIEW, APPROVED, REJECTED, SUPERSEDED, VOIDED;

    public boolean canTransitionTo(DocumentStatus target) {
        return switch (this) {
            case DRAFT      -> target == FOR_REVIEW || target == SUPERSEDED || target == VOIDED;
            case CURRENT    -> target == FOR_REVIEW || target == SUPERSEDED || target == VOIDED;
            case FOR_REVIEW -> target == APPROVED   || target == REJECTED   || target == SUPERSEDED;
            case APPROVED   -> target == SUPERSEDED;
            case REJECTED   -> target == SUPERSEDED;
            case SUPERSEDED, VOIDED -> false;
        };
    }

    public boolean isFinal() { return this == SUPERSEDED || this == VOIDED; }

    /** Part of the working register: not retired. */
    public boolean isLive() { return this == CURRENT || this == FOR_REVIEW || this == APPROVED; }

    /** Can this document be sent out on a transmittal? */
    public boolean canBeTransmitted() { return isLive(); }

    /** Superseded automatically when a newer revision is UPLOADED (approved ones wait for approval). */
    public boolean supersededOnUpload() {
        return this == DRAFT || this == CURRENT || this == FOR_REVIEW || this == REJECTED;
    }
}