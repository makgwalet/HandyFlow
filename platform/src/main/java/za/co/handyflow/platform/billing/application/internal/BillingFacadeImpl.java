package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.billing.application.BillingFacade;
import za.co.handyflow.platform.shared.TenantId;

@Slf4j
@Service
@RequiredArgsConstructor
class BillingFacadeImpl implements BillingFacade {

    private final EntitlementService entitlementService;
    @Override
    public boolean hasActiveSubscription(TenantId tenantId) {
        return entitlementService.hasActiveSubscription(tenantId);
    }


    @Override
    public boolean isModuleActive(TenantId tenantId, String moduleKey) {
        return entitlementService.isModuleActive(tenantId, moduleKey);
    }

    @Override
    public boolean isFeatureEnabled(TenantId tenantId, String featureKey) {
        return entitlementService.isFeatureEnabled(tenantId, featureKey);
    }
}