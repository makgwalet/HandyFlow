package za.co.handyflow.platform.projects.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import za.co.handyflow.platform.projects.application.internal.ProjectAccess;
import za.co.handyflow.platform.projects.application.internal.ProjectAccessGuard;
import za.co.handyflow.platform.projects.application.internal.ProjectScopeResolver;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Optional;
import java.util.UUID;

/**
 * Single choke point for project-level access on every /api/v1/projects/** route.
 *
 * Only active when app.projects.restrict-to-members=true. Runs after the JWT filter
 * (so TenantContext is populated) and before the controller method.
 *
 * DECISIONS
 *  - Not a member            → 404 (same as a missing project; existence not revealed)
 *  - Member but VIEWER writes → 403
 *  - Cost routes (budget, EVM, change orders) need the cost capability → else 403
 *  - Member-management writes need manageMembers → else 403
 *  - PM_ADMIN bypasses (via ProjectAccessGuard)
 *  - Unrecognised route under the prefix → 403 (FAIL CLOSED). When you add a new
 *    controller path, register its keyword in ProjectRoute / ProjectScopeResolver.
 *  - An entity id that doesn't exist (or is in another tenant) passes through so the
 *    controller returns its normal 404.
 *
 * Unauthenticated requests are left to the security filter chain.
 */
@Slf4j
@Component
public class ProjectAccessInterceptor implements HandlerInterceptor {

    private final ProjectAccessGuard   guard;
    private final ProjectScopeResolver resolver;

    /**
     * Both collaborators are @Lazy: this interceptor is part of every @WebMvcTest slice (Spring Boot includes
     * HandlerInterceptor beans) but the guard and resolver are not, so eager injection made every controller slice
     * test fail to load. At runtime they resolve on first use, which is the first project request.
     */
    public ProjectAccessInterceptor(@Lazy ProjectAccessGuard guard, @Lazy ProjectScopeResolver resolver) {
        this.guard = guard;
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!guard.isRestricted()) return true;
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String tenant = TenantContext.getTenantId();
        String user   = TenantContext.getUserId();
        if (tenant == null || user == null) return true;

        ProjectRoute route = ProjectRoute.parse(stripContextPath(request));

        switch (route.kind()) {
            case COLLECTION, SPECIAL -> { return true; }   // filtered inside the service layer
            case UNKNOWN -> {
                log.warn("Unrecognised project route denied in restricted mode: {} {}",
                        request.getMethod(), request.getRequestURI());
                throw ProjectAccessGuard.forbidden("Access denied");
            }
            default -> { /* PROJECT_SCOPED / ENTITY_SCOPED handled below */ }
        }

        UUID tenantId = UUID.fromString(tenant);
        UUID userId   = UUID.fromString(user);

        UUID projectId = route.projectId();
        if (projectId == null) {
            Optional<UUID> resolved = resolver.projectIdFor(route.entityKeyword(), route.entityId(), tenantId);
            if (resolved.isEmpty()) return true;           // nonexistent → controller's own 404
            projectId = resolved.get();
        }

        ProjectAccess access = guard.accessFor(tenantId, userId, projectId, guard.isAdmin());
        if (!access.view()) throw ProjectAccessGuard.hidden();

        boolean read = isRead(request.getMethod());
        if (route.membersDomain() && !read && !access.manageMembers()) {
            throw ProjectAccessGuard.forbidden("Only a project manager can change project members");
        }
        if (!read && !access.write()) {
            throw ProjectAccessGuard.forbidden("Your project role is read-only");
        }
        if (route.costDomain() && !access.cost()) {
            throw ProjectAccessGuard.forbidden("Your project role cannot access cost information");
        }
        return true;
    }

    private static boolean isRead(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private static String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) ? uri.substring(ctx.length()) : uri;
    }
}