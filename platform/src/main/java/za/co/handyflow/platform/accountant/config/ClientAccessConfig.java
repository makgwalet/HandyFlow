package za.co.handyflow.platform.accountant.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import za.co.handyflow.platform.accountant.application.internal.ClientAccessService;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-level access for the whole practice API in one place (finance patch F15). Every URL of the form
 * {@code /api/v1/accountant/clients/{id}/...} is refused unless the signed-in person holds ACCOUNTANT_ALL_CLIENTS or is
 * assigned to that client. Lists and endpoints that name a client some other way are checked in the service.
 * The client portal (a different URL tree) is not affected.
 */
@Configuration
public class ClientAccessConfig implements WebMvcConfigurer {

    private static final Pattern CLIENT_URL = Pattern.compile("^/api/v1/accountant/clients/([0-9a-fA-F-]{36})(?:/.*)?$");

    private final ObjectProvider<ClientAccessService> access;

    public ClientAccessConfig(ObjectProvider<ClientAccessService> access) {
        this.access = access;
    }

    /** The client id in a practice URL, or null. */
    public static UUID clientIdIn(String path) {
        Matcher m = CLIENT_URL.matcher(path);
        if (!m.matches()) return null;
        try { return UUID.fromString(m.group(1)); } catch (IllegalArgumentException e) { return null; }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
                ClientAccessService svc = access.getIfAvailable();
                UUID clientId = clientIdIn(request.getRequestURI());
                if (svc == null || clientId == null || !TenantContext.hasTenant()) return true;
                svc.require(TenantContext.getTenantIdAsObject(), clientId);
                return true;
            }
        }).addPathPatterns("/api/v1/accountant/clients/**");
    }
}
