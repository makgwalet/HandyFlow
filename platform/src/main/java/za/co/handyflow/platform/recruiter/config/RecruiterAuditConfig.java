package za.co.handyflow.platform.recruiter.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Staff routes only: the public careers and applicant-portal routes have no signed-in user to attribute anything to. */
@Configuration
@RequiredArgsConstructor
public class RecruiterAuditConfig implements WebMvcConfigurer {

    private final RecruiterAuditInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/v1/recruiter/**")
                .excludePathPatterns("/api/v1/recruiter/careers/**", "/api/v1/recruiter/portal/**");
    }
}
