package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A maintenance job on one machine: requested, started, possibly waiting for parts, then completed or cancelled. */
@Entity
@Table(name = "earthmoving_work_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthWorkOrder {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "wo_number", nullable = false)
    private String number;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String priority = "NORMAL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderStatus status = WorkOrderStatus.OPEN;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "assigned_to_name")
    private String assignedToName;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private String source = "MANUAL";

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "blocks_release", nullable = false)
    private boolean blocksRelease = true;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "meter_hours", precision = 10, scale = 1)
    private BigDecimal meterHours;

    @Column(name = "downtime_hours", precision = 10, scale = 1)
    private BigDecimal downtimeHours;

    @Column(name = "labour_hours", precision = 10, scale = 1)
    private BigDecimal labourHours;

    @Column(name = "work_done")
    private String workDone;

    @Column(name = "test_result")
    private String testResult;

    @Column(name = "maintenance_record_id")
    private UUID maintenanceRecordId;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static EarthWorkOrder create(TenantId tenantId, UUID assetId, String number, String title, String description,
                                        String type, String priority, LocalDate dueDate, String source, UUID planId,
                                        UUID incidentId, boolean blocksRelease, UUID requestedBy) {
        EarthWorkOrder w = new EarthWorkOrder();
        w.tenantId = tenantId;
        w.assetId = assetId;
        w.number = number;
        w.title = title.trim();
        w.description = description;
        w.type = type.trim().toUpperCase();
        w.priority = priority == null || priority.isBlank() ? "NORMAL" : priority.trim().toUpperCase();
        w.dueDate = dueDate;
        w.source = source;
        w.planId = planId;
        w.incidentId = incidentId;
        w.blocksRelease = blocksRelease;
        w.requestedBy = requestedBy;
        w.createdAt = Instant.now();
        w.updatedAt = w.createdAt;
        return w;
    }

    private void move(WorkOrderStatus to) {
        WorkOrderRules.checkMove(status, to);
        this.status = to;
        this.updatedAt = Instant.now();
    }

    public void assign(UUID userId, String name) {
        if (!status.isOpen()) throw new IllegalStateException("A " + status.name().toLowerCase() + " work order cannot be reassigned.");
        this.assignedToUserId = userId;
        this.assignedToName = name;
        this.updatedAt = Instant.now();
    }

    public void start() { move(WorkOrderStatus.IN_PROGRESS); if (startedAt == null) startedAt = Instant.now(); }

    public void awaitParts() { move(WorkOrderStatus.AWAITING_PARTS); }

    public void resume() { move(WorkOrderStatus.IN_PROGRESS); }

    public void complete(UUID by, String workDone, String testResult, BigDecimal meterHours, BigDecimal downtimeHours,
                         BigDecimal labourHours, UUID maintenanceRecordId) {
        WorkOrderRules.checkComplete(type, workDone, testResult, meterHours, downtimeHours, labourHours);
        move(WorkOrderStatus.COMPLETED);
        this.workDone = workDone.trim();
        this.testResult = testResult == null ? null : testResult.trim();
        this.meterHours = meterHours;
        this.downtimeHours = downtimeHours;
        this.labourHours = labourHours;
        this.completedAt = Instant.now();
        this.completedBy = by;
        this.maintenanceRecordId = maintenanceRecordId;
    }

    public void cancel(String reason) {
        String why = EarthmovingRules.requireReason(reason, "cancelling a work order");
        move(WorkOrderStatus.CANCELLED);
        this.cancelReason = why;
    }

    public void setBlocksRelease(boolean value) {
        if (!status.isOpen()) throw new IllegalStateException("Only an open work order can change whether it blocks release.");
        this.blocksRelease = value;
        this.updatedAt = Instant.now();
    }

    public boolean isOpenBlocking() { return status.isOpen() && blocksRelease; }

    public boolean isOverdue(LocalDate today) { return status.isOpen() && dueDate != null && dueDate.isBefore(today); }
}
