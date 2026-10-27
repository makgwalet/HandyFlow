package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.facilities.application.internal.FacilityInspectionScheduleRules;
import za.co.handyflow.platform.facilities.application.internal.FacilityTime;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "Run this checklist here every N days." Not a stored inspection: a due date that the daily sweep reminds about, and
 * that moves on when an inspection for the same checklist, site and asset is completed.
 */
@Entity
@Table(name = "facility_inspection_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityInspectionSchedule {

    public static final int MAX_FREQUENCY_DAYS = 3650;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "frequency_days", nullable = false)
    private int frequencyDays;

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

    public static FacilityInspectionSchedule create(TenantId tenantId, UUID templateId, UUID siteId, UUID assetId,
                                                    int frequencyDays, LocalDate firstDue) {
        requireFrequency(frequencyDays);
        FacilityInspectionSchedule s = new FacilityInspectionSchedule();
        s.tenantId = tenantId;
        s.templateId = templateId;
        s.siteId = siteId;
        s.assetId = assetId;
        s.frequencyDays = frequencyDays;
        s.nextDueDate = firstDue != null ? firstDue : FacilityTime.today().plusDays(frequencyDays);
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    /** Change how often it runs and/or when it is next due. Neither changes the checklist, site or asset. */
    public void update(Integer frequencyDays, LocalDate nextDueDate) {
        if (frequencyDays != null) {
            requireFrequency(frequencyDays);
            this.frequencyDays = frequencyDays;
        }
        if (nextDueDate != null) this.nextDueDate = nextDueDate;
        this.updatedAt = Instant.now();
    }

    /** A matching inspection was completed on {@code completedOn}: move the due date on. */
    public void recordCompleted(LocalDate completedOn) {
        this.nextDueDate = FacilityInspectionScheduleRules.nextDue(nextDueDate, frequencyDays, completedOn);
        this.lastCompletedDate = completedOn;
        this.updatedAt = Instant.now();
    }

    public boolean isOverdue(LocalDate today) { return active && deletedAt == null && nextDueDate.isBefore(today); }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
    public void reactivate() { this.active = true; this.updatedAt = Instant.now(); }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
        this.updatedAt = this.deletedAt;
    }

    private static void requireFrequency(int days) {
        if (days < 1 || days > MAX_FREQUENCY_DAYS)
            throw new IllegalArgumentException("frequencyDays must be between 1 and " + MAX_FREQUENCY_DAYS);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
