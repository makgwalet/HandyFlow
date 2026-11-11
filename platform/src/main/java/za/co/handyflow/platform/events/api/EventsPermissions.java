package za.co.handyflow.platform.events.api;

/**
 * SpEL expressions for @PreAuthorize on the Events controllers — one place to
 * see which authority guards which kind of action.
 * <p>
 * MIGRATION-SAFE: every write expression accepts EITHER the fine-grained
 * authority OR the legacy EVENTS_MANAGE, so existing roles keep working
 * unchanged. A role can be moved to the narrower authorities (e.g. give a
 * door-staff role EVENTS_CHECKIN only) without any further code change.
 * Once every role has been migrated, drop EVENTS_MANAGE from these strings
 * to make the separation strict.
 * <p>
 * The authority codes must exist in the identity module's permission data
 * before they can be granted to a role. Until they do, EVENTS_MANAGE holders
 * simply continue to pass every check.
 */
public final class EventsPermissions {

    private EventsPermissions() {}

    public static final String READ         = "hasAuthority('EVENTS_READ')";

    /** Create, edit, postpone and duplicate events. */
    public static final String WRITE        = "hasAnyAuthority('EVENTS_WRITE','EVENTS_MANAGE')";

    /** Publish (open registration), go live, complete. */
    public static final String APPROVE      = "hasAnyAuthority('EVENTS_APPROVE','EVENTS_MANAGE')";

    /** Cancel an entire event — separated from editing because of its financial and legal impact. */
    public static final String CANCEL       = "hasAnyAuthority('EVENTS_CANCEL','EVENTS_MANAGE')";

    /** Register, cancel, transfer and import guests; manage the waitlist. */
    public static final String REGISTRATION = "hasAnyAuthority('EVENTS_REGISTRATION_MANAGE','EVENTS_MANAGE')";

    /** Ticket tiers, pricing and capacity. */
    public static final String TICKETING    = "hasAnyAuthority('EVENTS_TICKETING_MANAGE','EVENTS_MANAGE')";

    /** Door scanning — a check-in operator needs nothing else. */
    public static final String CHECKIN      = "hasAnyAuthority('EVENTS_CHECKIN','EVENTS_MANAGE')";

    /** Registering someone at the door: door staff (check-in) or registration staff may do it. */
    public static final String WALK_IN      =
            "hasAnyAuthority('EVENTS_CHECKIN','EVENTS_REGISTRATION_MANAGE','EVENTS_MANAGE')";

    /** Vendor / supplier records, supplier quotes and commitments (procurement). */
    public static final String SUPPLIER     = "hasAnyAuthority('EVENTS_SUPPLIER_MANAGE','EVENTS_MANAGE')";

    /** Read-only access to an event's change history (auditors). */
    public static final String AUDIT_READ   = "hasAnyAuthority('EVENTS_AUDIT_READ','EVENTS_MANAGE')";

    // ── Budget & supplier costs (Chunk 4) ─────────────────────────────────────

    /**
     * See budgets, commitments, cost entries and supplier balances. Financial
     * data is deliberately NOT covered by plain EVENTS_READ. Procurement staff
     * can read it too, since they need the budget lines to raise quotes against.
     */
    public static final String BUDGET_READ =
            "hasAnyAuthority('EVENTS_BUDGET_READ','EVENTS_BUDGET_MANAGE','EVENTS_SUPPLIER_MANAGE','EVENTS_MANAGE')";

    /** Edit budget lines, submit the budget, and record or void invoices and payments (finance). */
    public static final String BUDGET_MANAGE  = "hasAnyAuthority('EVENTS_BUDGET_MANAGE','EVENTS_MANAGE')";

    /** Approve, reject or reopen the budget, and approve supplier commitments. */
    public static final String BUDGET_APPROVE = "hasAnyAuthority('EVENTS_BUDGET_APPROVE','EVENTS_MANAGE')";

    // ── Dashboards & reports (Chunk 5) ────────────────────────────────────────

    /**
     * Operational dashboards: the events overview, registration and attendance.
     * These show counts, never personal details or money, so plain EVENTS_READ
     * is enough — an auditor or report viewer can be given just EVENTS_REPORTS_READ.
     */
    public static final String REPORTS_READ =
            "hasAnyAuthority('EVENTS_REPORTS_READ','EVENTS_READ','EVENTS_MANAGE')";

    /**
     * Downloads that contain personal information (guest register, no-show list,
     * check-in log). Deliberately separate from reading and from editing — this
     * is the permission that lets personal data LEAVE the system, and every use
     * of it is written to the event's audit trail.
     */
    public static final String EXPORT_PII = "hasAnyAuthority('EVENTS_EXPORT','EVENTS_MANAGE')";

    // ── Payments (Chunk 6) ────────────────────────────────────────────────────

    /** See payments, refunds and the profitability picture. Financial, so not covered by plain EVENTS_READ. */
    public static final String PAYMENTS_READ =
            "hasAnyAuthority('EVENTS_PAYMENTS_READ','EVENTS_PAYMENTS_MANAGE','EVENTS_BUDGET_READ','EVENTS_MANAGE')";

    /** Confirm money received, and cancel open checkouts. */
    public static final String PAYMENTS_MANAGE = "hasAnyAuthority('EVENTS_PAYMENTS_MANAGE','EVENTS_MANAGE')";

    /**
     * Give money back: raise, complete or fail refunds, and refund a cancelled event in bulk.
     * Deliberately separate from confirming payments — the person who takes the money should not
     * also be the person who can return it.
     */
    public static final String PAYMENTS_REFUND = "hasAnyAuthority('EVENTS_REFUND','EVENTS_MANAGE')";

    // ── Event-day operations (Chunk 7) ────────────────────────────────────────
    // The programme and checklists are readable with plain EVENTS_READ (REPORTS_READ / READ above).
    // What is NOT: the roster, incidents and emergency contacts — they hold staff phone numbers and
    // descriptions of things that went wrong, so they need an operations permission.

    /** See the staff roster (with phone numbers), incidents and emergency contacts. */
    public static final String OPS_READ = "hasAnyAuthority('EVENTS_OPS_READ','EVENTS_OPS_MANAGE','EVENTS_MANAGE')";

    /** Edit the run sheet, roster, contacts and checklist; handle, escalate and resolve incidents. */
    public static final String OPS_MANAGE = "hasAnyAuthority('EVENTS_OPS_MANAGE','EVENTS_MANAGE')";

    /**
     * Report an incident or add a note to one. Wider than OPS_MANAGE on purpose: the person who sees the
     * problem is often door staff or a steward, not an operations manager, and must be able to raise it.
     */
    public static final String INCIDENT_REPORT =
            "hasAnyAuthority('EVENTS_INCIDENT_REPORT','EVENTS_OPS_MANAGE','EVENTS_CHECKIN','EVENTS_MANAGE')";
}
