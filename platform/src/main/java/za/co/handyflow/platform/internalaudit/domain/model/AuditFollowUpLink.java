package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Marks an engagement as a follow-up of an earlier one. */
@Entity(name = "AuditFollowUpLink")
@Table(name = "audit_followup_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditFollowUpLink {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "prior_engagement_id", nullable = false) private UUID priorEngagementId;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public static AuditFollowUpLink of(UUID tenantId, UUID engagementId, UUID priorEngagementId, UUID by) {
        if (engagementId.equals(priorEngagementId)) throw new IllegalArgumentException("An engagement cannot follow up itself");
        AuditFollowUpLink l = new AuditFollowUpLink();
        l.tenantId = tenantId; l.engagementId = engagementId; l.priorEngagementId = priorEngagementId; l.createdBy = by;
        return l;
    }
}
