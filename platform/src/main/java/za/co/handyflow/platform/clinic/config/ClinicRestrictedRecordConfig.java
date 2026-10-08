package za.co.handyflow.platform.clinic.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
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

/** Registers the restricted-record interceptor on the whole Clinic API. */
@Configuration
public class ClinicRestrictedRecordConfig implements WebMvcConfigurer {

    private final ObjectProvider<ClinicRestrictedRecordService> service;

    public ClinicRestrictedRecordConfig(ObjectProvider<ClinicRestrictedRecordService> service) { this.service = service; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ClinicRestrictedRecordInterceptor(service)).addPathPatterns("/api/v1/clinic/**");
    }
}
