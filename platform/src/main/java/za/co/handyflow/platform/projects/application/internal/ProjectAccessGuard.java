package za.co.handyflow.platform.projects.application.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.projects.domain.model.ProjectMember;
import za.co.handyflow.platform.projects.domain.repository.ProjectMemberRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decides what the current user can do on a project.
 *
 * MODES (app.projects.restrict-to-members, default false):
 *  - false: everyone with the right tenant-wide authority has FULL access, exactly
 *    as before this chunk. Membership rows are still recorded.
 *  - true:  access needs a membership row (role → capabilities), except that users
 *    holding PM_ADMIN bypass (so a tenant can never lock itself out).
 *
 * Fail-safe: in restricted mode, no resolvable user → NONE.
 */
@Component
public class ProjectAccessGuard {

    public static final String ADMIN_AUTHORITY = "PM_ADMIN";

    private final ProjectMemberRepository memberRepo;
    private final boolean restricted;

    public ProjectAccessGuard(ProjectMemberRepository memberRepo,
                              @Value("${app.projects.restrict-to-members:false}") boolean restricted) {
        this.memberRepo = memberRepo;
        this.restricted = restricted;
    }

    public boolean isRestricted() { return restricted; }

    /** True when the current authenticated user holds PM_ADMIN. */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
    }

    // ── Single project ────────────────────────────────────────────────────────

    /** Access for the CURRENT user (from TenantContext / SecurityContext). */
    public ProjectAccess accessFor(UUID tenantId, UUID projectId) {
        if (!restricted) return ProjectAccess.FULL;
        UUID userId = currentUserIdOrNull();
        if (userId == null) return ProjectAccess.NONE;
        return accessFor(tenantId, userId, projectId, isAdmin());
    }

    /** Explicit-user variant (used by the interceptor and by tests). */
    public ProjectAccess accessFor(UUID tenantId, UUID userId, UUID projectId, boolean admin) {
        if (!restricted || admin) return ProjectAccess.FULL;
        return memberRepo.findByProjectAndUser(tenantId, projectId, userId)
                .map(m -> ProjectAccess.of(m.getRole()))
                .orElse(ProjectAccess.NONE);
    }

    // ── Many projects (list pages) — one query, not N ─────────────────────────

    public Map<UUID, ProjectAccess> accessFor(UUID tenantId, Collection<UUID> projectIds) {
        Map<UUID, ProjectAccess> result = new HashMap<>();
        UUID userId = currentUserIdOrNull();
        boolean full = !restricted || isAdmin();
        if (full || projectIds.isEmpty()) {
            projectIds.forEach(id -> result.put(id, full ? ProjectAccess.FULL : ProjectAccess.NONE));
            return result;
        }
        projectIds.forEach(id -> result.put(id, ProjectAccess.NONE));
        if (userId != null) {
            for (ProjectMember m : memberRepo.findByUserAndProjects(tenantId, userId, projectIds)) {
                result.put(m.getProjectId(), ProjectAccess.of(m.getRole()));
            }
        }
        return result;
    }

    // ── Enforcement helpers ───────────────────────────────────────────────────

    public void requireView(UUID tenantId, UUID projectId) {
        if (!accessFor(tenantId, projectId).view()) throw hidden();
    }

    public void requireManageMembers(UUID tenantId, UUID projectId) {
        ProjectAccess a = accessFor(tenantId, projectId);
        if (!a.view()) throw hidden();
        if (!a.manageMembers()) throw forbidden("Only a project manager can change project members");
    }

    // ── Scoping for tenant-wide queries ───────────────────────────────────────

    /**
     * Returns the user id to scope list/dashboard queries to, or null when no
     * scoping applies (unrestricted mode, or PM_ADMIN). In restricted mode with
     * no resolvable user this throws rather than silently returning everything.
     */
    public UUID memberScopeUserId() {
        if (!restricted || isAdmin()) return null;
        UUID userId = currentUserIdOrNull();
        if (userId == null) throw forbidden("Access denied");
        return userId;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static UUID currentUserIdOrNull() {
        String id = TenantContext.getUserId();
        if (id == null) return null;
        try { return UUID.fromString(id); } catch (IllegalArgumentException e) { return null; }
    }

    /** Non-members get the same 404 as a project that doesn't exist — existence isn't revealed. */
    public static HandyFlowException hidden() {
        return new HandyFlowException("Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public static HandyFlowException forbidden(String message) {
        return new HandyFlowException(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
}