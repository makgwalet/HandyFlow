package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.billing.domain.model.ModuleSubscription;
import za.co.handyflow.platform.billing.domain.model.Subscription;
import za.co.handyflow.platform.billing.domain.repository.ModuleSubscriptionRepository;
import za.co.handyflow.platform.billing.domain.repository.SubscriptionRepository;
import za.co.handyflow.platform.billing.domain.repository.TenantModuleRepository;
import za.co.handyflow.platform.shared.TenantId;

@Slf4j
@Service
@RequiredArgsConstructor
class EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final ModuleSubscriptionRepository moduleSubscriptionRepository;
    // FIX: EntitlementService.isModuleActive() previously checked only the
    // plan's bundled features/plan_modules and moduleSubscriptionRepository
    // (table module_subscriptions) — but ModuleService.activateModule(), the
    // only code path behind both the self-service POST
    // /api/v1/billing/modules/activate endpoint AND the onboarding
    // TenantCreatedEvent handler (BillingEventHandlers), writes exclusively
    // to tenant_modules via TenantModuleRepository. Confirmed by search: no
    // code anywhere in this codebase ever saves a ModuleSubscription row —
    // module_subscriptions is effectively dead. The practical effect: any
    // module a tenant gets access to purely via activation (not bundled
    // into their plan) shows as ACTIVE/accessible on GET .../modules/mine
    // (which reads tenant_modules correctly) while every real endpoint for
    // that module still 403s here, forever — the write path and the read
    // path never agreed. TenantModuleRepository.hasAccess() already existed
    // with exactly the right TRIAL/ACTIVE/grace-period logic; it just was
    // never wired into this check. Added as an additional source of truth
    // rather than replacing the moduleSubscriptionRepository check, in case
    // that table is populated by some other path outside this synced
    // source tree — but tenant_modules is the one every confirmed
    // activation flow in this codebase actually writes to.
    private final TenantModuleRepository tenantModuleRepository;

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

        // FIX: the real per-tenant activation table (see field comment above).
        if (tenantModuleRepository.hasAccess(tenantId.getValue(), moduleKey)) return true;

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