package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A workshop job on one vehicle: from request, through quotation and approval, to the work
 * itself, actual cost lines (labour, parts, sublet) and completion.
 * <p>
 * Rules enforced here:
 * <ul>
 *   <li>Legal status transitions only ({@link WorkOrderStatus}).</li>
 *   <li>If there is a quotation, the order must be APPROVED before work can start; with no
 *       quotation (in-house work) it can start straight from OPEN.</li>
 *   <li>An expired quotation cannot be approved.</li>
 *   <li>Cost lines can only be logged while the work is IN_PROGRESS or ON_HOLD.</li>
 *   <li>A SERVICE order needs an odometer reading to complete (it resets the service interval).</li>
 * </ul>
 * The service layer handles everything that touches other aggregates: the vehicle's
 * operational status, the linked issue and accident, and the service-history record.
 */
@Entity
@Table(name = "fleet_work_orders")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WorkOrder {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "reference_number", nullable = false, length = 30)
    private String referenceNumber;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "issue_id")
    private UUID issueId;

    @Column(name = "accident_id")
    private UUID accidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 30)
    private WorkOrderType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WorkOrderPriority priority = WorkOrderPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderStatus status = WorkOrderStatus.OPEN;

    @Column(name = "assigned_technician_id")
    private UUID assignedTechnicianId;

    /** External workshop, when the job isn't done in-house. */
    private String supplier;

    @Column(name = "scheduled_for")
    private LocalDate scheduledFor;

    @Column(name = "odometer_in")
    private Integer odometerIn;

    /** When the vehicle left the road. Downtime counts from here, or from {@link #startedAt} if unset. */
    @Column(name = "vehicle_off_road_from")
    private Instant vehicleOffRoadFrom;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // ── Quotation ───────────────────────────────────────────────────────
    @Column(name = "quoted_amount", precision = 15, scale = 2)
    private BigDecimal quotedAmount;

    @Column(name = "quotation_ref", length = 100)
    private String quotationRef;

    @Column(name = "quotation_date")
    private LocalDate quotationDate;

    @Column(name = "quotation_valid_until")
    private LocalDate quotationValidUntil;

    @Column(name = "quotation_notes", columnDefinition = "TEXT")
    private String quotationNotes;

    // ── Approval / holds ────────────────────────────────────────────────
    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_note", columnDefinition = "TEXT")
    private String approvalNote;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "hold_reason", columnDefinition = "TEXT")
    private String holdReason;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    // ── Completion ──────────────────────────────────────────────────────
    @Column(name = "completion_odometer")
    private Integer completionOdometer;

    @Column(name = "invoice_ref", length = 100)
    private String invoiceRef;

    @Column(name = "completion_notes", columnDefinition = "TEXT")
    private String completionNotes;

    @Column(name = "next_service_km")
    private Integer nextServiceKm;

    /** The VehicleService history row written when the order completed. */
    @Column(name = "service_record_id")
    private UUID serviceRecordId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @BatchSize(size = 50)
    private List<WorkOrderLine> lines = new ArrayList<>();

    // ── Creation & editing ──────────────────────────────────────────────

    public static WorkOrder create(TenantId tenantId, String referenceNumber, UUID vehicleId, UUID issueId,
                                   UUID accidentId, WorkOrderType type, String title, String description,
                                   WorkOrderPriority priority, UUID technicianId, String supplier,
                                   LocalDate scheduledFor, Integer odometerIn, Instant vehicleOffRoadFrom,
                                   UUID requestedBy) {
        WorkOrder w = new WorkOrder();
        w.tenantId = tenantId;
        w.referenceNumber = referenceNumber;
        w.vehicleId = vehicleId;
        w.issueId = issueId;
        w.accidentId = accidentId;
        w.requestedBy = requestedBy;
        w.status = WorkOrderStatus.OPEN;
        w.createdAt = Instant.now();
        w.applyDetails(type, title, description, priority, technicianId, supplier, scheduledFor,
                odometerIn, vehicleOffRoadFrom);
        return w;
    }

    public void updateDetails(WorkOrderType type, String title, String description, WorkOrderPriority priority,
                              UUID technicianId, String supplier, LocalDate scheduledFor, Integer odometerIn,
                              Instant vehicleOffRoadFrom) {
        if (!WorkOrderStatus.ACTIVE.contains(status)) {
            throw new IllegalStateException("Work order " + referenceNumber + " is " + status + " and can no longer be edited");
        }
        applyDetails(type, title, description, priority, technicianId, supplier, scheduledFor,
                odometerIn, vehicleOffRoadFrom);
    }

    // ── Quotation & approval ────────────────────────────────────────────

    /** Records (or revises) the supplier's quotation and moves the order to QUOTED, awaiting approval. */
    public void recordQuotation(BigDecimal amount, String supplier, String reference, LocalDate quotationDate,
                                LocalDate validUntil, String notes) {
        if (status != WorkOrderStatus.OPEN && status != WorkOrderStatus.QUOTED) {
            throw new IllegalStateException("A quotation can only be recorded while the work order is OPEN or QUOTED (it is " + status + ")");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("The quoted amount must be greater than zero");
        }
        if (quotationDate != null && validUntil != null && validUntil.isBefore(quotationDate)) {
            throw new IllegalArgumentException("The quotation cannot expire before its own date");
        }
        this.quotedAmount = amount;
        if (supplier != null) this.supplier = supplier;
        this.quotationRef = reference;
        this.quotationDate = quotationDate;
        this.quotationValidUntil = validUntil;
        this.quotationNotes = notes;
        this.rejectionReason = null;
        this.approvedBy = null;
        this.approvedAt = null;
        this.approvalNote = null;
        if (status == WorkOrderStatus.OPEN) changeStatus(WorkOrderStatus.QUOTED);
        touch();
    }

    public void approve(UUID approver, String note) {
        if (status != WorkOrderStatus.QUOTED) {
            throw new IllegalStateException("Only a QUOTED work order can be approved (it is " + status + ")");
        }
        if (quotationValidUntil != null && quotationValidUntil.isBefore(LocalDate.now())) {
            throw new IllegalStateException("The quotation expired on " + quotationValidUntil
                    + " — obtain a new quotation before approving");
        }
        changeStatus(WorkOrderStatus.APPROVED);
        this.approvedBy = approver;
        this.approvedAt = Instant.now();
        this.approvalNote = note;
    }

    /** Sends a quoted order back to OPEN. The quotation stays on file but must be revised (or the order cancelled). */
    public void reject(String reason) {
        if (status != WorkOrderStatus.QUOTED) {
            throw new IllegalStateException("Only a QUOTED work order can be rejected (it is " + status + ")");
        }
        if (reason == null) throw new IllegalArgumentException("A reason is required to reject a quotation");
        changeStatus(WorkOrderStatus.OPEN);
        this.rejectionReason = reason;
    }

    // ── Work ────────────────────────────────────────────────────────────

    public void start() {
        if (status == WorkOrderStatus.OPEN && quotedAmount != null && quotedAmount.signum() > 0) {
            throw new IllegalStateException("This work order has a quotation that has not been approved — "
                    + "get it approved, or revise the quotation, before starting work");
        }
        changeStatus(WorkOrderStatus.IN_PROGRESS);
        this.startedAt = Instant.now();
    }

    public void hold(String reason) {
        if (reason == null) throw new IllegalArgumentException("A reason is required to put a work order on hold");
        changeStatus(WorkOrderStatus.ON_HOLD);
        this.holdReason = reason;
    }

    public void resume() {
        changeStatus(WorkOrderStatus.IN_PROGRESS);
        this.holdReason = null;
    }

    public void complete(Integer completionOdometer, String invoiceRef, String notes, Integer nextServiceKm,
                         Instant completedAt) {
        if (type == WorkOrderType.SERVICE && completionOdometer == null) {
            throw new IllegalArgumentException("An odometer reading is required to complete a SERVICE work order");
        }
        if (startedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Completion cannot be before the work started");
        }
        changeStatus(WorkOrderStatus.COMPLETED);
        this.completionOdometer = completionOdometer;
        this.invoiceRef = invoiceRef;
        this.completionNotes = notes;
        this.nextServiceKm = nextServiceKm;
        this.completedAt = completedAt;
    }

    public void cancel(String reason) {
        if (reason == null) throw new IllegalArgumentException("A reason is required to cancel a work order");
        changeStatus(WorkOrderStatus.CANCELLED);
        this.cancelReason = reason;
    }

    public void linkServiceRecord(UUID serviceRecordId) {
        this.serviceRecordId = serviceRecordId;
    }

    // ── Cost lines ──────────────────────────────────────────────────────

    public WorkOrderLine addLine(WorkOrderLineType type, String description, String partNumber,
                                 BigDecimal quantity, BigDecimal unitCost, UUID technicianId) {
        requireWorking();
        WorkOrderLine line = WorkOrderLine.create(this, type, description, partNumber, quantity, unitCost, technicianId);
        lines.add(line);
        touch();
        return line;
    }

    public void updateLine(UUID lineId, WorkOrderLineType type, String description, String partNumber,
                           BigDecimal quantity, BigDecimal unitCost, UUID technicianId) {
        requireWorking();
        findLine(lineId).update(type, description, partNumber, quantity, unitCost, technicianId);
        touch();
    }

    public void removeLine(UUID lineId) {
        requireWorking();
        lines.remove(findLine(lineId));
        touch();
    }

    public BigDecimal getTotalFor(WorkOrderLineType lineType) {
        return lines.stream().filter(l -> l.getLineType() == lineType)
                .map(WorkOrderLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getActualTotal() {
        return lines.stream().map(WorkOrderLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void applyDetails(WorkOrderType type, String title, String description, WorkOrderPriority priority,
                              UUID technicianId, String supplier, LocalDate scheduledFor, Integer odometerIn,
                              Instant vehicleOffRoadFrom) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.priority = priority != null ? priority : WorkOrderPriority.NORMAL;
        this.assignedTechnicianId = technicianId;
        this.supplier = supplier;
        this.scheduledFor = scheduledFor;
        this.odometerIn = odometerIn;
        this.vehicleOffRoadFrom = vehicleOffRoadFrom;
        touch();
    }

    private void changeStatus(WorkOrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot change work order " + referenceNumber
                    + " from " + status + " to " + target);
        }
        this.status = target;
        touch();
    }

    private void requireWorking() {
        if (!WorkOrderStatus.WORKING.contains(status)) {
            throw new IllegalStateException("Cost lines can only be changed while work is IN_PROGRESS or ON_HOLD (it is " + status + ")");
        }
    }

    private WorkOrderLine findLine(UUID lineId) {
        return lines.stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Line " + lineId + " does not belong to this work order"));
    }

    private void touch() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}