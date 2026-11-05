package za.co.handyflow.platform.billing.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Module entitlement for the staff APIs listed in {@link ModuleEntitlementPaths}, in one place, the same way
 * ClinicModuleGuardConfig does for Clinic.
 *
 * Mode is set by handyflow.entitlement.enforce:
 *  false (default) = report only: a request the tenant is not entitled to is logged as a warning and allowed;
 *  true            = enforce: FeatureGuard.requireModule throws 402/403.
 * Modules in ModuleEntitlementPaths.REPORT_ONLY (no catalogue row) are reported even when enforcing.
 * Run in report mode first and check the log for "ENTITLEMENT-REPORT" lines, because trial, internal and demo
 * tenants may use a module without a subscription row and would be locked out by enforcing straight away.
 */
@Slf4j
@Configuration
public class ModuleEntitlementInterceptorConfig implements WebMvcConfigurer {

    private final ObjectProvider<FeatureGuard> featureGuard;
    private final boolean enforce;
    private final ConcurrentHashMap<String, AtomicLong> reported = new ConcurrentHashMap<>();

    public ModuleEntitlementInterceptorConfig(ObjectProvider<FeatureGuard> featureGuard,
                                              @Value("${handyflow.entitlement.enforce:false}") boolean enforce) {
        this.featureGuard = featureGuard;
        this.enforce = enforce;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
                FeatureGuard guard = featureGuard.getIfAvailable();
                if (guard == null || !TenantContext.hasTenant()) return true;
                String key = ModuleEntitlementPaths.moduleFor(request.getRequestURI()).orElse(null);
                if (key == null) return true;
                if (enforce && ModuleEntitlementPaths.enforceable(key)) {
                    guard.requireModule(key);
                } else if (!guard.hasModule(key)) {
                    String id = TenantContext.getTenantIdAsObject().getValue() + "|" + key;
                    long n = reported.computeIfAbsent(id, k -> new AtomicLong()).incrementAndGet();
                    if (n == 1 || n % 100 == 0) {
                        log.warn("ENTITLEMENT-REPORT tenant={} module={} would be refused when enforcement is on "
                                + "(path={}, occurrences={})", TenantContext.getTenantIdAsObject().getValue(),
                                key, request.getRequestURI(), n);
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/v1/**");
    }
}
