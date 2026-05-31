package za.co.handyflow.platform.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import za.co.handyflow.platform.billing.application.BillingFacade;
import za.co.handyflow.platform.billing.domain.model.Subscription;
import za.co.handyflow.platform.billing.domain.repository.SubscriptionRepository;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

@Component
@RequiredArgsConstructor
public class FeatureGuard {

    private final BillingFacade          billingFacade;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Call at the start of any module-gated endpoint.
     * Throws 402 if tenant has no active subscription.
     * Throws 402 with suspension message if SUSPENDED.
     * Throws 403 if the module is not in their plan.
     *
     * WHY allow PAST_DUE through?
     * PAST_DUE = within the 7-day grace period.
     * Tenant still has full access — we just show a warning banner.
     * Only SUSPENDED = hard lock.
     */
    public void requireModule(String moduleKey) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();

        // Check subscription status first
        subscriptionRepository.findByTenantId(tenantId).ifPresent(sub -> {
            if (sub.getStatus().name().equals("SUSPENDED")) {
                throw new ResponseStatusException(
                        HttpStatus.PAYMENT_REQUIRED,
                        "Your account has been suspended due to non-payment. " +
                        "Please contact support or pay your outstanding invoice to restore access.");
            }
            if (sub.getStatus().name().equals("CANCELLED")) {
                throw new ResponseStatusException(
                        HttpStatus.PAYMENT_REQUIRED,
                        "Your HandyFlow subscription has been cancelled.");
            }
        });

        if (!billingFacade.hasActiveSubscription(tenantId)) {
            throw new ResponseStatusException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "Your subscription has expired. Please renew to continue.");
        }

        if (!billingFacade.isModuleActive(tenantId, moduleKey)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The '%s' module is not included in your current plan. " +
                    "Please upgrade to access this feature.".formatted(moduleKey));
        }
    }

    public void requireFeature(String featureKey) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();

        if (!billingFacade.hasActiveSubscription(tenantId)) {
            throw new ResponseStatusException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "Your subscription has expired.");
        }

        if (!billingFacade.isFeatureEnabled(tenantId, featureKey)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Feature '%s' is not available on your current plan.".formatted(featureKey));
        }
    }

    public boolean hasModule(String moduleKey) {
        if (!TenantContext.hasTenant()) return false;
        TenantId tenantId = TenantContext.getTenantIdAsObject();

        // Suspended tenants have no module access
        boolean suspended = subscriptionRepository.findByTenantId(tenantId)
                .map(sub -> sub.getStatus().name().equals("SUSPENDED")
                         || sub.getStatus().name().equals("CANCELLED"))
                .orElse(false);
        if (suspended) return false;

        return billingFacade.hasActiveSubscription(tenantId)
                && billingFacade.isModuleActive(tenantId, moduleKey);
    }
}
