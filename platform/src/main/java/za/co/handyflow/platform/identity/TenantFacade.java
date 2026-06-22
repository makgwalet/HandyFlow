package za.co.handyflow.platform.identity;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;

public interface TenantFacade {
    Optional<TenantDetails> findTenantDetails(TenantId tenantId);
    /**
     * Returns all tenants with active subscriptions.
     * Used by scheduled jobs (VAT reminders, AR alerts) to notify all tenants.
     */
    List<TenantDetails> findAllActive();

}
