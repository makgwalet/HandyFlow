package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Something that must be put right: a failed inspection check, a lapsed certificate, or a manual finding.
 * OPEN -> IN_PROGRESS (a work order has been raised) -> CLOSED, or CANCELLED with a reason. Closing needs a note
 * saying what was done; when a work order is linked the service also requires it to be finished.
 */
@Entity
@Table(name = "facility_corrective_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityCorrectiveAction {

    public static final Set<String> SOURCES = Set.of("INSPECTION", "CERTIFICATE", "MANUAL");
    public static final List<String> PRIORITIES = List.of("LOW", "NORMAL", "HIGH", "URGENT", "EMERGENCY");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;
    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(name = "asset_id")
    private UUID assetId;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 20)
    private String priority;

    /** True for a failed critical check — drives the action centre's severity. */
    @Column(nullable = false)
    private boolean critical;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "closure_note", length = 1000)
    private String closureNote;
    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FacilityCorrectiveAction create(TenantId tenantId, String sourceType, UUID sourceId, UUID siteId, UUID assetId,
                                                  String description, String priority, boolean critical, LocalDate dueDate,
                                                  UUID createdBy) {
        String source = sourceType == null ? "" : sourceType.trim().toUpperCase();
        if (!SOURCES.contains(source)) throw new IllegalArgumentException("sourceType must be one of " + SOURCES);
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Describe what needs to be put right");
        if (description.trim().length() > 1000) throw new IllegalArgumentException("The description can be at most 1000 characters");
        String p = priority == null || priority.isBlank() ? "NORMAL" : priority.trim().toUpperCase();
        if (!PRIORITIES.contains(p)) throw new IllegalArgumentException("priority must be one of " + PRIORITIES);
        if (dueDate == null) throw new IllegalArgumentException("A due date is required");
        FacilityCorrectiveAction a = new FacilityCorrectiveAction();
        a.tenantId = tenantId;
        a.sourceType = source;
        a.sourceId = sourceId;
        a.siteId = siteId;
        a.assetId = assetId;
        a.description = description.trim();
        a.priority = p;
        a.critical = critical;
        a.dueDate = dueDate;
        a.createdByUserId = createdBy;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    public boolean isTerminal() { return "CLOSED".equals(status) || "CANCELLED".equals(status); }

    public boolean isOverdue(LocalDate today) { return !isTerminal() && dueDate.isBefore(today); }

    public void linkWorkOrder(UUID workOrderId) {
        requireOpen();
        if (this.workOrderId != null) throw new IllegalStateException("A work order has already been raised for this action");
        this.workOrderId = workOrderId;
        this.status = "IN_PROGRESS";
        this.updatedAt = Instant.now();
    }

    public void close(String note) {
        requireOpen();
        if (note == null || note.isBlank()) throw new IllegalArgumentException("Say what was done before closing");
        this.status = "CLOSED";
        this.closureNote = note.trim();
        this.closedAt = Instant.now();
        this.updatedAt = this.closedAt;
    }

    public void cancel(String reason) {
        requireOpen();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to cancel an action");
        this.status = "CANCELLED";
        this.closureNote = reason.trim();
        this.closedAt = Instant.now();
        this.updatedAt = this.closedAt;
    }

    public void reschedule(LocalDate newDue) {
        requireOpen();
        if (newDue == null) throw new IllegalArgumentException("A due date is required");
        this.dueDate = newDue;
        this.updatedAt = Instant.now();
    }

    private void requireOpen() {
        if (isTerminal()) throw new IllegalStateException("This action is already " + status);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
