package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.ResourceBookingRules;

import java.time.Instant;
import java.util.UUID;

/** A booking of a quantity of a site resource into a timetable slot. Released with a reason, never deleted. */
@Entity(name = "TrainProvSlotResource")
@Table(name = "trainprov_slot_resources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvSlotResource {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "slot_id", nullable = false, updatable = false)
    private UUID slotId;
    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;
    @Column(nullable = false, updatable = false)
    private int quantity = 1;
    @Column(nullable = false)
    private String status = ResourceBookingRules.BOOKED;
    @Column(name = "clash_note", updatable = false)
    private String clashNote;
    @Column(name = "override_reason", updatable = false)
    private String overrideReason;
    @Column(name = "override_by", updatable = false)
    private UUID overrideBy;
    @Column(name = "override_at", updatable = false)
    private Instant overrideAt;
    @Column(name = "released_reason")
    private String releasedReason;
    @Column(name = "released_by")
    private UUID releasedBy;
    @Column(name = "released_at")
    private Instant releasedAt;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static TrainProvSlotResource book(TenantId t, UUID slotId, UUID resourceId, int quantity, String clashNote, String overrideReason, UUID by) {
        TrainProvSlotResource b = new TrainProvSlotResource();
        b.tenantId = t.getValue();
        b.slotId = slotId;
        b.resourceId = resourceId;
        b.quantity = quantity;
        b.createdBy = by;
        b.createdAt = Instant.now();
        if (clashNote != null) {
            if (overrideReason == null || overrideReason.trim().length() < ResourceBookingRules.MIN_REASON) {
                throw new IllegalArgumentException("Say why the clash is acceptable, in at least " + ResourceBookingRules.MIN_REASON + " characters");
            }
            b.clashNote = clashNote;
            b.overrideReason = overrideReason.trim();
            b.overrideBy = by;
            b.overrideAt = b.createdAt;
        }
        return b;
    }

    public void release(String reason, UUID by) {
        if (!ResourceBookingRules.BOOKED.equals(status)) {
            throw new IllegalStateException("That booking is already released");
        }
        String problem = ResourceBookingRules.releaseProblem(reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        status = ResourceBookingRules.RELEASED;
        releasedReason = reason.trim();
        releasedBy = by;
        releasedAt = Instant.now();
    }
}
