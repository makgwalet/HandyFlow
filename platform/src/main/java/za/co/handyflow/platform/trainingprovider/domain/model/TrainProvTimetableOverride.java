package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.TimetableRules;

import java.time.Instant;
import java.util.UUID;

/**
 * One approved clash: a slot was booked at the same time as another booking for the same venue, person, cohort or learner, and an
 * administrator said why that was acceptable. The times of both bookings are copied here, so the record still reads true if either slot
 * is changed later. Written once, never changed.
 */
@Entity(name = "TrainProvTimetableOverride")
@Table(name = "trainprov_timetable_overrides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvTimetableOverride {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "slot_id", nullable = false, updatable = false)
    private UUID slotId;
    @Column(name = "resource_type", nullable = false, updatable = false)
    private String resourceType;
    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;
    @Column(name = "resource_label", nullable = false, updatable = false)
    private String resourceLabel;
    @Column(name = "clashing_slot_id", nullable = false, updatable = false)
    private UUID clashingSlotId;
    @Column(name = "slot_starts_at", nullable = false, updatable = false)
    private Instant slotStartsAt;
    @Column(name = "slot_ends_at", nullable = false, updatable = false)
    private Instant slotEndsAt;
    @Column(name = "clash_starts_at", nullable = false, updatable = false)
    private Instant clashStartsAt;
    @Column(name = "clash_ends_at", nullable = false, updatable = false)
    private Instant clashEndsAt;
    @Column(nullable = false, updatable = false)
    private String reason;
    @Column(name = "approved_by", nullable = false, updatable = false)
    private UUID approvedBy;
    @Column(name = "approved_at", nullable = false, updatable = false)
    private Instant approvedAt;

    public static TrainProvTimetableOverride of(TenantId t, TrainProvTimetableSlot slot, TimetableRules.Clash clash, TrainProvTimetableSlot other,
                                                String reason, UUID approver) {
        if (!TimetableRules.reasonOk(reason)) {
            throw new IllegalArgumentException("Say why the clash is acceptable, in at least " + TimetableRules.MIN_REASON + " characters");
        }
        TrainProvTimetableOverride o = new TrainProvTimetableOverride();
        o.tenantId = t.getValue();
        o.slotId = slot.getId();
        o.resourceType = clash.resourceType();
        o.resourceId = clash.resourceId();
        o.resourceLabel = clash.resourceLabel();
        o.clashingSlotId = other.getId();
        o.slotStartsAt = slot.getStartsAt();
        o.slotEndsAt = slot.getEndsAt();
        o.clashStartsAt = other.getStartsAt();
        o.clashEndsAt = other.getEndsAt();
        o.reason = reason.trim();
        o.approvedBy = approver;
        o.approvedAt = Instant.now();
        return o;
    }
}
