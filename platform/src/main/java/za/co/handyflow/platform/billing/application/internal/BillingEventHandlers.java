package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantCreatedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
class BillingEventHandlers {

    private final SubscriptionService subscriptionService;
    private final ModuleService       moduleService;

    @ApplicationModuleListener
    void onTenantCreated(TenantCreatedEvent event) {
        log.info("Billing: Processing TenantCreatedEvent for tenant={}",
                event.tenantId());

        subscriptionService.createTrialSubscription(
                event.tenantId(),
                event.ownerEmail()
        );

        if (event.moduleKeys() != null && !event.moduleKeys().isEmpty()) {
            log.info("Billing: Activating {} modules for tenant={}",
                event.moduleKeys().size(), event.tenantId());
            moduleService.activateModules(event.tenantId(), event.moduleKeys(), 60);
        }
    }
}