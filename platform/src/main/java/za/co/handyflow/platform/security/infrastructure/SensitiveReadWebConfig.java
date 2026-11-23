// security/infrastructure/SensitiveReadWebConfig.java
package za.co.handyflow.platform.security.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the sensitive-read trace on the Security routes. Taken through an ObjectProvider so a slice test without it still loads. */
@Configuration
@RequiredArgsConstructor
public class SensitiveReadWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<SensitiveReadInterceptor> interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        SensitiveReadInterceptor i = interceptor.getIfAvailable();
        if (i == null) return;
        registry.addInterceptor(i).addPathPatterns("/api/v1/security/**");
    }
}
