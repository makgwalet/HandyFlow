package za.co.handyflow.platform.clinic.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import za.co.handyflow.platform.clinic.application.internal.ClinicRestrictedRecordService;
import za.co.handyflow.platform.clinic.application.internal.RestrictedRecordRules;
import za.co.handyflow.platform.clinic.application.internal.RestrictedRecordRules.Guard;
import za.co.handyflow.platform.clinic.application.internal.RestrictedRecordRules.Outcome;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enforces restricted patient records for every Clinic request in one place (CLINIC-DEC-008, 009): a request that reaches
 * the clinical record of a restricted patient is refused with 403 {@code RESTRICTED_RECORD} unless the user holds standing
 * access or has an unexpired break-glass session. Under break-glass each request is audited, and documents need the
 * print/export permission. Fails closed: if the check cannot be made, the request is refused (503).
 * If no service bean exists (slice tests) the interceptor does nothing.
 */
public class ClinicRestrictedRecordInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ClinicRestrictedRecordInterceptor.class);
    private final ObjectProvider<ClinicRestrictedRecordService> service;

    public ClinicRestrictedRecordInterceptor(ObjectProvider<ClinicRestrictedRecordService> service) { this.service = service; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        ClinicRestrictedRecordService svc = service.getIfAvailable();
        if (svc == null || !TenantContext.hasTenant()) return true;
        String path = request.getRequestURI();
        Guard guard = RestrictedRecordRules.classify(path);
        if (guard == null) return true;
        try {
            UUID tenant = TenantContext.getTenantIdAsObject().getValue();
            Optional<UUID> patient = svc.patientOf(tenant, guard);
            if (patient.isEmpty()) return true;                     // unknown resource: the controller answers 404
            var restriction = svc.restriction(tenant, patient.get());
            if (restriction.isEmpty()) return true;

            Set<String> authorities = authorities();
            UUID user = currentUser();
            boolean standing = authorities.contains("CLINIC_RESTRICTED_RECORD_ACCESS");
            Optional<UUID> session = user == null ? Optional.empty() : svc.activeSession(tenant, patient.get(), user);
            boolean document = RestrictedRecordRules.isDocument(path);
            boolean mayDocument = authorities.contains("CLINIC_BREAK_GLASS_PRINT") || authorities.contains("CLINIC_BREAK_GLASS_EXPORT");
            Outcome outcome = RestrictedRecordRules.decide(true, standing, session.isPresent(), document, mayDocument);
            switch (outcome) {
                case ALLOW -> { return true; }
                case ALLOW_VIEW_UNDER_BREAK_GLASS -> {
                    svc.audit(tenant, session.get(), patient.get(), user, RestrictedRecordRules.VIEWED, guard.kind().name(), guard.id(), path);
                    return true;
                }
                case ALLOW_DOCUMENT_UNDER_BREAK_GLASS -> {
                    svc.audit(tenant, session.get(), patient.get(), user, RestrictedRecordRules.EXPORTED, guard.kind().name(), guard.id(), path);
                    return true;
                }
                case DENY_DOCUMENT_NOT_PERMITTED -> {
                    deny(response, "You do not have permission to print or export documents from a restricted record.",
                            "RESTRICTED_DOCUMENT", restriction.get().category(), patient.get());
                    return false;
                }
                default -> {
                    deny(response, "This record is restricted. Break the glass with a reason to open it; the practice is alerted.",
                            "RESTRICTED_RECORD", restriction.get().category(), patient.get());
                    return false;
                }
            }
        } catch (RuntimeException e) {
            log.error("Restricted-record check failed; request refused: {}", e.getMessage());
            response.setStatus(503);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Could not check whether this record is restricted. Try again.\"}");
            return false;
        }
    }

    private static Set<String> authorities() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return Set.of();
        return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static UUID currentUser() {
        try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }

    private static void deny(HttpServletResponse response, String message, String code, String category, UUID patientId) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"data\":{\"code\":\"" + code
                + "\",\"category\":\"" + category + "\",\"patientId\":\"" + patientId + "\"}}");
    }
}
