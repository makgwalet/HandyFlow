package za.co.handyflow.platform.projects.application.internal.document;

import java.time.LocalDate;

/**
 * Who may do what with a controlled document, and when a retained document is due for review.
 * Pure — no Spring, no database — so the rules are pinned by plain unit tests.
 *
 * A DOCUMENT CONTROLLER is anyone who holds the tenant authority PM_APPROVE or PM_ADMIN, or whose
 * role on THIS project is MANAGER or DOCUMENT_CONTROLLER (see ProjectMemberRole). Ordinary writers
 * can still upload, submit for review and download.
 *
 *  - Void: a controller may void any unreviewed document. The person who uploaded a document may
 *    void their own mistake while it is still unreviewed. Nobody else.
 *  - Visibility (INTERNAL ⇄ CLIENT) and legal hold: controllers only. Visibility decides what a
 *    client can see, so it is not left to anyone with write access.
 */
public final class DocumentAccessRules {

    private DocumentAccessRules() {}

    public static boolean mayVoid(boolean controller, boolean uploader) {
        return controller || uploader;
    }

    public static boolean mayChangeVisibility(boolean controller) {
        return controller;
    }

    public static boolean mayChangeLegalHold(boolean controller) {
        return controller;
    }

    // ── Retention ────────────────────────────────────────────────────────────

    /** Retention runs from the day the project was closed. Negative years count as zero. */
    public static LocalDate retentionUntil(LocalDate closedOn, int years) {
        return closedOn.plusYears(Math.max(years, 0));
    }

    /**
     * A document is due for disposal REVIEW (never automatic deletion) when its retention date has
     * passed and it is not under legal hold. A document with no retention date is never due.
     */
    public static boolean dueForRetentionReview(LocalDate retentionUntil, boolean legalHold, LocalDate today) {
        return !legalHold && retentionUntil != null && retentionUntil.isBefore(today);
    }
}
