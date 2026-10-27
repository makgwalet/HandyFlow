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
 * The job card — a unit of maintenance work at a site, optionally against an asset.
 * <p>
 * Lifecycle (Chunk 4):
 * <pre>
 *   REQUESTED -> (approve) -> OPEN -> ASSIGNED -> IN_PROGRESS <-> ON_HOLD
 *   REQUESTED -> (reject)  -> REJECTED                         (terminal)
 *   IN_PROGRESS -> (complete) -> COMPLETED                     when requiresVerification = false
 *   IN_PROGRESS -> (complete) -> PENDING_VERIFICATION -> (verify) -> COMPLETED
 *                                PENDING_VERIFICATION -> (reject verification) -> IN_PROGRESS (rework)
 *   any non-terminal, non-pending state -> CANCELLED           (terminal)
 * </pre>
 * Work orders raised directly by a manager, or generated from a PPM schedule, start OPEN as before;
 * only requests start REQUESTED. Verification is opt-in per work order, so existing flows are unchanged.
 * <p>
 * Earlier chunks: the hold reason has its own column; re-assigning an in-flight job keeps its status;
 * negative cost is rejected.
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
    private UUID ppmScheduleId; // set only when generated from a due PPM schedule

    @Column(nullable = false)
    private String category; // PPM, CORRECTIVE, EMERGENCY, INSPECTION, OTHER

    @Column(nullable = false)
    private String priority = "NORMAL"; // LOW, NORMAL, HIGH, URGENT, EMERGENCY

    @Column(nullable = false)
    private String status = "OPEN"; // REQUESTED, REJECTED, OPEN, ASSIGNED, IN_PROGRESS, ON_HOLD, PENDING_VERIFICATION, COMPLETED, CANCELLED

    @Column(nullable = false)
    private String description;

    @Column(name = "reported_by")
    private String reportedBy;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "safety_impact", nullable = false)
    private boolean safetyImpact = false;

    /** Set automatically when another open, reactive work order already exists for the same asset — a hint for the triager, never a block. */
    @Column(name = "duplicate_of_work_order_id")
    private UUID duplicateOfWorkOrderId;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

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

    @Column(name = "completed_by_user_id")
    private UUID completedByUserId;

    @Column(name = "completion_notes")
    private String completionNotes;

    @Column(name = "hold_reason", length = 1000)
    private String holdReason;

    @Column(name = "requires_verification", nullable = false)
    private boolean requiresVerification = false;
    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;
    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rework_count", nullable = false)
    private int reworkCount = 0;
    @Column(name = "last_rework_reason", length = 1000)
    private String lastReworkReason;

    @Column(name = "downtime_minutes")
    private Integer downtimeMinutes;
    @Column(name = "failure_cause")
    private String failureCause;

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

    /** Direct/PPM creation — starts OPEN. */
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

    /** Request intake — starts REQUESTED and must be approved before it can be assigned. */
    public static FacilityWorkOrder createRequest(TenantId tenantId, String workOrderNumber, UUID siteId,
                                                  UUID assetId, String category, String priority,
                                                  String description, String reportedBy, UUID requestedByUserId,
                                                  boolean safetyImpact, UUID duplicateOfWorkOrderId) {
        FacilityWorkOrder w = create(tenantId, workOrderNumber, siteId, assetId, null, category, priority,
                description, reportedBy, null);
        w.status = "REQUESTED";
        w.requestedByUserId = requestedByUserId;
        w.safetyImpact = safetyImpact;
        w.duplicateOfWorkOrderId = duplicateOfWorkOrderId;
        return w;
    }

    public void requireVerification(boolean required) {
        this.requiresVerification = required;
        this.updatedAt = Instant.now();
    }

    public void approve(UUID approverId, String priorityOverride, LocalDate scheduledDate, Boolean requiresVerification) {
        if (!"REQUESTED".equals(status))
            throw new IllegalStateException("Only a REQUESTED work order can be approved — current status: " + status);
        this.status = "OPEN";
        this.approvedByUserId = approverId;
        this.approvedAt = Instant.now();
        if (priorityOverride != null) this.priority = priorityOverride;
        if (scheduledDate != null) this.scheduledDate = scheduledDate;
        if (requiresVerification != null) this.requiresVerification = requiresVerification;
        this.updatedAt = Instant.now();
    }

    public void reject(UUID approverId, String reason) {
        if (!"REQUESTED".equals(status))
            throw new IllegalStateException("Only a REQUESTED work order can be rejected — current status: " + status);
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("A reason is required to reject a request");
        this.status = "REJECTED";
        this.approvedByUserId = approverId;
        this.approvedAt = Instant.now();
        this.rejectionReason = reason.trim();
        this.updatedAt = Instant.now();
    }

    /** Only an OPEN work order moves to ASSIGNED; later states keep their status on re-assignment. */
    public void assign(UUID technicianId, String technicianName, UUID vendorId, String vendorName) {
        if ("REQUESTED".equals(status))
            throw new IllegalStateException("Approve the request before assigning it");
        if ("PENDING_VERIFICATION".equals(status))
            throw new IllegalStateException("This work order is awaiting verification and cannot be re-assigned");
        requireNotTerminal();
        if (technicianId == null && vendorId == null)
            throw new IllegalArgumentException("Assign to either a technician or a vendor");
        this.technicianId = technicianId;
        this.technicianName = technicianName;
        this.vendorId = vendorId;
        this.vendorName = vendorName;
        if ("OPEN".equals(status)) this.status = "ASSIGNED";
        this.updatedAt = Instant.now();
    }

    public void start() {
        if (!"ASSIGNED".equals(status) && !"ON_HOLD".equals(status))
            throw new IllegalStateException("Only an ASSIGNED or ON_HOLD work order can be started — current status: " + status);
        this.status = "IN_PROGRESS";
        this.holdReason = null;
        this.updatedAt = Instant.now();
    }

    public void putOnHold(String reason) {
        if (!"IN_PROGRESS".equals(status))
            throw new IllegalStateException("Only an IN_PROGRESS work order can be put on hold — current status: " + status);
        this.status = "ON_HOLD";
        this.holdReason = reason;
        this.updatedAt = Instant.now();
    }

    /** Ends as COMPLETED, or as PENDING_VERIFICATION when this work order requires verification. */
    public void complete(String completionNotes, BigDecimal cost, Instant completedAt, UUID completedByUserId,
                         Integer downtimeMinutes, String failureCause) {
        if (!"IN_PROGRESS".equals(status) && !"ON_HOLD".equals(status) && !"ASSIGNED".equals(status))
            throw new IllegalStateException("Cannot complete a work order in status " + status);
        if (cost != null && cost.signum() < 0)
            throw new IllegalArgumentException("cost must not be negative");
        if (downtimeMinutes != null && downtimeMinutes < 0)
            throw new IllegalArgumentException("downtimeMinutes must not be negative");
        this.status = requiresVerification ? "PENDING_VERIFICATION" : "COMPLETED";
        this.completionNotes = completionNotes;
        this.holdReason = null;
        this.cost = cost;
        this.downtimeMinutes = downtimeMinutes;
        this.failureCause = failureCause;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
        this.completedByUserId = completedByUserId;
        this.updatedAt = Instant.now();
    }

    public void verify(UUID verifierId) {
        if (!"PENDING_VERIFICATION".equals(status))
            throw new IllegalStateException("Only a work order awaiting verification can be verified — current status: " + status);
        this.status = "COMPLETED";
        this.verifiedByUserId = verifierId;
        this.verifiedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Sends the work back to the technician: IN_PROGRESS again, completion details cleared, rework recorded. */
    public void rejectVerification(String reason) {
        if (!"PENDING_VERIFICATION".equals(status))
            throw new IllegalStateException("Only a work order awaiting verification can be sent back — current status: " + status);
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("A reason is required to send work back for rework");
        this.status = "IN_PROGRESS";
        this.reworkCount++;
        this.lastReworkReason = reason.trim();
        this.completedAt = null;
        this.completedByUserId = null;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status) || "REJECTED".equals(status))
            throw new IllegalStateException("Cannot cancel a work order that is already " + status);
        if ("PENDING_VERIFICATION".equals(status))
            throw new IllegalStateException("Work awaiting verification must be verified or sent back for rework, not cancelled");
        this.status = "CANCELLED";
        this.cancellationReason = reason;
        this.updatedAt = Instant.now();
    }

    public boolean isTerminal() {
        return "COMPLETED".equals(status) || "CANCELLED".equals(status) || "REJECTED".equals(status);
    }

    /** Overdue = not finished and not already waiting on a verifier, past its scheduled date. */
    public boolean isOverdue(LocalDate asOfDate) {
        return !isTerminal() && !"PENDING_VERIFICATION".equals(status)
                && scheduledDate != null && scheduledDate.isBefore(asOfDate);
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    private void requireNotTerminal() {
        if (isTerminal())
            throw new IllegalStateException("Cannot assign a work order that is already " + status);
    }
}