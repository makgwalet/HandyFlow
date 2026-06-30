// security/domain/model/ResourceCustody.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * ResourceCustody — generalized checkout/check-in record for any physical
 * resource a guard is responsible for during a session: radios, keys,
 * firearms, devices, vehicles.
 *
 * WHY one table for all resource types instead of separate tables?
 * The checkout/return/witness/condition-notes pattern is identical regardless
 * of what's being checked out. resourceId is an optional FK to a type-specific
 * table (e.g. the Phase 3 armoury register) when one exists; resourceRef is
 * always a human-readable label since not every resource type has a backing
 * row yet (e.g. "Radio R-014" has no security_radios table).
 *
 * Two-person witness:
 * For high-risk items (firearms, in Phase 3) a second guard's confirmation is
 * required before the checkout is considered valid. For radios/keys this is
 * optional and configured per site.
 */
@Entity
@Table(name = "security_resource_custody")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ResourceCustody {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(name = "resource_ref", nullable = false, length = 100)
    private String resourceRef;   // e.g. "Radio R-014", "Master Key Set B"

    @Column(name = "resource_id")
    private UUID resourceId;      // FK to armoury table (Phase 3) if applicable

    @Column(name = "checked_out_at", nullable = false, updatable = false)
    private Instant checkedOutAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "witnessed_by")
    private UUID witnessedBy;

    @Column(name = "checkout_notes")
    private String checkoutNotes;

    @Column(name = "checkin_notes")
    private String checkinNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_on_return", length = 20)
    private ConditionOnReturn conditionOnReturn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static ResourceCustody checkout(TenantId tenantId, UUID sessionId,
                                           UUID guardId, UUID shiftId,
                                           ResourceType resourceType, String resourceRef,
                                           UUID witnessedBy, String notes) {
        ResourceCustody c   = new ResourceCustody();
        c.tenantId          = tenantId;
        c.sessionId         = sessionId;
        c.guardId           = guardId;
        c.shiftId           = shiftId;
        c.resourceType      = resourceType;
        c.resourceRef       = resourceRef;
        c.witnessedBy       = witnessedBy;
        c.checkoutNotes     = notes;
        c.checkedOutAt      = Instant.now();
        c.createdAt         = Instant.now();
        return c;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void returnResource(ConditionOnReturn condition, String notes) {
        if (this.checkedInAt != null) {
            throw new IllegalStateException("Resource already returned");
        }
        this.checkedInAt       = Instant.now();
        this.conditionOnReturn = condition;
        this.checkinNotes      = notes;
    }

    public boolean isCheckedOut() { return checkedInAt == null; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum ResourceType {
        RADIO, KEY, FIREARM, VEHICLE, OTHER
    }

    public enum ConditionOnReturn {
        GOOD, DAMAGED, MISSING
    }
}
