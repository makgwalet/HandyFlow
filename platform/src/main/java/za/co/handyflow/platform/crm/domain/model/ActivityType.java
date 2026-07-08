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