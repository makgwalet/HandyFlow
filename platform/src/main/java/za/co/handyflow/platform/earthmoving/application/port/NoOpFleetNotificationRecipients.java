package za.co.handyflow.platform.earthmoving.application.port;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/**
 * Default {@link FleetNotificationRecipients}: logs a warning and returns no
 * one. Active only if no other bean implementing the interface is present —
 * see {@link #fleetNotificationRecipients}. Once you add a real
 * implementation as a {@code @Component} in your Identity module, this
 * backs off automatically; nothing here needs to change.
 */
@Slf4j
@Configuration
public class NoOpFleetNotificationRecipients {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(FleetNotificationRecipients.class)
    public FleetNotificationRecipients fleetNotificationRecipients() {
        return tenantId -> {
            log.warn("No FleetNotificationRecipients implementation is wired up — asset lifecycle " +
                    "notifications (breakdowns, service due, etc.) have no recipients and will not be " +
                    "delivered for tenant={}. Implement za.co.handyflow.platform.earthmoving.application" +
                    ".port.FleetNotificationRecipients in your Identity/Security module to fix this.", tenantId);
            return List.of();
        };
    }
}