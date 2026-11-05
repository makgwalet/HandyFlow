package za.co.handyflow.platform.projects.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers ProjectAccessInterceptor on every project route.
 *
 * The interceptor is taken through an ObjectProvider, like the Training, Recruiter, Accountant, Clinic and entitlement
 * configurations. A WebMvcConfigurer is part of every @WebMvcTest slice but the interceptor (a plain @Component) is not,
 * so a direct constructor dependency made every controller slice test fail to load its application context.
 */
@Configuration
@RequiredArgsConstructor
public class ProjectAccessWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<ProjectAccessInterceptor> interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        ProjectAccessInterceptor i = interceptor.getIfAvailable();
        if (i == null) return;
        registry.addInterceptor(i)
                .addPathPatterns("/api/v1/projects", "/api/v1/projects/**");
    }
}
