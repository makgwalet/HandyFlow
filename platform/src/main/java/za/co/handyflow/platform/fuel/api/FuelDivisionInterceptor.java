package za.co.handyflow.platform.fuel.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import za.co.handyflow.platform.fuel.application.internal.FuelDivisionGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelDivisionRules;
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

    /** Lazy so controller slice tests, which do not contain the guard, still load. */
    public FuelDivisionInterceptor(@Lazy FuelDivisionGuard guard) { this.guard = guard; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (TenantContext.getTenantId() == null) return true;
        var required = FuelDivisionRules.requirementFor(request.getRequestURI());
        if (required.isEmpty()) return true;
        guard.require(TenantContext.getTenantIdAsObject(), required);
        return true;
    }
}
