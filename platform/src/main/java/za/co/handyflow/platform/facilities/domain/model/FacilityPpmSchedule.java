package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A planned preventive maintenance (PPM) task recurring at a fixed
 * interval against an asset (e.g. "HVAC filter change" every 30 days,
 * "Generator full service" every 180 days, "Fire extinguisher inspection"
 * every 365 days). One asset can have several independent schedules.
 * {@code nextDueDate} is the single source of truth for "is this due" —
 * recomputed from {@code frequencyDays} every time
 * {@code recordCompleted()} runs, never drifting from a separately-tracked
 * counter.
 */
@Entity
@Table(name = "facility_ppm_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityPpmSchedule {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    private String description;

    @Column(name = "frequency_days", nullable = false)
    private Integer frequencyDays;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "last_completed_date")
    private LocalDate lastCompletedDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FacilityPpmSchedule create(TenantId tenantId, UUID assetId, String taskName,
                                              String description, Integer frequencyDays, LocalDate startDate) {
        if (frequencyDays == null || frequencyDays <= 0)
            throw new IllegalArgumentException("frequencyDays must be a positive number of days");
        FacilityPpmSchedule s = new FacilityPpmSchedule();
        s.tenantId = tenantId;
        s.assetId = assetId;
        s.taskName = taskName;
        s.description = description;
        s.frequencyDays = frequencyDays;
        s.nextDueDate = startDate != null ? startDate : LocalDate.now();
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void update(String taskName, String description, Integer frequencyDays) {
        if (taskName != null) this.taskName = taskName;
        this.description = description;
        if (frequencyDays != null) {
            if (frequencyDays <= 0)
                throw new IllegalArgumentException("frequencyDays must be a positive number of days");
            this.frequencyDays = frequencyDays;
        }
        this.updatedAt = Instant.now();
    }

    /** Called when the work order generated from this schedule completes. */
    public void recordCompleted(LocalDate completedDate) {
        this.lastCompletedDate = completedDate;
        this.nextDueDate = completedDate.plusDays(frequencyDays);
        this.updatedAt = Instant.now();
    }

    public boolean isDue(LocalDate asOfDate) {
        return active && !nextDueDate.isAfter(asOfDate);
    }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
    public void reactivate() { this.active = true; this.updatedAt = Instant.now(); }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
