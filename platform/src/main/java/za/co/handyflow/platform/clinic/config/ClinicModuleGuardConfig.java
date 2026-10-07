package za.co.handyflow.platform.clinic.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.clinic.application.internal.ClinicAccessLogService;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

/**
 * Module entitlement for the whole Clinic API in one place (audit P0: the clinic
 * controllers never called FeatureGuard.requireModule, so any tenant could use them).
 *
 * A single interceptor on /api/v1/clinic/** keeps the six controllers untouched.
 * Unauthenticated/tenant-less requests are left to Spring Security; the guard only
 * runs once a tenant is resolved. If no FeatureGuard bean exists (slice tests) it is skipped.
 */
@Configuration
public class ClinicModuleGuardConfig implements WebMvcConfigurer {

    static final String MODULE_KEY = "clinic";

    private final ObjectProvider<FeatureGuard> featureGuard;
    private final ObjectProvider<ClinicAccessLogService> accessLog;

    public ClinicModuleGuardConfig(ObjectProvider<FeatureGuard> featureGuard,
                                   ObjectProvider<ClinicAccessLogService> accessLog) {
        this.featureGuard = featureGuard;
        this.accessLog = accessLog;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
                FeatureGuard guard = featureGuard.getIfAvailable();
                if (guard != null && TenantContext.hasTenant()) {
                    guard.requireModule(MODULE_KEY);   // throws 402/403 ResponseStatusException
                }
                return true;
            }
        }).addPathPatterns("/api/v1/clinic/**");

        // S1-6: record who read which patient record (successful GETs only).
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                        Object handler, Exception ex) {
                if (!"GET".equalsIgnoreCase(request.getMethod()) || response.getStatus() >= 400) return;
                ClinicAccessLogService svc = accessLog.getIfAvailable();
                if (svc == null || !TenantContext.hasTenant()) return;
                String path = request.getRequestURI();
                if (path.contains("/access-log")) return;
                ClinicAccessLogService.Target t = ClinicAccessLogService.classify(path);
                if (t == null) return;
                UUID user;
                try { user = UserContext.getCurrentUserId(); } catch (RuntimeException e) { user = null; }
                svc.record(TenantContext.getTenantIdAsObject().getValue(), user, t, "GET", path,
                        response.getStatus(), request.getRemoteAddr(), TenantContext.isImpersonation());
            }
        }).addPathPatterns("/api/v1/clinic/**");
    }
}
