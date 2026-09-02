package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The job card — a unit of maintenance work at a site, optionally against
 * a specific asset. Raised either automatically from a
 * {@link FacilityPpmSchedule} coming due, or manually/reactively (a
 * breakdown report, a one-off request). Status lifecycle: OPEN -> ASSIGNED
 * -> IN_PROGRESS -> [ON_HOLD <-> IN_PROGRESS] -> COMPLETED, with CANCELLED
 * reachable from any non-terminal state.
 */
@Entity
@Table(name = "facility_work_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityWorkOrder {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "work_order_number", nullable = false, unique = true)
    private String workOrderNumber;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "ppm_schedule_id")
    private UUID ppmScheduleId; // set only when this work order was generated from a due PPM schedule

    @Column(nullable = false)
    private String category; // PPM, CORRECTIVE, EMERGENCY, INSPECTION, OTHER

    @Column(nullable = false)
    private String priority = "NORMAL"; // LOW, NORMAL, HIGH, URGENT, EMERGENCY

    @Column(nullable = false)
    private String status = "OPEN"; // OPEN, ASSIGNED, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED

    @Column(nullable = false)
    private String description;

    @Column(name = "reported_by")
    private String reportedBy;

    @Column(name = "technician_id")
    private UUID technicianId;
    @Column(name = "technician_name")
    private String technicianName;

    @Column(name = "vendor_id")
    private UUID vendorId;
    @Column(name = "vendor_name")
    private String vendorName;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completion_notes")
    private String completionNotes;

    @Column(precision = 15, scale = 2)
    private BigDecimal cost;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FacilityWorkOrder create(TenantId tenantId, String workOrderNumber, UUID siteId,
                                            UUID assetId, UUID ppmScheduleId, String category,
                                            String priority, String description, String reportedBy,
                                            LocalDate scheduledDate) {
        FacilityWorkOrder w = new FacilityWorkOrder();
        w.tenantId = tenantId;
        w.workOrderNumber = workOrderNumber;
        w.siteId = siteId;
        w.assetId = assetId;
        w.ppmScheduleId = ppmScheduleId;
        w.category = category != null ? category.toUpperCase() : "CORRECTIVE";
        w.priority = priority != null ? priority.toUpperCase() : "NORMAL";
        w.description = description;
        w.reportedBy = reportedBy;
        w.scheduledDate = scheduledDate;
        w.createdAt = Instant.now();
        w.updatedAt = Instant.now();
        return w;
    }

    public void assign(UUID technicianId, String technicianName, UUID vendorId, String vendorName) {
        requireOpenOrAssignable();
        if (technicianId == null && vendorId == null)
            throw new IllegalArgumentException("Assign to either a technician or a vendor");
        this.technicianId = technicianId;
        this.technicianName = technicianName;
        this.vendorId = vendorId;
        this.vendorName = vendorName;
        this.status = "ASSIGNED";
        this.updatedAt = Instant.now();
    }

    public void start() {
        if (!"ASSIGNED".equals(status) && !"ON_HOLD".equals(status))
            throw new IllegalStateException("Only an ASSIGNED or ON_HOLD work order can be started — current status: " + status);
        this.status = "IN_PROGRESS";
        this.updatedAt = Instant.now();
    }

    public void putOnHold(String reason) {
        if (!"IN_PROGRESS".equals(status))
            throw new IllegalStateException("Only an IN_PROGRESS work order can be put on hold — current status: " + status);
        this.status = "ON_HOLD";
        this.completionNotes = reason;
        this.updatedAt = Instant.now();
    }

    public void complete(String completionNotes, BigDecimal cost, Instant completedAt) {
        if (!"IN_PROGRESS".equals(status) && !"ON_HOLD".equals(status) && !"ASSIGNED".equals(status))
            throw new IllegalStateException("Cannot complete a work order in status " + status);
        this.status = "COMPLETED";
        this.completionNotes = completionNotes;
        this.cost = cost;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status))
            throw new IllegalStateException("Cannot cancel a work order that is already " + status);
        this.status = "CANCELLED";
        this.cancellationReason = reason;
        this.updatedAt = Instant.now();
    }

    public boolean isTerminal() { return "COMPLETED".equals(status) || "CANCELLED".equals(status); }
    public boolean isOverdue(LocalDate asOfDate) {
        return !isTerminal() && scheduledDate != null && scheduledDate.isBefore(asOfDate);
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    private void requireOpenOrAssignable() {
        if (isTerminal())
            throw new IllegalStateException("Cannot assign a work order that is already " + status);
    }
}
