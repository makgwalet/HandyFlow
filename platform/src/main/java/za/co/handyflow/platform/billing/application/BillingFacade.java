package za.co.handyflow.platform.billing.application;

import za.co.handyflow.platform.shared.TenantId;

/**
 * Public contract for the Billing module.
 * Other modules use this interface — never the internals.
 * Full implementation comes in Billing module phase.
 */
public interface BillingFacade {

    boolean hasActiveSubscription(TenantId tenantId);
    boolean isModuleActive(TenantId tenantId, String moduleKey);
    boolean isFeatureEnabled(TenantId tenantId, String featureKey);
}
