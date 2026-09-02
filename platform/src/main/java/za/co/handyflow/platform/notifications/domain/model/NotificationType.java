package za.co.handyflow.platform.notifications.domain.model;

import java.util.Set;

import static za.co.handyflow.platform.notifications.domain.model.NotificationChannel.EMAIL;
import static za.co.handyflow.platform.notifications.domain.model.NotificationChannel.IN_APP;
import static za.co.handyflow.platform.notifications.domain.model.NotificationChannel.SMS;
import static za.co.handyflow.platform.notifications.domain.model.NotificationSeverity.CRITICAL;
import static za.co.handyflow.platform.notifications.domain.model.NotificationSeverity.INFO;
import static za.co.handyflow.platform.notifications.domain.model.NotificationSeverity.WARNING;

/**
 * NotificationType — the full catalogue of events any module can raise via
 * {@code NotificationService.send()}.
 *
 * NOTE ON THIS FILE: this reproduces the existing constant (INCIDENT_REPORTED,
 * used by earthmoving.IncidentService) plus every new type identified in the
 * platform-wide notification review. Merge this into your real
 * NotificationType.java rather than replacing it wholesale — if any other
 * constants already exist beyond INCIDENT_REPORTED, keep them and just add
 * the ones below that are missing.
 *
 * Each constant carries a sane default so most call sites only need to set
 * .type(...), .title(...), .message(...), .recipients(...) and can omit
 * .severity()/.channels() entirely (NotificationRequest falls back to these).
 *
 * Conventions used below:
 *   - IN_APP is always included — every notification should be visible in the bell.
 *   - EMAIL is added for anything the recipient should see even if they're not
 *     logged in (deadlines, compliance, money).
 *   - SMS is reserved for CRITICAL, time-sensitive, or safety-relevant events
 *     only — it costs money per message and should not be default-on for
 *     routine INFO-level events.
 *
 * FIX: Gate Access & Registry (Security sub-module) — added GATE_OVERSTAY.
 * WARNING, not CRITICAL, and no SMS — the plan's own §9 is explicit that an
 * overstay is deliberately kept as a quieter, separate path from a real
 * Incident: "most overstays are someone forgetting to sign out, not a
 * security event." Same tone as GUARD_LATE/PATROL_ROUND_MISSED in this same
 * section, not GUARD_NO_SHOW/DURESS_TRIGGERED.
 *
 * FIX: Track 7 Module 5a (Facilities & Maintenance, internal) — added the
 * FACILITY_* constants. FACILITY_COMPLIANCE_EXPIRED is CRITICAL but not
 * SMS'd — an expired electrical COC/fire/elevator/gas certificate is a real
 * regulatory and insurance risk, but it's a paperwork lapse discovered on a
 * daily sweep, not an active safety emergency in progress, so it follows
 * the same {IN_APP, EMAIL}-only CRITICAL treatment as
 * SARS_DEADLINE_OVERDUE/TRAININGPROVIDER_CERTIFICATE_EXPIRED rather than
 * the SMS-included tier reserved for ASSET_BREAKDOWN/VEHICLE_BREAKDOWN/
 * DURESS_TRIGGERED/GUARD_NO_SHOW/FUEL_NEGATIVE_VARIANCE.
 *
 * FIX: Track 7 Module 5b (Facilities Management, provider) — added the
 * FM_* constants. Same severity reasoning as the 5a FACILITY_* set above,
 * applied to the outsourced-provider sibling (client-billed work orders,
 * per-client service agreements, and the practice's own invoices rather
 * than compliance certificates).
 *
 * FIX: Track 7 Module 6 (Bookkeeping Services) — added the BK_* constants.
 * BK_TRANSACTION_UNRECONCILED is WARNING, not CRITICAL — a stale
 * reconciliation is a practice-hygiene risk worth surfacing daily, not an
 * emergency, matching the tone of AR_AGING_ALERT/STOCK_LOW rather than
 * SARS_DEADLINE_OVERDUE.
 */
public enum NotificationType {

    // ── CRM ──────────────────────────────────────────────────────────────────
    // FIX: backlog 4.1 — new-lead notifications previously used raw
    // EmailService.send() directly from CustomerService, bypassing the
    // notification pipeline entirely (no in-app bell entry, no per-user
    // channel-preference opt-out). INFO, not WARNING — a new lead is a
    // routine, positive event, not something requiring urgent attention.
    NEW_LEAD_ASSIGNED(INFO, Set.of(IN_APP, EMAIL)),

