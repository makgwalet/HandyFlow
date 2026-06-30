// security/domain/model/DetailAssignment.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * DetailAssignment — one guard's role on a protection detail's team roster.
 *
 * Replaces plain Shift for CP work — a detail is staffed by a coordinated
 * team with distinct, non-interchangeable roles, not identical guard slots
 * filling one schedule (Part 9.1).
 *
 * Five roles:
 *   TEAM_LEADER          — coordinates the detail, single point of accountability
 *   DRIVER                — operates a protection vehicle
 *   CPO                    — close protection officer, direct principal contact
 *   ADVANCE                — sent ahead to recon upcoming itinerary stops
 *   COUNTER_SURVEILLANCE    — monitors for hostile surveillance of the principal
 *
 * vehicleId is reserved for Phase 3.5's security_protection_vehicles table —
 * present in the schema now so a later ALTER TABLE isn't needed, but not yet
 * populated by any service code in this core pass.
 */
@Entity
@Table(name = "security_detail_assignments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DetailAssignment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "detail_id", nullable = false)
    private UUID detailId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DetailRole role;

    @Column(name = "assignment_start", nullable = false)
    private Instant assignmentStart;

    @Column(name = "assignment_end")
    private Instant assignmentEnd;

    @Column(name = "vehicle_id")
    private UUID vehicleId;   // reserved for Phase 3.5

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static DetailAssignment create(TenantId tenantId, UUID detailId, UUID guardId,
                                          DetailRole role, Instant assignmentStart) {
        DetailAssignment a   = new DetailAssignment();
        a.tenantId           = tenantId;
        a.detailId           = detailId;
        a.guardId            = guardId;
        a.role               = role;
        a.assignmentStart    = assignmentStart;
        a.createdAt          = Instant.now();
        return a;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void end(Instant assignmentEnd) {
        if (this.assignmentEnd != null) {
            throw new IllegalStateException("Assignment already ended");
        }
        this.assignmentEnd = assignmentEnd;
    }

    public boolean isActive() { return assignmentEnd == null; }

    // ── Enum ───────────────────────────────────────────────────────────────────

    public enum DetailRole {
        TEAM_LEADER, DRIVER, CPO, ADVANCE, COUNTER_SURVEILLANCE
    }
}
