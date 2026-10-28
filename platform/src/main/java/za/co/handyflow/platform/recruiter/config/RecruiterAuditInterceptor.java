package za.co.handyflow.platform.recruiter.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterAuditService;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterAuditRules;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Map;
import java.util.UUID;

/**
 * Writes the audit trail for staff requests once they have succeeded. It sees the route that matched and its ids, never the request body, so
 * candidates' details and notes do not get copied into the log.
 */
@Component
@RequiredArgsConstructor
public class RecruiterAuditInterceptor implements HandlerInterceptor {

    static final String PREFIX = "/api/v1/recruiter";
    private final RecruiterAuditService audit;

    @Override
    @SuppressWarnings("unchecked")
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (ex != null || !RecruiterAuditRules.isSuccess(response.getStatus()) || !TenantContext.hasTenant()) return;
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String full) || !full.startsWith(PREFIX)) return;
        String route = full.substring(PREFIX.length());
        String action = RecruiterAuditRules.actionFor(request.getMethod(), route);
        if (action == null) return;

        Map<String, String> vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars == null) vars = Map.of();
        String entityType = RecruiterAuditRules.entityType(route);
        UUID entityId = uuid(first(vars, "id", "appId", "jobId", "poolId", "applicantId"));
        UUID applicationId = route.startsWith("/applications") ? uuid(first(vars, "id", "appId")) : null;
        String detail = detail(vars, request);

        boolean support = TenantContext.isImpersonation();
        UUID actor = support ? null : uuid(TenantContext.getUserId());
        String name = support ? "Support session" : TenantContext.getCurrentUserName();
        audit.record(TenantContext.getTenantIdAsObject(), actor, name, action, entityType, entityId, applicationId, detail);
    }

    /** Route parts other than the main id (an action name, a check id) and the date range of an export. */
    private static String detail(Map<String, String> vars, HttpServletRequest request) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String k = e.getKey();
            if (k.equals("id") || k.equals("appId") || k.equals("jobId") || k.equals("poolId") || k.equals("applicantId")) continue;
            b.append(k).append('=').append(e.getValue()).append(' ');
        }
        String q = request.getQueryString();
        if ("GET".equals(request.getMethod()) && q != null) b.append(q);
        return b.toString();
    }

    private static String first(Map<String, String> vars, String... names) {
        for (String n : names) if (vars.containsKey(n)) return vars.get(n);
        return null;
    }

    private static UUID uuid(String s) {
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
