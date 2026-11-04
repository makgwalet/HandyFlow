package za.co.handyflow.platform.projects.application.internal.notify;

import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;

import java.util.Set;
import java.util.UUID;

/**
 * Something happened on a project that people should hear about. Published inside the business transaction and delivered
 * only after it commits (see {@link ProjectNotificationListener}), so a rolled-back change never notifies anyone and a
 * notification failure never rolls back the change.
 *
 * @param users  named people (assignee, owner, originator)
 * @param roles  plus every project member holding one of these roles
 * @param actor  never notified about their own action
 */
public record ProjectEvent(UUID tenantId, UUID projectId, PmEvent event, String title, String message, UUID entityId,
                           Set<UUID> users, Set<ProjectMemberRole> roles, UUID actor) {

    public static ProjectEvent to(UUID tenantId, UUID projectId, PmEvent event, String title, String message, UUID entityId,
                                  UUID user, UUID actor) {
        return new ProjectEvent(tenantId, projectId, event, title, message, entityId, user == null ? Set.of() : Set.of(user), Set.of(), actor);
    }

    public static ProjectEvent toRoles(UUID tenantId, UUID projectId, PmEvent event, String title, String message, UUID entityId,
                                       Set<ProjectMemberRole> roles, UUID actor) {
        return new ProjectEvent(tenantId, projectId, event, title, message, entityId, Set.of(), roles, actor);
    }
}
