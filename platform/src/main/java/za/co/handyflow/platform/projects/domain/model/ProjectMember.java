package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;

import java.time.Instant;
import java.util.UUID;

/** A user's membership (and role) on a single project. Unique per (project, user). */
@Entity
@Table(name = "project_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_name")
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ProjectMemberRole role;

    @Column(name = "added_by")
    private UUID addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public static ProjectMember create(UUID tenantId, UUID projectId, UUID userId,
                                       String userName, ProjectMemberRole role, UUID addedBy) {
        ProjectMember m = new ProjectMember();
        m.id        = UUID.randomUUID();
        m.tenantId  = tenantId;
        m.projectId = projectId;
        m.userId    = userId;
        m.userName  = userName;
        m.role      = role;
        m.addedBy   = addedBy;
        m.addedAt   = Instant.now();
        return m;
    }

    public void changeRole(ProjectMemberRole newRole) { this.role = newRole; }
}