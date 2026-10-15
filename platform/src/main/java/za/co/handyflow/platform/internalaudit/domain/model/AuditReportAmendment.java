package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** The Head of Internal Audit reopening an engagement after its report went for sign-off, and why. Open until the report is resubmitted. */
@Entity(name = "AuditReportAmendment")
@Table(name = "audit_report_amendments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditReportAmendment {
    public static final int MIN_REASON = 10;

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT") private String reason;
    @Column(name = "amended_by", nullable = false) private UUID amendedBy;
    @Column(name = "amended_at", nullable = false) private Instant amendedAt = Instant.now();
    @Column(name = "resubmitted_at") private Instant resubmittedAt;

    public static AuditReportAmendment open(UUID tenantId, UUID engagementId, String reason, UUID by) {
        if (reason == null || reason.trim().length() < MIN_REASON) {
            throw new IllegalArgumentException("Say why the report is being reopened (at least " + MIN_REASON + " characters)");
        }
        AuditReportAmendment a = new AuditReportAmendment();
        a.tenantId = tenantId; a.engagementId = engagementId; a.reason = reason.trim(); a.amendedBy = by;
        return a;
    }

    public void markResubmitted() { this.resubmittedAt = Instant.now(); }
}
