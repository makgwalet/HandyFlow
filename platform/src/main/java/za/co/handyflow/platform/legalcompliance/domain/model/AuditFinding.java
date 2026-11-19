package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A finding raised by an audit. OPEN → IN_REMEDIATION (management responds with an owner and a date) → CLOSED.
 * Whether closure is allowed also depends on its corrective actions; that is checked by AssuranceRules in the service because the
 * actions are separate aggregates. The closer must not be the owner or the person who gave the management response.
 */
@Entity(name = "LcAuditFinding")
@Table(name = "legalcompliance_audit_findings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditFinding extends AggregateRoot<AuditFinding> {

    public static final int MIN_NOTE = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "audit_id", nullable = false) private UUID auditId;
    @Column(name = "control_id") private UUID controlId;
    @Enumerated(EnumType.STRING) @Column(name = "finding_type", nullable = false, length = 20) private AuditFindingType findingType;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "root_cause", columnDefinition = "TEXT") private String rootCause;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private FindingSeverity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private AuditFindingStatus status = AuditFindingStatus.OPEN;
    @Column(name = "owner_id") private UUID ownerId;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "management_response", columnDefinition = "TEXT") private String managementResponse;
    @Column(name = "responded_by") private UUID respondedBy;
    @Column(name = "responded_by_name", length = 255) private String respondedByName;
    @Column(name = "responded_at") private Instant respondedAt;
    @Column(name = "closed_by") private UUID closedBy;
    @Column(name = "closed_by_name", length = 255) private String closedByName;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "closure_note", columnDefinition = "TEXT") private String closureNote;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    public static AuditFinding raise(TenantId tenantId, String reference, UUID auditId, UUID controlId, AuditFindingType type, String title, String description,
                                     String rootCause, FindingSeverity severity, UUID ownerId, String ownerName, LocalDate dueDate, UUID by, String byName) {
        if (type == null) throw new IllegalArgumentException("A finding type is required");
        if (severity == null) throw new IllegalArgumentException("A severity is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (description == null || description.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Describe the finding (at least " + MIN_NOTE + " characters)");
        if (ownerName == null || ownerName.isBlank()) throw new IllegalArgumentException("A finding needs an owner");
        if (dueDate == null) throw new IllegalArgumentException("A due date is required");
        AuditFinding f = new AuditFinding();
        f.initTenantId(tenantId);
        f.reference = reference;
        f.auditId = auditId;
        f.controlId = controlId;
        f.findingType = type;
        f.title = title.trim();
        f.description = description.trim();
        f.rootCause = rootCause == null || rootCause.isBlank() ? null : rootCause.trim();
        f.severity = severity;
        f.ownerId = ownerId;
        f.ownerName = ownerName.trim();
        f.dueDate = dueDate;
        f.createdBy = by;
        f.createdByName = byName;
        return f;
    }

    /** The audited party's management response. Can be revised while the finding is open or in remediation. */
    public void respond(String response, UUID ownerId, String ownerName, LocalDate dueDate, UUID by, String byName) {
        if (status == AuditFindingStatus.CLOSED) throw new IllegalStateException("The finding is already closed");
        if (response == null || response.trim().length() < MIN_NOTE) throw new IllegalArgumentException("A management response of at least " + MIN_NOTE + " characters is required");
        if (ownerName == null || ownerName.isBlank()) throw new IllegalArgumentException("An owner is required");
        if (dueDate == null) throw new IllegalArgumentException("A due date is required");
        this.managementResponse = response.trim();
        this.ownerId = ownerId;
        this.ownerName = ownerName.trim();
        this.dueDate = dueDate;
        this.respondedBy = by;
        this.respondedByName = byName;
        this.respondedAt = Instant.now();
        this.status = AuditFindingStatus.IN_REMEDIATION;
    }

    public void close(UUID by, String byName, String note) {
        if (status == AuditFindingStatus.CLOSED) throw new IllegalStateException("The finding is already closed");
        if (findingType != AuditFindingType.OBSERVATION && status != AuditFindingStatus.IN_REMEDIATION)
            throw new IllegalStateException("The audited party must respond to the finding before it can be closed");
        if (by != null && (by.equals(ownerId) || by.equals(respondedBy)))
            throw new IllegalStateException("Segregation of duties: the owner or the person who gave the management response cannot close the finding");
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException("A closure note of at least " + MIN_NOTE + " characters is required");
        this.closedBy = by;
        this.closedByName = byName;
        this.closedAt = Instant.now();
        this.closureNote = note.trim();
        this.status = AuditFindingStatus.CLOSED;
    }

    public boolean isOpen() { return status != AuditFindingStatus.CLOSED; }
    public boolean isOverdue(LocalDate today) { return isOpen() && dueDate.isBefore(today); }
}
