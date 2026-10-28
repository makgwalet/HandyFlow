package za.co.handyflow.platform.training.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.training.application.internal.TrainingAuditService;
import za.co.handyflow.platform.training.domain.model.TrainingAuditRules;

import java.util.Map;
import java.util.UUID;

/**
 * Writes the audit trail for Training requests once they have succeeded. It sees the route that matched and its ids, never the request body, so
 * employees' results and notes do not get copied into the log. The service is looked up lazily so a controller slice test needs no audit bean.
 */
@Component
@RequiredArgsConstructor
public class TrainingAuditInterceptor implements HandlerInterceptor {

    static final String PREFIX = "/api/v1/training";
    private final ObjectProvider<TrainingAuditService> audit;

    @Override
    @SuppressWarnings("unchecked")
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (ex != null || !TrainingAuditRules.isSuccess(response.getStatus()) || !TenantContext.hasTenant()) return;
        TrainingAuditService service = audit.getIfAvailable();
        if (service == null) return;
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String full) || !full.startsWith(PREFIX)) return;
        String route = full.substring(PREFIX.length());
        String action = TrainingAuditRules.actionFor(request.getMethod(), route);
        if (action == null) return;

        Map<String, String> vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars == null) vars = Map.of();
        UUID entityId = uuid(first(vars, "id", "sessionId", "enrollmentId"));
        String q = request.getQueryString();
        String detail = "GET".equals(request.getMethod()) && q != null ? q : null;

        boolean support = TenantContext.isImpersonation();
        UUID actor = support ? null : uuid(TenantContext.getUserId());
        String name = support ? "Support session" : TenantContext.getCurrentUserName();
        service.record(TenantContext.getTenantIdAsObject(), actor, name, action, TrainingAuditRules.entityType(route), entityId, detail);
    }

    private static String first(Map<String, String> vars, String... names) {
        for (String n : names) if (vars.containsKey(n)) return vars.get(n);
        return null;
    }

    private static UUID uuid(String s) {
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
