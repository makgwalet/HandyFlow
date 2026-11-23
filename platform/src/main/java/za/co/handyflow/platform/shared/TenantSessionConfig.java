package za.co.handyflow.platform.shared;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Switches on company-tagged connections. Off unless handyflow.rls.session-tenant=true, so deploying this changes nothing by itself.
 * Rollout: deploy with it off; turn it on in staging and read the status page; then production; and only after that run
 * SELECT hf_rls_set_strict(true) in the database (see V866).
 */
@Configuration
@ConditionalOnProperty(name = "handyflow.rls.session-tenant", havingValue = "true")
public class TenantSessionConfig {

    /** Wraps the application's data source once. Static so it is created early without dragging the configuration class along. */
    @Bean
    static BeanPostProcessor tenantSessionDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return wrap(bean);
            }
        };
    }

    static Object wrap(Object bean) {
        if (bean instanceof DataSource ds && !(bean instanceof TenantSessionDataSource)) return new TenantSessionDataSource(ds);
        return bean;
    }
}
