// security/infrastructure/SensitiveReadInterceptor.java
package za.co.handyflow.platform.security.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import za.co.handyflow.platform.security.application.internal.SensitiveReadAuditService;
import za.co.handyflow.platform.security.application.internal.SensitiveReadRules;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Map;
import java.util.UUID;

/**
 * Leaves a trace of who downloaded sensitive evidence, exported payroll or opened vetting (SP-15), without touching any controller.
 *
 * <p>It acts after the request, only when the answer was a 2xx: a refused request ({@code 403}) handed over nothing and is not logged here.
 * Because the file has already been sent by then, a failure to write the trace cannot stop it; it is logged loudly at ERROR instead.
 * (Revealing a full ID or bank number is stricter: that is recorded before the value is returned.)</p>
 */
@Slf4j
@Component
public class SensitiveReadInterceptor implements HandlerInterceptor {

    private final SensitiveReadAuditService audit;

    public SensitiveReadInterceptor(SensitiveReadAuditService audit) { this.audit = audit; }

    @Override
    @SuppressWarnings("unchecked")
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            if (!SensitiveReadRules.delivered(response.getStatus(), ex)) return;
            String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            Map<String, String> vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (!TenantContext.hasTenant()) return;
            UUID tenant = UUID.fromString(TenantContext.getTenantId());
            var target = SensitiveReadRules.classify(request.getMethod(), pattern, vars, tenant);
            if (target.isEmpty()) return;
            boolean support = TenantContext.isImpersonation();
            UUID actor = support ? null : TenantContext.getCurrentUserId();
            audit.record(tenant, actor, target.get(), support);
        } catch (RuntimeException e) {
            log.error("[Security] Could not record a sensitive read for {} {}: {}", request.getMethod(), request.getRequestURI(), e.toString());
        }
    }
}
