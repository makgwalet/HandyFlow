package za.co.handyflow.platform.crm.domain.model;

/**
 * ActivityType — all possible events on a Customer's timeline.
 *
 * WHY a separate enum file (not an inner enum)?
 * 1. Reusability: the ActivityType is referenced by CustomerActivity,
 *    CustomerService, and potentially future reporting queries.
 *    An inner enum would force imports of Customer just to get the type.
 * 2. Testability: you can write tests for activity handling without
 *    constructing a full Customer.
 */
public enum ActivityType {

    // ── Lifecycle ─────────────────────────────────────────────────────────
    CREATED,
    UPDATED,
    DELETED,
    RESTORED,

    // ── Status & segmentation ─────────────────────────────────────────────
    STATUS_CHANGED,
    TAG_ADDED,
    TAG_REMOVED,

    /**
     * FIX: "no lead/pipeline stage tracking" gap. Same shape as
     * STATUS_CHANGED (payload: {"from": "NEW", "to": "CONTACTED"}) —
     * deliberately not its own dedicated entity/table the way Consent/
     * FollowUp/Communication needed, since a stage change is structurally
     * identical to a status change: a simple field transition on Customer,
     * not a separate domain concept with its own lifecycle.
     */
    STAGE_CHANGED,

    /**
     * FIX: backlog 4.1 — "no lead ownership/assignment" gap. Same shape as
     * STATUS_CHANGED/STAGE_CHANGED (payload: {"from": "<uuid-or-NONE>",
     * "to": "<uuid-or-NONE>"}) — an owner reassignment is the same kind of
     * simple field transition, not a new domain concept.
     */
    OWNER_CHANGED,

    /**
     * FIX: backlog 4.2 — "no deal value / expected close date" gap. Single
     * event covering both fields together (payload carries both from/to
     * pairs when either or both changed) rather than two separate types —
     * a sales rep updating a forecast typically changes both the amount
     * and the date in the same edit, and splitting them into
     * DEAL_VALUE_CHANGED / CLOSE_DATE_CHANGED would just double the
     * timeline noise for what's really one "updated my forecast" action.
     */
    DEAL_UPDATED,

    // ── Notes ─────────────────────────────────────────────────────────────
    NOTE_ADDED,

    // ── Cross-module events (written by CrmFacade when other modules link) ─
    BOOKING_LINKED,
    INVOICE_LINKED,
    QUOTE_LINKED,
    // NEW: written by CrmFacade.notifyMarketingConsentChanged() — the
    // Marketing module's opt-in/opt-out flow previously had no facade
    // method to call at all, so these events never reached a customer's
    // timeline. Deliberately two distinct types (not one
    // MARKETING_CONSENT_CHANGED with a boolean payload) to match how
    // STATUS_CHANGED-style events read on the timeline UI — "opted out"
    // is a more useful label at a glance than "consent changed: false".
    MARKETING_OPTED_IN,
    MARKETING_OPTED_OUT,

    // ── POPIA / compliance ────────────────────────────────────────────────
    RETENTION_REVIEW_REQUIRED  // System flag: data retention period expired
}