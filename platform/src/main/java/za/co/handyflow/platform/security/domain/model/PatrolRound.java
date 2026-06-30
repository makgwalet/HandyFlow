// security/domain/model/PatrolRound.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * PatrolRound — one expected patrol window within a shift.
 *
 * This is the unit the system reasons about for fraud detection (Part 6.6),
 * not the individual checkpoint scan. A 12-hour shift with a 2-hour route
 * interval has 6 rounds; each round's scans are evaluated as a group against
 * its expected time window, not against the shift as a whole.
 *
 * Status lifecycle:
 *   EXPECTED      → round generated, not yet started
 *   IN_PROGRESS   → first scan recorded, not yet complete
 *   COMPLETE      → all (or enough, per route config) checkpoints scanned
 *   PARTIAL       → some but not enough checkpoints scanned, round window passed
 *   MISSED        → no scans at all, round window passed (set by scheduler)
 *   OFF_SCHEDULE  → completed too early relative to the previous round
 *                   (front-loading fraud signal) — this is a flag set alongside
 *                   COMPLETE/IN_PROGRESS, not a separate terminal status, since
 *                   the round can still be legitimately "done" just suspiciously fast
 *
 * WHY scansExpected/scansCompleted as plain counters rather than joining
 * security_checkpoint_logs every time?
 * The round status needs to be cheap to query for the live dashboard
 * ("Round 3 of 6 — 4 of 8 checkpoints done") without a join on every render.
 * The counters are updated incrementally as scans come in via recordScan().
 */
@Entity
@Table(name = "security_patrol_rounds")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PatrolRound {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "route_id")
    private UUID routeId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "expected_start_at")
    private Instant expectedStartAt;

    @Column(name = "expected_end_at")
    private Instant expectedEndAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoundStatus status = RoundStatus.EXPECTED;

    @Column(name = "checkpoints_expected", nullable = false)
    private int scansExpected;

    @Column(name = "checkpoints_scanned", nullable = false)
    private int scansCompleted = 0;

    @Column(name = "off_schedule", nullable = false)
    private boolean offSchedule = false;

    @Column(name = "off_schedule_reason")
    private String offScheduleReason;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledgement_note")
    private String acknowledgementNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static PatrolRound create(TenantId tenantId, UUID siteId, UUID shiftId,
                                     UUID routeId, int roundNumber,
                                     Instant expectedStartAt, Instant expectedEndAt,
                                     int scansExpected) {
        PatrolRound r        = new PatrolRound();
        r.tenantId           = tenantId;
        r.siteId             = siteId;
        r.shiftId            = shiftId;
        r.routeId            = routeId;
        r.roundNumber        = roundNumber;
        r.expectedStartAt    = expectedStartAt;
        r.expectedEndAt      = expectedEndAt;
        r.scansExpected      = scansExpected;
        r.status             = RoundStatus.EXPECTED;
        r.createdAt          = Instant.now();
        return r;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    /**
     * Records a checkpoint scan against this round.
     * Called from PatrolRoundService.routeScanToRound() after OFF_SCHEDULE
     * detection has already run.
     */
    public void recordScan(boolean offSchedule, String offScheduleReason) {
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
            this.status    = RoundStatus.IN_PROGRESS;
        }
        this.scansCompleted++;
        if (offSchedule) {
            this.offSchedule       = true;
            this.offScheduleReason = offScheduleReason;
        }
        if (scansCompleted >= scansExpected) {
            this.status      = RoundStatus.COMPLETE;
            this.completedAt = Instant.now();
        }
    }

    /** Called by the missed-round scheduler when the window passes with no scans. */
    public void markMissed() {
        if (this.status != RoundStatus.EXPECTED) return; // already started/completed
        this.status = RoundStatus.MISSED;
    }

    /** Called by the missed-round scheduler when the window passes with partial scans. */
    public void markPartial() {
        if (this.status != RoundStatus.IN_PROGRESS) return;
        this.status = RoundStatus.PARTIAL;
    }

    /** Supervisor acknowledges a MISSED/PARTIAL round with a note. */
    public void acknowledge(UUID supervisorId, String note) {
        this.acknowledgedBy      = supervisorId;
        this.acknowledgementNote = note;
    }

    // ── Enum ───────────────────────────────────────────────────────────────────

    public enum RoundStatus {
        EXPECTED, IN_PROGRESS, COMPLETE, PARTIAL, MISSED
    }
}