    // ── Existing ─────────────────────────────────────────────────────────────
    INCIDENT_REPORTED(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Billing / Subscription ─────────────────────────────────────────────
    SUBSCRIPTION_TRIAL_ENDING_SOON(INFO, Set.of(IN_APP, EMAIL)),
    MODULE_TRIAL_ENDING_SOON(INFO, Set.of(IN_APP, EMAIL)),
    SUBSCRIPTION_PAST_DUE(WARNING, Set.of(IN_APP, EMAIL)),
    SUBSCRIPTION_SUSPENDED(CRITICAL, Set.of(IN_APP, EMAIL)),
    SUBSCRIPTION_REINSTATED(INFO, Set.of(IN_APP, EMAIL)),

    // ── Accounting ───────────────────────────────────────────────────────────
    SARS_DEADLINE_DUE_30D(INFO, Set.of(IN_APP, EMAIL)),
    SARS_DEADLINE_DUE_7D(WARNING, Set.of(IN_APP, EMAIL)),
    SARS_DEADLINE_DUE_1D(CRITICAL, Set.of(IN_APP, EMAIL)),
    SARS_DEADLINE_OVERDUE(CRITICAL, Set.of(IN_APP, EMAIL)),
    FEE_NOTE_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    JOURNAL_SUBMITTED_FOR_REVIEW(INFO, Set.of(IN_APP)),
    JOURNAL_POSTED(INFO, Set.of(IN_APP)),
    VAT_PERIOD_CLOSING_SOON(WARNING, Set.of(IN_APP, EMAIL)),
    AR_AGING_ALERT(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Accounts Payable ────────────────────────────────────────────────────
    BILL_DUE_SOON(INFO, Set.of(IN_APP, EMAIL)),
    BILL_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    BILL_PENDING_APPROVAL(INFO, Set.of(IN_APP, EMAIL)),
    EFT_BATCH_SUBMITTED(INFO, Set.of(IN_APP)),
    EFT_BATCH_PAID(INFO, Set.of(IN_APP)),

    // ── Bookings ─────────────────────────────────────────────────────────────
    BOOKING_CONFIRMED(INFO, Set.of(IN_APP, EMAIL)),
    BOOKING_REMINDER(INFO, Set.of(IN_APP, EMAIL)),
    BOOKING_CANCELLED(INFO, Set.of(IN_APP, EMAIL)),
    BOOKING_RESCHEDULED(INFO, Set.of(IN_APP, EMAIL)),

    // ── Clinic ───────────────────────────────────────────────────────────────
    LAB_RESULT_RECEIVED(INFO, Set.of(IN_APP)),
    LAB_RESULT_ABNORMAL(WARNING, Set.of(IN_APP, EMAIL)),
    CLAIM_SUBMITTED(INFO, Set.of(IN_APP)),
    CLAIM_PAID(INFO, Set.of(IN_APP, EMAIL)),
    CLAIM_REJECTED(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Contracting ──────────────────────────────────────────────────────────
    CONTRACT_SENT_FOR_SIGNING(INFO, Set.of(IN_APP, EMAIL)),
    CONTRACT_PARTY_SIGNED(INFO, Set.of(IN_APP, EMAIL)),
    CONTRACT_FULLY_SIGNED(INFO, Set.of(IN_APP, EMAIL)),
    CONTRACT_DECLINED(WARNING, Set.of(IN_APP, EMAIL)),
    CONTRACT_COMMENT_POSTED(INFO, Set.of(IN_APP, EMAIL)),

    // ── Creative ─────────────────────────────────────────────────────────────
    PROOF_READY_FOR_APPROVAL(INFO, Set.of(IN_APP, EMAIL)),
    PROOF_APPROVED(INFO, Set.of(IN_APP, EMAIL)),
    PROOF_REJECTED(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Earthmoving / Fleet-of-heavy-plant ──────────────────────────────────
    ASSET_BREAKDOWN(CRITICAL, Set.of(IN_APP, EMAIL, SMS)),
    ASSET_SERVICE_DUE(WARNING, Set.of(IN_APP, EMAIL)),
    ASSET_MAINTENANCE_RECORDED(INFO, Set.of(IN_APP)),
    ASSET_DEPLOYED(INFO, Set.of(IN_APP)),
    ASSET_HIRE_ENDING_SOON(INFO, Set.of(IN_APP, EMAIL)),

    // ── Events ───────────────────────────────────────────────────────────────
    EVENT_LIVE(INFO, Set.of(IN_APP)),
    EVENT_VENDOR_CONFIRMED(INFO, Set.of(IN_APP)),
    EVENT_CAPACITY_MILESTONE(INFO, Set.of(IN_APP)),

    // ── Expenses ─────────────────────────────────────────────────────────────
    EXPENSE_CLAIM_SUBMITTED(INFO, Set.of(IN_APP, EMAIL)),
    EXPENSE_CLAIM_APPROVED(INFO, Set.of(IN_APP, EMAIL)),
    EXPENSE_CLAIM_REJECTED(WARNING, Set.of(IN_APP, EMAIL)),
    EXPENSE_CLAIM_REIMBURSED(INFO, Set.of(IN_APP, EMAIL)),

    // ── Fleet ────────────────────────────────────────────────────────────────
    VEHICLE_SERVICE_DUE(WARNING, Set.of(IN_APP, EMAIL)),
    VEHICLE_LICENCE_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
    VEHICLE_ROADWORTHY_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
    VEHICLE_INSURANCE_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
    VEHICLE_BREAKDOWN(CRITICAL, Set.of(IN_APP, EMAIL, SMS)),
    TRIP_RUNNING_LONG(WARNING, Set.of(IN_APP, EMAIL)),
    DRIVER_LICENSE_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
    DRIVER_PRDP_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Fuel ─────────────────────────────────────────────────────────────────
    FUEL_TANK_LOW(WARNING, Set.of(IN_APP, EMAIL)),
    FUEL_DELIVERY_COMPLETED(INFO, Set.of(IN_APP)),
    FUEL_NEGATIVE_VARIANCE(CRITICAL, Set.of(IN_APP, EMAIL, SMS)),
    FUEL_DELIVERY_UPCOMING(INFO, Set.of(IN_APP, EMAIL)),
    FUEL_DELIVERY_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),

    // ── HR / Payroll ─────────────────────────────────────────────────────────
    PAY_RUN_PROCESSED(INFO, Set.of(IN_APP, EMAIL)),
    PAYSLIP_AVAILABLE(INFO, Set.of(IN_APP, EMAIL)),
    EMP201_DUE(WARNING, Set.of(IN_APP, EMAIL)),
    LEAVE_REQUEST_SUBMITTED(INFO, Set.of(IN_APP, EMAIL)),
    LEAVE_REQUEST_APPROVED(INFO, Set.of(IN_APP, EMAIL)),
    LEAVE_REQUEST_REJECTED(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Invoicing ────────────────────────────────────────────────────────────
    QUOTE_SENT(INFO, Set.of(IN_APP)),
    QUOTE_ACCEPTED(INFO, Set.of(IN_APP, EMAIL)),
    QUOTE_REJECTED(WARNING, Set.of(IN_APP, EMAIL)),
    QUOTE_EXPIRING_SOON(INFO, Set.of(IN_APP, EMAIL)),
    INVOICE_ISSUED(INFO, Set.of(IN_APP)),
    INVOICE_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    INVOICE_PAYMENT_RECEIVED(INFO, Set.of(IN_APP, EMAIL)),
    RETAINER_HOURS_OVERAGE(WARNING, Set.of(IN_APP, EMAIL)),
    RECURRING_SCHEDULE_FAILED(CRITICAL, Set.of(IN_APP, EMAIL)),
    // ── Marketing ────────────────────────────────────────────────────────────
    CAMPAIGN_LAUNCHED(INFO, Set.of(IN_APP)),
    CAMPAIGN_COMPLETED(INFO, Set.of(IN_APP)),

    // ── POS ──────────────────────────────────────────────────────────────────
    STOCK_LOW(WARNING, Set.of(IN_APP, EMAIL)),
    PURCHASE_ORDER_PARTIALLY_RECEIVED(INFO, Set.of(IN_APP)),
    CASH_UP_VARIANCE(WARNING, Set.of(IN_APP, EMAIL)),
    REFUND_PROCESSED(INFO, Set.of(IN_APP)),

    // ── Project Management ──────────────────────────────────────────────────
    MILESTONE_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    RISK_ESCALATED(WARNING, Set.of(IN_APP, EMAIL)),
    RFI_SUBMITTED(INFO, Set.of(IN_APP, EMAIL)),
    RFI_RESPONDED(INFO, Set.of(IN_APP, EMAIL)),
    CHANGE_ORDER_APPROVED(INFO, Set.of(IN_APP, EMAIL)),

    // ── Property ─────────────────────────────────────────────────────────────
    RENT_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    LEASE_EXPIRING_SOON(INFO, Set.of(IN_APP, EMAIL)),
    INSPECTION_DUE(INFO, Set.of(IN_APP, EMAIL)),

    // ── Recruiter ────────────────────────────────────────────────────────────
    NEW_APPLICATION_RECEIVED(INFO, Set.of(IN_APP, EMAIL)),
    INTERVIEW_SCHEDULED(INFO, Set.of(IN_APP, EMAIL)),
    APPLICATION_STAGE_CHANGED(INFO, Set.of(IN_APP)),

    // ── Security ─────────────────────────────────────────────────────────────
    ALARM_EVENT_RAISED(WARNING, Set.of(IN_APP, EMAIL)),
    DURESS_TRIGGERED(CRITICAL, Set.of(IN_APP, EMAIL, SMS)),
    DISPATCH_CREATED(WARNING, Set.of(IN_APP, EMAIL)),
    DISPATCH_RESOLVED(INFO, Set.of(IN_APP)),
    GUARD_NO_SHOW(CRITICAL, Set.of(IN_APP, EMAIL, SMS)),
    GUARD_LATE(WARNING, Set.of(IN_APP, EMAIL)),
    GUARD_OVERTIME_UNCONFIRMED(WARNING, Set.of(IN_APP, EMAIL)),
    PSIRA_EXPIRED(CRITICAL, Set.of(IN_APP, EMAIL)),
    PSIRA_EXPIRING_SOON(WARNING, Set.of(IN_APP, EMAIL)),
    GUARD_SCREENING_DUE(WARNING, Set.of(IN_APP, EMAIL)),
    FIREARM_LICENSE_EXPIRED(CRITICAL, Set.of(IN_APP, EMAIL)),
    FIREARM_LICENSE_EXPIRING_SOON(WARNING, Set.of(IN_APP, EMAIL)),
    SHIFT_SWAP_REQUESTED(INFO, Set.of(IN_APP)),
    SHIFT_SWAP_APPROVED(INFO, Set.of(IN_APP, EMAIL)),
    WEBHOOK_SUBSCRIPTION_SUSPENDED(WARNING, Set.of(IN_APP, EMAIL)),
    PATROL_ROUND_MISSED(WARNING, Set.of(IN_APP, EMAIL)),
    PRINCIPAL_VETTING_HIT(CRITICAL, Set.of(IN_APP, EMAIL)),
    GATE_OVERSTAY(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Supply Chain ─────────────────────────────────────────────────────────
    PO_APPROVED(INFO, Set.of(IN_APP, EMAIL)),
    PO_RETURNED_FOR_REVISION(WARNING, Set.of(IN_APP, EMAIL)),
    SUPPLIER_INVOICE_DISPUTED(WARNING, Set.of(IN_APP, EMAIL)),
    SUPPLIER_INVOICE_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    LOW_STOCK_DIGEST(INFO, Set.of(IN_APP, EMAIL)),

    // ── Training ─────────────────────────────────────────────────────────────
    TRAINING_SESSION_UPCOMING(INFO, Set.of(IN_APP, EMAIL)),
    TRAINING_CERTIFICATE_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
    TRAINING_CERTIFICATE_EXPIRED(CRITICAL, Set.of(IN_APP, EMAIL)),

    // ── Warehousing ──────────────────────────────────────────────────────────
    WAREHOUSING_INBOUND_SHIPMENT_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    WAREHOUSING_OUTBOUND_ORDER_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    WAREHOUSING_INVOICE_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Legal / Compliance ──────────────────────────────────────────────────
    LEGALCOMPLIANCE_OBLIGATION_DUE_SOON(WARNING, Set.of(IN_APP, EMAIL)),
    LEGALCOMPLIANCE_OBLIGATION_OVERDUE(CRITICAL, Set.of(IN_APP, EMAIL)),
    LEGALCOMPLIANCE_DSAR_DUE_SOON(WARNING, Set.of(IN_APP, EMAIL)),
    LEGALCOMPLIANCE_DSAR_OVERDUE(CRITICAL, Set.of(IN_APP, EMAIL)),

    // ── Legal Practice ───────────────────────────────────────────────────────
// LpNotificationScheduler's daily 10:00 SAST sweep of LpMatterKeyDate rows
// (court dates, prescription deadlines, filing deadlines) that are due or
// overdue and not yet acknowledged. WARNING, not CRITICAL — same tier as
// AG_SCOUTING_FOLLOWUP_DUE/AG_HEALTH_EVENT_DUE: a real, time-sensitive
// professional obligation, but not itself a safety or fraud event. Both
// IN_APP and EMAIL — a missed prescription deadline or court date is
// something a firm needs to see even when not logged in.
    LP_MATTER_KEYDATE_DUE(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Agriculture ─────────────────────────────────────────────────────────
    AG_HEALTH_EVENT_DUE(WARNING, Set.of(IN_APP, EMAIL)),
    AG_INVENTORY_LOW_STOCK(WARNING, Set.of(IN_APP, EMAIL)),
    AG_SCOUTING_FOLLOWUP_DUE(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Debt Collection ──────────────────────────────────────────────────────
    DEBTCOLLECTION_CASE_ACTION_DUE_SOON(WARNING, Set.of(IN_APP, EMAIL)),
    DEBTCOLLECTION_CASE_ACTION_OVERDUE(CRITICAL, Set.of(IN_APP, EMAIL)),
    DEBTCOLLECTION_PAYMENT_PLAN_INSTALLMENT_DUE_SOON(WARNING, Set.of(IN_APP, EMAIL)),
    DEBTCOLLECTION_PAYMENT_PLAN_INSTALLMENT_OVERDUE(CRITICAL, Set.of(IN_APP, EMAIL)),


    // ── Collections Agency ───────────────────────────────────────────────────
    COLLECTIONSAGENCY_FIRM_REGISTRATION_DUE_SOON(WARNING, Set.of(IN_APP, EMAIL)),
    COLLECTIONSAGENCY_FIRM_REGISTRATION_EXPIRED(CRITICAL, Set.of(IN_APP, EMAIL)),
    COLLECTIONSAGENCY_COLLECTOR_REGISTRATION_DUE_SOON(WARNING, Set.of(IN_APP, EMAIL)),
    COLLECTIONSAGENCY_COLLECTOR_REGISTRATION_EXPIRED(CRITICAL, Set.of(IN_APP, EMAIL)),
    COLLECTIONSAGENCY_PAYMENT_PLAN_INSTALLMENT_DUE_SOON(WARNING, Set.of(IN_APP, EMAIL)),
    COLLECTIONSAGENCY_PAYMENT_PLAN_INSTALLMENT_OVERDUE(CRITICAL, Set.of(IN_APP, EMAIL)),

    // ── Training Provider ───────────────────────────────────────────────────
    TRAININGPROVIDER_ACCREDITATION_EXPIRING(CRITICAL, Set.of(IN_APP, EMAIL)),
    TRAININGPROVIDER_SESSION_UPCOMING(WARNING, Set.of(IN_APP, EMAIL)),
    TRAININGPROVIDER_CERTIFICATE_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
    TRAININGPROVIDER_CERTIFICATE_EXPIRED(CRITICAL, Set.of(IN_APP, EMAIL)),
    TRAININGPROVIDER_INVOICE_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Facilities & Maintenance (Internal — Track 7 Module 5a) ─────────────
    FACILITY_PPM_DUE(INFO, Set.of(IN_APP, EMAIL)),
    FACILITY_WORKORDER_URGENT(WARNING, Set.of(IN_APP, EMAIL)),
    FACILITY_WORKORDER_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    FACILITY_COMPLIANCE_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
    FACILITY_COMPLIANCE_EXPIRED(CRITICAL, Set.of(IN_APP, EMAIL)),

    // ── Facilities Management (Provider — Track 7 Module 5b) ────────────────
    FM_PPM_DUE(INFO, Set.of(IN_APP, EMAIL)),
    FM_WORKORDER_URGENT(WARNING, Set.of(IN_APP, EMAIL)),
    FM_WORKORDER_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    FM_AGREEMENT_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
    FM_INVOICE_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Bookkeeping Services (Track 7 Module 6) ──────────────────────────────
    BK_TRANSACTION_UNRECONCILED(WARNING, Set.of(IN_APP, EMAIL)),
    BK_AGREEMENT_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
    BK_INVOICE_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),

    // ── Tasks ────────────────────────────────────────────────────────────────
    TASK_ASSIGNED(INFO, Set.of(IN_APP, EMAIL)),
    TASK_COMMENT_ADDED(INFO, Set.of(IN_APP)),
    TASK_DUE_SOON(INFO, Set.of(IN_APP, EMAIL)),
    TASK_OVERDUE(WARNING, Set.of(IN_APP, EMAIL));

    private final NotificationSeverity defaultSeverity;
    private final Set<NotificationChannel> defaultChannels;

    NotificationType(NotificationSeverity defaultSeverity, Set<NotificationChannel> defaultChannels) {
        this.defaultSeverity = defaultSeverity;
        this.defaultChannels = defaultChannels;
    }

    public NotificationSeverity defaultSeverity() {
        return defaultSeverity;
    }

    public Set<NotificationChannel> defaultChannels() {
        return defaultChannels;
    }
}