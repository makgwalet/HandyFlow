package za.co.handyflow.platform.clinic.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import za.co.handyflow.platform.clinic.application.internal.ClinicRestrictedRecordService;
import za.co.handyflow.platform.clinic.application.internal.RestrictedListRedactor;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.Set;
import java.util.UUID;

/**
 * Masks restricted patients in Clinic worklists (results inbox, critical results, drafts, handoff queue, recalls, tasks)
 * for people who cannot open their record (CLINIC-DEC-008). Fails closed: if the check cannot be made the list is refused.
 * With no service bean (slice tests) it does nothing.
 */
@RestControllerAdvice(basePackages = "za.co.handyflow.platform.clinic.api")
public class ClinicRestrictedListAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(ClinicRestrictedListAdvice.class);
    private final ObjectProvider<ClinicRestrictedRecordService> service;
    private final ObjectMapper mapper;

    public ClinicRestrictedListAdvice(ObjectProvider<ClinicRestrictedRecordService> service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) { return true; }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || !(request instanceof ServletServerHttpRequest sr)) return body;
        HttpServletRequest http = sr.getServletRequest();
        if (!RestrictedListRedactor.applies(http.getMethod(), http.getRequestURI())) return body;
        ClinicRestrictedRecordService svc = service.getIfAvailable();
        if (svc == null || !TenantContext.hasTenant()) return body;
        try {
            if (authorities().contains("CLINIC_RESTRICTED_RECORD_ACCESS")) return body;
            JsonNode tree = mapper.valueToTree(body);
            Set<UUID> ids = RestrictedListRedactor.patientIds(tree);
            if (ids.isEmpty()) return body;
            UUID tenant = TenantContext.getTenantIdAsObject().getValue();
            UUID user = currentUser();
            Set<UUID> hidden = svc.hiddenFrom(tenant, user, ids);
            if (hidden.isEmpty()) return body;
            RestrictedListRedactor.redact(tree, hidden);
            return tree;
        } catch (RuntimeException e) {
            log.error("Restricted-list check failed; list refused: {}", e.getMessage());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Could not check whether any records in this list are restricted. Try again.");
        }
    }

    private static Set<String> authorities() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return Set.of();
        return a.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
    }

    private static UUID currentUser() {
        try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }
}
