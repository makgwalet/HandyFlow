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
 * Engagement-scoped audit responsibility — deliberately separate from
 * system permissions (AUDIT_READ/AUDIT_MANAGE/AUDIT_ADMIN), per the
 * product owner's own explicit warning against confusing the two. A
 * person may hold AUDIT_WORKPAPER_EDIT tenant-wide as a system
 * permission, but be Lead Auditor on one engagement and Reviewer on a
 * completely different one — role here is a per-engagement fact, not
 * something that can live on a tenant-wide role. Segregation of duties
 * (a reviewer can't also be the preparer on the same workpaper) is
 * enforced in later phases by checking THIS table at the point of each
 * action, not by the system permission alone.
 */
@Entity
@Table(name = "audit_engagement_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngagementAssignment {

    public enum Role {
        HEAD_OF_INTERNAL_AUDIT, AUDIT_MANAGER, SENIOR_AUDITOR, AUDITOR, AUDIT_REVIEWER
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "role", nullable = false) private String role;
    @Column(name = "assigned_by") private UUID assignedBy;
    @Column(name = "assigned_at", nullable = false, updatable = false) private Instant assignedAt;

    public static EngagementAssignment create(UUID tenantId, UUID engagementId, UUID userId,
                                              Role role, UUID assignedBy) {
        EngagementAssignment a = new EngagementAssignment();
        a.tenantId = tenantId;
        a.engagementId = engagementId;
        a.userId = userId;
        a.role = role.name();
        a.assignedBy = assignedBy;
        a.assignedAt = Instant.now();
        return a;
    }
}
