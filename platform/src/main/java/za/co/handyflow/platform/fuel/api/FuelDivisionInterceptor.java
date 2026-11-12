package za.co.handyflow.platform.fuel.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import za.co.handyflow.platform.fuel.application.internal.FuelDivisionGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelDivisionRules;
import za.co.handyflow.platform.fuel.application.internal.FuelScopeService;
import za.co.handyflow.platform.shared.TenantContext;

/**
 * One choke point that applies the division rules to every /api/v1/fuel route, so a client cannot reach a division the
 * tenant has not activated by calling the API directly. Runs after authentication (TenantContext is set). A request
 * with no tenant is left to the security chain. The customer portal derives its tenant from the access grant, not the
 * user, so it is not covered here (tracked in the gap register).
 */
@Component
public class FuelDivisionInterceptor implements HandlerInterceptor {

    private final FuelDivisionGuard guard;

    private final FuelScopeService scopes;

    /** Lazy so controller slice tests, which do not contain the guard, still load. */
    @Autowired
    public FuelDivisionInterceptor(@Lazy FuelDivisionGuard guard, @Lazy FuelScopeService scopes) { this.guard = guard; this.scopes = scopes; }

    /** Division checks only, with no per-user limits. */
    public FuelDivisionInterceptor(FuelDivisionGuard guard) { this.guard = guard; this.scopes = null; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (TenantContext.getTenantId() == null) return true;
        var tenant = TenantContext.getTenantIdAsObject();
        var path = request.getRequestURI();
        var required = FuelDivisionRules.requirementFor(path);
        if (!required.isEmpty()) guard.require(tenant, required);
        if (scopes != null) {
            // Per-user limits (Phase 8c). Only users the security chain has identified can have limits.
            java.util.UUID user = currentUser();
            if (user != null) {
                scopes.requireDivision(tenant, user, required);
                FuelScopeService.targetOf(path).ifPresent(t -> scopes.requireTarget(tenant, user, t));
            }
        }
        return true;
    }

    private static java.util.UUID currentUser() {
        String id = TenantContext.getUserId();
        if (id == null || TenantContext.isImpersonation()) return null;
        try { return java.util.UUID.fromString(id); } catch (IllegalArgumentException e) { return null; }
    }
}
