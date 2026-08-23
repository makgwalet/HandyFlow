// security/domain/model/Shift.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Shift.
 * <p>
 * WHY a distinct PULLED status rather than reusing COMPLETED?
 * A normally-completed shift implies the guard worked the full assignment and
 * (if minScanCount > 0) met patrol requirements. A pulled shift is a supervisor
 * interrupt -- reporting, payroll, and attendance-rate calculations need to be
 * able to tell the difference (the guard DID show up and work part of the
 * shift, unlike a genuine no-show).
 * <p>
 * FIX: backlog 7.1/7.3 — ReportingService's completion-rate/guard-hours math
 * previously treated anything not COMPLETED/CANCELLED as effectively missed,
 * silently excluding PULLED entirely (not just miscounting it). Fixed in
 * ReportingService directly — PULLED shifts now credit their partial hours
 * worked (startAt through pulledAt) toward guard-hours/coverage, and appear
 * in their own reporting bucket rather than being folded into completed or
 * missed. See ReportingService's own Javadoc for the full fix. This class
 * itself needed no changes for that fix — pulledAt already existed and was
 * already the right data to use.
 */
@Entity
@Table(name = "security_shifts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Shift {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status;

    private String notes;

    @Column(name = "min_scan_count", nullable = false)
    private int minScanCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    // ── Alert dedup (V210) ────────────────────────────────────────────────────

    @Column(name = "late_alert_sent_at")
    private Instant lateAlertSentAt;

    @Column(name = "no_show_alert_sent_at")
    private Instant noShowAlertSentAt;

    @Column(name = "overtime_alert_sent_at")
    private Instant overtimeAlertSentAt;

    // ── Supervisor interrupts (V210) ──────────────────────────────────────────

    @Column(name = "no_show_dismissed_at")
    private Instant noShowDismissedAt;

    @Column(name = "no_show_dismissed_by")
    private UUID noShowDismissedBy;

    @Column(name = "no_show_dismiss_reason")
    private String noShowDismissReason;

    @Column(name = "overtime_closed_at")
    private Instant overtimeClosedAt;

    @Column(name = "overtime_closed_by")
    private UUID overtimeClosedBy;

    @Column(name = "overtime_close_reason")
    private String overtimeCloseReason;

    @Column(name = "pulled_at")
    private Instant pulledAt;

    @Column(name = "pulled_by")
    private UUID pulledBy;

    @Column(name = "pull_reason")
    private String pullReason;

    @Version
    private Long version;

    public static Shift create(TenantId tenantId, UUID siteId, UUID guardId,
                               Instant startAt, Instant endAt, String notes) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Shift end must be after start");
        }
        Shift s = new Shift();
        s.tenantId  = tenantId;
        s.siteId    = siteId;
        s.guardId   = guardId;
        s.startAt   = startAt;
        s.endAt     = endAt;
        s.status    = ShiftStatus.SCHEDULED;
        s.notes     = notes;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void start() {
        if (status != ShiftStatus.SCHEDULED) {
            throw new IllegalStateException("Only SCHEDULED shifts can be started");
        }
        this.status    = ShiftStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (status != ShiftStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE shifts can be completed");
        }
        this.status    = ShiftStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void miss() {
        if (status != ShiftStatus.SCHEDULED) return;
        this.status    = ShiftStatus.MISSED;
        this.updatedAt = Instant.now();
    }

    public void cancel(UUID cancelledBy) {
        this.status    = ShiftStatus.CANCELLED;
        this.deletedAt = Instant.now();
        this.deletedBy = cancelledBy;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    public void reassignGuard(UUID newGuardId) {
        if (this.status != ShiftStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "Only SCHEDULED shifts can be reassigned via swap (status: " + this.status + ")");
        }
        this.guardId   = newGuardId;
        this.updatedAt = java.time.Instant.now();
    }

    // ── Alert dedup mark methods ──────────────────────────────────────────────
    // Called by NoShowAlertScheduler immediately after a shift is folded into a
    // digest notification -- mark-before-send, same convention as CRM schedulers.

    public void markLateAlertSent()     { this.lateAlertSentAt     = Instant.now(); }
    public void markNoShowAlertSent()   { this.noShowAlertSentAt   = Instant.now(); }
    public void markOvertimeAlertSent() { this.overtimeAlertSentAt = Instant.now(); }

    // ── Supervisor interrupts ─────────────────────────────────────────────────

    /**
     * Dismisses a no-show alert without changing shift status -- e.g. the
     * guard called in sick and a replacement is being arranged manually, or
     * the schedule entry itself was wrong. Purely a record-keeping action;
     * the shift remains SCHEDULED (or whatever it already was) until someone
     * either starts it or cancels it separately.
     */
    public void dismissNoShow(UUID dismissedBy, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to dismiss a no-show alert");
        }
        this.noShowDismissedAt     = Instant.now();
        this.noShowDismissedBy     = dismissedBy;
        this.noShowDismissReason   = reason;
        this.updatedAt             = Instant.now();
    }

    /**
     * Supervisor force-closes an ACTIVE shift that has run past its scheduled
     * end without the guard clocking out. Unlike ShiftService.completeShift(),
     * this bypasses minScanCount enforcement entirely -- the whole point is
     * that the guard isn't available to complete the normal flow, so blocking
     * on a scan-count check the guard has no way to satisfy would be absurd.
     */
    public void closeOvertime(UUID closedBy, String reason) {
        if (status != ShiftStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only ACTIVE shifts can have overtime force-closed (status: " + status + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to force-close overtime");
        }
        this.status                = ShiftStatus.COMPLETED;
        this.overtimeClosedAt      = Instant.now();
        this.overtimeClosedBy      = closedBy;
        this.overtimeCloseReason   = reason;
        this.updatedAt             = Instant.now();
    }

    /**
     * Supervisor pulls a guard off site mid-shift -- an operational decision
     * (client complaint, guard unwell, redeployment elsewhere) rather than a
     * normal end-of-shift completion. Deliberately a distinct terminal status
     * from COMPLETED -- see class javadoc for why.
     *
     * Does NOT reach into DeviceSessionService/ResourceCustody here -- ending
     * the linked device session and flagging any open resource custody
     * (radio/firearm still checked out) is the caller's (ShiftService)
     * responsibility, since this entity has no visibility into those tables.
     */
    public void pullFromSite(UUID pulledBy, String reason) {
        if (status != ShiftStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only ACTIVE shifts can be pulled (status: " + status + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to pull a guard from site");
        }
        this.status     = ShiftStatus.PULLED;
        this.pulledAt   = Instant.now();
        this.pulledBy   = pulledBy;
        this.pullReason = reason;
        this.updatedAt  = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}