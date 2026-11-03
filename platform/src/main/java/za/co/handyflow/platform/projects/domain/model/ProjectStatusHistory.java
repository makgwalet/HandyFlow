package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of a project status change: who, when, from/to, and why.
 * Deliberately has no setters and no update path — audit rows are never edited.
 */
@Entity
@Table(name = "project_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectStatusHistory {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ProjectStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ProjectStatus toStatus;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "reason")
    private String reason;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_by_name")
    private String changedByName;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public static ProjectStatusHistory record(UUID tenantId, UUID projectId,
                                              ProjectStatus from, ProjectStatus to,
                                              String action, String reason,
                                              UUID changedBy, String changedByName) {
        ProjectStatusHistory h = new ProjectStatusHistory();
        h.id            = UUID.randomUUID();
        h.tenantId      = tenantId;
        h.projectId     = projectId;
        h.fromStatus    = from;
        h.toStatus      = to;
        h.action        = action;
        h.reason        = reason;
        h.changedBy     = changedBy;
        h.changedByName = changedByName;
        h.changedAt     = Instant.now();
        return h;
    }
}