package za.co.handyflow.platform.controls.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A flagged problem from ANY module, on the shared "needs attention"
 * board — Stage 1 of the Financial Control & Assurance plan, Option C.
 * <p>
 * Deliberately generic, same shape decision as Evidence: sourceModule +
 * controlType + relatedEntityType + relatedEntityId identify what
 * raised this and what it's about, without a foreign key into any
 * specific module's schema — a check in Accounting or Payroll should be
 * able to raise one of these without this module ever depending on them.
 * <p>
 * Confirmed real precedent this generalizes: SCM's own three-way match
 * (ScSupplierInvoice.matchStatus = DISPUTE) — that mechanism is
 * UNTOUCHED by this class. SCM keeps its own matchStatus as the real
 * source of truth for its own screens; this table is an ADDITIONAL,
 * parallel record for the cross-module view, written at the same
 * moment SCM already notifies on a dispute, not a replacement for it.
 */
@Entity
@Table(name = "control_exceptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ControlException {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;

    @Column(name = "source_module", nullable = false, length = 50) private String sourceModule;
    @Column(name = "control_type", nullable = false, length = 100) private String controlType;
    @Column(name = "related_entity_type", nullable = false, length = 100) private String relatedEntityType;
    @Column(name = "related_entity_id", nullable = false) private UUID relatedEntityId;

    @Column(name = "severity", nullable = false, length = 20) private String severity = "WARNING";
    @Column(name = "description", nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "status", nullable = false, length = 20) private String status = "OPEN";

    @Column(name = "detected_at", nullable = false) private Instant detectedAt = Instant.now();
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolved_by") private UUID resolvedBy;
    @Column(name = "resolved_by_name") private String resolvedByName;
    @Column(name = "resolution_notes", columnDefinition = "TEXT") private String resolutionNotes;

    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    public static ControlException raise(UUID tenantId, String sourceModule, String controlType,
                                         String relatedEntityType, UUID relatedEntityId,
                                         String severity, String description) {
        ControlException e = new ControlException();
        e.tenantId = tenantId;
        e.sourceModule = sourceModule;
        e.controlType = controlType;
        e.relatedEntityType = relatedEntityType;
        e.relatedEntityId = relatedEntityId;
        e.severity = severity != null ? severity : "WARNING";
        e.description = description;
        return e;
    }

    public void resolve(UUID resolvedBy, String resolvedByName, String resolutionNotes) {
        if ("RESOLVED".equals(this.status)) {
            throw new IllegalStateException("This exception is already resolved");
        }
        this.status = "RESOLVED";
        this.resolvedBy = resolvedBy;
        this.resolvedByName = resolvedByName;
        this.resolutionNotes = resolutionNotes;
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void dismiss(UUID dismissedBy, String dismissedByName, String reason) {
        this.status = "DISMISSED";
        this.resolvedBy = dismissedBy;
        this.resolvedByName = dismissedByName;
        this.resolutionNotes = reason;
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}