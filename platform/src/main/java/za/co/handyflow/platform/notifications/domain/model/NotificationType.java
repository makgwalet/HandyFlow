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
 */
public enum NotificationType {

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

    // ── HR / Payroll ─────────────────────────────────────────────────────────
    PAY_RUN_PROCESSED(INFO, Set.of(IN_APP, EMAIL)),
    PAYSLIP_AVAILABLE(INFO, Set.of(IN_APP, EMAIL)),
    EMP201_DUE(WARNING, Set.of(IN_APP, EMAIL)),

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

    // ── Supply Chain ─────────────────────────────────────────────────────────
    PO_APPROVED(INFO, Set.of(IN_APP, EMAIL)),
    PO_RETURNED_FOR_REVISION(WARNING, Set.of(IN_APP, EMAIL)),
    SUPPLIER_INVOICE_DISPUTED(WARNING, Set.of(IN_APP, EMAIL)),
    SUPPLIER_INVOICE_OVERDUE(WARNING, Set.of(IN_APP, EMAIL)),
    LOW_STOCK_DIGEST(INFO, Set.of(IN_APP, EMAIL));

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