package za.co.handyflow.platform.projects.domain.enums;

/**
 * A person's role ON ONE PROJECT (not to be confused with tenant-wide authorities
 * such as PM_READ / PM_WRITE, which still apply on top).
 *
 *  role              view  write  cost  manage members
 *  SPONSOR            ✓     ✓      ✓
 *  MANAGER            ✓     ✓      ✓       ✓
 *  PLANNER            ✓     ✓
 *  COST_CONTROLLER    ✓     ✓      ✓
 *  SITE_SUPERVISOR    ✓     ✓
 *  MEMBER             ✓     ✓
 *  VIEWER             ✓
 *
 * "cost" = budget lines, EVM, change orders, and cost/rate fields on projects,
 * tasks and resources. SPONSOR can write because approving is a POST (the
 * method-level PM_APPROVE authority is what actually gates approvals).
 */
public enum ProjectMemberRole {
    SPONSOR, MANAGER, PLANNER, COST_CONTROLLER, SITE_SUPERVISOR, MEMBER, VIEWER;

    public boolean canWrite()         { return this != VIEWER; }
    public boolean canViewCost()      { return this == SPONSOR || this == MANAGER || this == COST_CONTROLLER; }
    public boolean canManageMembers() { return this == MANAGER; }
}