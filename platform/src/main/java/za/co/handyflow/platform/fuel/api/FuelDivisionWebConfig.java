package za.co.handyflow.platform.fuel.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class FuelDivisionWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<FuelDivisionInterceptor> interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        FuelDivisionInterceptor i = interceptor.getIfAvailable();
        if (i == null) return;
        registry.addInterceptor(i).addPathPatterns("/api/v1/fuel/**");
    }
}
