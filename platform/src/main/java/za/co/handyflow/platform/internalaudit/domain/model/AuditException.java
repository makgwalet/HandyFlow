package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * An issue raised from a failed/exceptional AuditTest. severity here is
 * deliberately its own field, never derived from or conflated with the
 * engagement's risk level or the sampling plan's materiality — per the
 * product owner's own hard constraint and example: "a fraudulent
 * payment can be high risk" even when its Rand value is immaterial.
 * Named AuditException (not just Exception) to avoid any confusion
 * with java.lang.Exception.
 * <p>
 * promotedToFindingId is nullable and unused until Phase 4's
 * AuditFinding exists — status can move to PROMOTED_TO_FINDING before
 * that entity is built, but the actual linkage only becomes meaningful
 * once Phase 4 ships.
 */
@Entity
@Table(name = "audit_exceptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditException {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "audit_test_id", nullable = false) private UUID auditTestId;
    @Column(name = "description", nullable = false) private String description;
    @Column(name = "severity", nullable = false) private String severity; // LOW | MEDIUM | HIGH | CRITICAL
    @Column(name = "status", nullable = false) private String status = "OPEN"; // OPEN | DISMISSED | PROMOTED_TO_FINDING
    @Column(name = "promoted_to_finding_id") private UUID promotedToFindingId;
    @Column(name = "raised_by") private UUID raisedBy;
    @Column(name = "raised_at", nullable = false, updatable = false) private Instant raisedAt;
    @Column(name = "resolution_notes") private String resolutionNotes;

    public static AuditException raise(UUID tenantId, UUID auditTestId, String description,
                                       String severity, UUID raisedBy) {
        AuditException e = new AuditException();
        e.tenantId = tenantId;
        e.auditTestId = auditTestId;
        e.description = description;
        e.severity = severity;
        e.raisedBy = raisedBy;
        e.raisedAt = Instant.now();
        return e;
    }

    public void dismiss(String resolutionNotes) {
        this.status = "DISMISSED";
        this.resolutionNotes = resolutionNotes;
    }
}
