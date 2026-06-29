// security/domain/model/RotationAssignment.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * RotationAssignment — binds one guard to one RotationPattern for a date range.
 *
 * WHY track positionInCycle?
 * When a guard is mid-rotation (e.g. returns from sick leave on day 3 of a
 * 4-on-2-off cycle), the schedule generator needs to know where in the cycle
 * to resume, not restart from day 1.  0 = start of cycle (default for new assignments).
 *
 * WHY allow endsAt = null?
 * Most rotation assignments are open-ended until explicitly terminated
 * (guard transferred, leaves, pattern changes).  The unique constraint
 * NULLS NOT DISTINCT on (guard_id, ends_at) ensures only one open-ended
 * assignment per guard can exist at a time.
 */
@Entity
@Table(name = "security_rotation_assignments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RotationAssignment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "pattern_id", nullable = false)
    private UUID patternId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "starts_at", nullable = false)
    private LocalDate startsAt;

    @Column(name = "ends_at")
    private LocalDate endsAt;

    @Column(name = "position_in_cycle", nullable = false)
    private int positionInCycle = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static RotationAssignment create(
            TenantId tenantId, UUID patternId, UUID guardId,
            LocalDate startsAt, int positionInCycle) {
        RotationAssignment a = new RotationAssignment();
        a.tenantId         = tenantId;
        a.patternId        = patternId;
        a.guardId          = guardId;
        a.startsAt         = startsAt;
        a.positionInCycle  = positionInCycle;
        a.createdAt        = Instant.now();
        return a;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    /** Ends this assignment on the given date, closing the open-ended record. */
    public void end(LocalDate endsAt) {
        if (this.endsAt != null) throw new IllegalStateException("Assignment already ended");
        this.endsAt = endsAt;
    }

    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return !startsAt.isAfter(today) && (endsAt == null || !endsAt.isBefore(today));
    }
}
