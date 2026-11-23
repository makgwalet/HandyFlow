// shared/ClockConfig.java
package za.co.handyflow.platform.shared;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** One Clock for code that needs "now", so tests can pin it (for example to 23:59 and 00:01). It is UTC: business days come from BusinessCalendar. */
@Configuration
public class ClockConfig {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() { return Clock.systemUTC(); }
}
