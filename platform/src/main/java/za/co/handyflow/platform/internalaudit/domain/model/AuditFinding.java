package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Deliberately richer than AuditException — see this class's own
 * migration comment (V276) for the fuller reasoning on why
 * ControlException's shape was confirmed insufficient for a real
 * finding on its own, back in the original design proposal. severity
 * is its own field, same hard constraint as AuditException — never
 * derived from the engagement's risk level or the sampling plan's
 * materiality.
 */
@Entity
@Table(name = "audit_findings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditFinding {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "source_exception_id") private UUID sourceExceptionId;
    @Column(name = "title", nullable = false) private String title;
    @Column(name = "description", nullable = false) private String description;
    @Column(name = "root_cause") private String rootCause;
    @Column(name = "recommendation") private String recommendation;
    @Column(name = "management_response") private String managementResponse;
    @Column(name = "severity", nullable = false) private String severity;
    @Column(name = "owner") private UUID owner;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "status", nullable = false) private String status = "OPEN"; // OPEN | IN_PROGRESS | RESOLVED | CLOSED
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    public static AuditFinding create(UUID tenantId, UUID engagementId, UUID sourceExceptionId,
                                      String title, String description, String rootCause, String recommendation,
                                      String severity, UUID owner, LocalDate dueDate, UUID createdBy) {
        AuditFinding f = new AuditFinding();
        f.tenantId = tenantId;
        f.engagementId = engagementId;
        f.sourceExceptionId = sourceExceptionId;
        f.title = title;
        f.description = description;
        f.rootCause = rootCause;
        f.recommendation = recommendation;
        f.severity = severity;
        f.owner = owner;
        f.dueDate = dueDate;
        f.createdBy = createdBy;
        f.createdAt = Instant.now();
        return f;
    }

    public void recordManagementResponse(String response) {
        this.managementResponse = response;
        if ("OPEN".equals(this.status)) {
            this.status = "IN_PROGRESS";
        }
    }

    public void resolve() {
        this.status = "RESOLVED";
        this.resolvedAt = Instant.now();
    }

    public void close() {
        if (!"RESOLVED".equals(status)) {
            throw new IllegalStateException("Only a RESOLVED finding can be closed. Current status: " + status);
        }
        this.status = "CLOSED";
    }

    public void reopen() {
        this.status = "IN_PROGRESS";
        this.resolvedAt = null;
    }
}
