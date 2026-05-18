package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.billing.domain.model.ModuleSubscription;
import za.co.handyflow.platform.billing.domain.model.Subscription;
import za.co.handyflow.platform.billing.domain.repository.ModuleSubscriptionRepository;
import za.co.handyflow.platform.billing.domain.repository.SubscriptionRepository;
import za.co.handyflow.platform.shared.TenantId;

@Slf4j
@Service
@RequiredArgsConstructor
class EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final ModuleSubscriptionRepository moduleSubscriptionRepository;

    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(TenantId tenantId){
        return subscriptionRepository.findByTenantId(tenantId)
                .map(Subscription::isAccessible)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isModuleActive(TenantId tenantId, String moduleKey) {
        Subscription sub = subscriptionRepository
                .findByTenantId(tenantId)
                .orElse(null);

        if (sub == null || !sub.isAccessible()) return false;

        // WHY check features JSONB first?
        // Core modules (crm, invoicing) are defined in the plan's
        // features JSON as boolean flags — not in plan_modules table.
        // Industry modules (security, clinic) are in plan_modules.
        Object featureValue = sub.getPlan().getFeatureValue(moduleKey);
        if (featureValue instanceof Boolean b && b) return true;

        // Then check industry module subscriptions
        if (sub.getPlan().includesModule(moduleKey)) return true;

        return moduleSubscriptionRepository
                .existsByTenantIdAndModuleKeyAndStatus(
                        tenantId, moduleKey,
                        ModuleSubscription.ModuleStatus.ACTIVE
                );
    }

    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(TenantId tenantId, String featureKey) {
        return subscriptionRepository.findByTenantId(tenantId)
                .filter(Subscription::isAccessible)
                .map(sub -> sub.getPlan().hasFeature(featureKey))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Object getFeatureValue(TenantId tenantId, String featureKey) {
        return subscriptionRepository.findByTenantId(tenantId)
                .filter(Subscription::isAccessible)
                .map(sub -> sub.getPlan().getFeatureValue(featureKey))
                .orElse(null);
    }
}
