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

    /**
     * WHY @ApplicationModuleListener?
     *
     * This is Spring Modulith's @EventListener — but with superpowers:
     * 1. It runs AFTER the publishing transaction commits (no rollback issues)
     * 2. It's logged in Spring Modulith's event publication table
     * 3. If this listener fails, it can be retried automatically
     * 4. Spring Modulith verifies this cross-module dependency in architecture tests
     *
     * The billing module LISTENS to identity events — it never calls identity directly.
     * This is the "Tell Don't Ask" principle at the module level.
     */
    @ApplicationModuleListener
    void onTenantCreated(TenantCreatedEvent event) {
        log.info("Billing: Processing TenantCreatedEvent for tenant={}",
                event.tenantId());

        subscriptionService.createTrialSubscription(
                event.tenantId(),
                event.ownerEmail()
        );
    }
}
