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

    /**
     * This listener is the CORRECT half of an intentionally-accepted
     * two-way coupling with identity — see
     * identity.application.internal.AuthService.subscriptionQueryFacade's
     * Javadoc for the full reasoning (HandyFlow BOS Discovery doc, Section
     * 31.3, Q19). This direction (billing reacting to identity's
     * TenantCreatedEvent) is the architecturally sound one; the other
     * direction (AuthService calling billing.SubscriptionQueryFacade
     * directly) is the one that closes the cycle. Both are kept
     * deliberately — do not remove this listener as a "fix" for the cycle;
     * removing it would break new-tenant subscription seeding without
     * actually resolving anything, since the cycle is caused by the other
     * direction.
     */
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
            moduleService.activateModules(event.tenantId(), event.moduleKeys(), 60, null);
        }
    }
}