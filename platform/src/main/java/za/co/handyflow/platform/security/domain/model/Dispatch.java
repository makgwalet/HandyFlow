// security/domain/model/Dispatch.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Dispatch — the response action taken on an AlarmEvent, with SLA timestamps.
 *
 * A dispatch is created when a control room operator (or an automated rule,
 * in a future phase) decides to send a unit in response to an alarm event.
 * Three timestamps drive SLA reporting:
 *   dispatchedAt — when the unit was sent (set at creation)
 *   arrivedAt    — when the unit reported on-scene (response-time SLA)
 *   resolvedAt   — when the situation was closed out (resolution-time SLA)
 *
 * WHY track arrivedAt separately from resolvedAt?
 * "How fast did armed response get there" and "how long did it take to
 * resolve" are two different SLA metrics clients care about, especially for
 * contract disputes — a slow arrival is a different failure mode from a slow
 * resolution once on-scene, and a company needs to be able to show both
 * numbers independently when a client questions response quality.
 *
 * WHY can dispatchedGuardId be null?
 * A POLICE dispatch (SAPS called in) doesn't map to an internal guard
 * record — the unit type is recorded but there's no guard to link.
 */
@Entity
@Table(name = "security_dispatches")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Dispatch {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "alarm_event_id", nullable = false)
    private UUID alarmEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatched_unit_type", nullable = false, length = 20)
    private DispatchedUnitType dispatchedUnitType;

    @Column(name = "dispatched_guard_id")
    private UUID dispatchedGuardId;

    @Column(name = "dispatched_by")
    private UUID dispatchedBy;

    @Column(name = "dispatched_at", nullable = false, updatable = false)
    private Instant dispatchedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DispatchOutcome outcome;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static Dispatch create(TenantId tenantId, UUID alarmEventId,
                                  DispatchedUnitType unitType, UUID dispatchedGuardId,
                                  UUID dispatchedBy) {
        Dispatch d            = new Dispatch();
        d.tenantId            = tenantId;
        d.alarmEventId        = alarmEventId;
        d.dispatchedUnitType  = unitType;
        d.dispatchedGuardId   = dispatchedGuardId;
        d.dispatchedBy        = dispatchedBy;
        d.dispatchedAt        = Instant.now();
        d.createdAt           = Instant.now();
        return d;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void recordArrival() {
        if (this.arrivedAt != null) {
            throw new IllegalStateException("Arrival already recorded");
        }
        this.arrivedAt = Instant.now();
    }

    public void resolve(DispatchOutcome outcome, String notes) {
        if (this.resolvedAt != null) {
            throw new IllegalStateException("Dispatch already resolved");
        }
        this.resolvedAt      = Instant.now();
        this.outcome          = outcome;
        this.resolutionNotes = notes;
    }

    // ── SLA computation ────────────────────────────────────────────────────────

    /** Minutes from dispatch to arrival — null until arrival is recorded. */
    public Long responseTimeMinutes() {
        if (arrivedAt == null) return null;
        return Duration.between(dispatchedAt, arrivedAt).toMinutes();
    }

    /** Minutes from dispatch to full resolution — null until resolved. */
    public Long resolutionTimeMinutes() {
        if (resolvedAt == null) return null;
        return Duration.between(dispatchedAt, resolvedAt).toMinutes();
    }

    public boolean isOpen() { return resolvedAt == null; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum DispatchedUnitType {
        ARMED_RESPONSE, GUARD, POLICE, OTHER
    }

    public enum DispatchOutcome {
        RESOLVED, ESCALATED, FALSE_ALARM, NO_ACTION_NEEDED
    }
}
