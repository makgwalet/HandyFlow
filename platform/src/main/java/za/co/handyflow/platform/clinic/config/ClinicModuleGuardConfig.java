package za.co.handyflow.platform.clinic.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.TenantContext;

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

    public ClinicModuleGuardConfig(ObjectProvider<FeatureGuard> featureGuard) {
        this.featureGuard = featureGuard;
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
    }
}
