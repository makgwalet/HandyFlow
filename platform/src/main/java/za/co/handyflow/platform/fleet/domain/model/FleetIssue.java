package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A maintenance / defect issue against a vehicle — the thing a failed inspection item
 * turns into. Deliberately lightweight: the workshop chunk's work orders can later be
 * raised from (and resolve) issues rather than replace them.
 */
@Entity
@Table(name = "fleet_issues")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FleetIssue {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueSource source;

    @Column(name = "source_inspection_id")
    private UUID sourceInspectionId;

    @Column(name = "source_result_id")
    private UUID sourceResultId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status = IssueStatus.OPEN;

    @Column(name = "reported_by")
    private UUID reportedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static FleetIssue manual(TenantId tenantId, UUID vehicleId, String title, String description,
                                    IssueSeverity severity, UUID reportedBy) {
        FleetIssue i = new FleetIssue();
        i.tenantId = tenantId;
        i.vehicleId = vehicleId;
        i.source = IssueSource.MANUAL;
        i.title = title;
        i.description = description;
        i.severity = severity;
        i.reportedBy = reportedBy;
        i.createdAt = Instant.now();
        i.updatedAt = Instant.now();
        return i;
    }

    public static FleetIssue fromInspection(TenantId tenantId, UUID vehicleId, UUID inspectionId, UUID resultId,
                                            String title, String description, IssueSeverity severity,
                                            UUID reportedBy) {
        FleetIssue i = manual(tenantId, vehicleId, title, description, severity, reportedBy);
        i.source = IssueSource.INSPECTION;
        i.sourceInspectionId = inspectionId;
        i.sourceResultId = resultId;
        return i;
    }

    /** Moves the issue to {@code target} if legal; resolving/dismissing stamps who and when, reopening clears it. */
    public void changeStatus(IssueStatus target, String note, UUID byUser) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot change issue status from " + status + " to " + target);
        }
        this.status = target;
        if (target == IssueStatus.RESOLVED || target == IssueStatus.DISMISSED) {
            this.resolvedAt = Instant.now();
            this.resolvedBy = byUser;
            this.resolutionNote = note;
        } else {
            this.resolvedAt = null;
            this.resolvedBy = null;
            this.resolutionNote = null;
        }
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}