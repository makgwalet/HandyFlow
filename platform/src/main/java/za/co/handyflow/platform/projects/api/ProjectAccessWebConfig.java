package za.co.handyflow.platform.projects.api;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers ProjectAccessInterceptor on every project route. */
@Configuration
@RequiredArgsConstructor
public class ProjectAccessWebConfig implements WebMvcConfigurer {

    private final ProjectAccessInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/v1/projects", "/api/v1/projects/**");
    }
}