package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit record of a membership change. No setters, no update path. */
@Entity
@Table(name = "project_member_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMemberEvent {

    public static final String ADDED        = "ADDED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String REMOVED      = "REMOVED";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_role")
    private ProjectMemberRole oldRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_role")
    private ProjectMemberRole newRole;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static ProjectMemberEvent record(UUID tenantId, UUID projectId, UUID userId, String action,
                                            ProjectMemberRole oldRole, ProjectMemberRole newRole,
                                            UUID actorId, String actorName) {
        ProjectMemberEvent e = new ProjectMemberEvent();
        e.id         = UUID.randomUUID();
        e.tenantId   = tenantId;
        e.projectId  = projectId;
        e.userId     = userId;
        e.action     = action;
        e.oldRole    = oldRole;
        e.newRole    = newRole;
        e.actorId    = actorId;
        e.actorName  = actorName;
        e.occurredAt = Instant.now();
        return e;
    }
}