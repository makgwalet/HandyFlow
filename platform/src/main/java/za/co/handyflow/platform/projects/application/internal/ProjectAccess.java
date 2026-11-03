package za.co.handyflow.platform.projects.application.internal;

import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;

/** What the current user may do on one project. */
public record ProjectAccess(boolean view, boolean write, boolean cost, boolean manageMembers) {

    public static final ProjectAccess FULL = new ProjectAccess(true, true, true, true);
    public static final ProjectAccess NONE = new ProjectAccess(false, false, false, false);

    public static ProjectAccess of(ProjectMemberRole role) {
        return new ProjectAccess(true, role.canWrite(), role.canViewCost(), role.canManageMembers());
    }
}