package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.billing.domain.model.Plan;
import za.co.handyflow.platform.billing.domain.model.Subscription;
import za.co.handyflow.platform.billing.domain.repository.PlanRepository;
import za.co.handyflow.platform.billing.domain.repository.SubscriptionRepository;
import za.co.handyflow.platform.shared.TenantId;

/**
 * STUB — Full implementation comes when we build the Billing module.
 *
 * WHY STUB AND NOT DELETE?
 * Because the architecture is correct — Billing WILL create a trial
 * subscription when a tenant registers. We're just not ready to build
 * that logic yet. The stub lets the project compile while keeping the
 * design intent visible.
 *
 * This is exactly what senior devs do: design the full picture first,
 * implement iteratively.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    public void createTrialSubscription(TenantId tenantId, String ownerEmail) {
        // WHY idempotency check first?
        // This method is called by an event listener which may retry on failure.
        // Running twice must not create two subscriptions.
        if (subscriptionRepository.existsByTenantId(tenantId)) {
            log.warn("Trial subscription already exists for tenant={}", tenantId);
            return;
        }

        Plan essentialPlan = planRepository.findByName("ESSENTIAL")
                .orElseThrow(() -> new IllegalStateException(
                        "ESSENTIAL plan not found — check V5 migration ran correctly"
                ));

        Subscription subscription = Subscription.createPilot(tenantId, essentialPlan);
        subscriptionRepository.save(subscription);

        log.info("Created 60-day pilot subscription for tenant={} plan=ESSENTIAL",
                tenantId);
    }

    @Transactional
    public void activatedSubscription(TenantId tenantId) {
        Subscription sub = findSubscription(tenantId);
        sub.activate();
        subscriptionRepository.save(sub);
        log.info("Activated subscription for tenant={}", tenantId);
    }

    @Transactional
    public void suspendExpiredPilots() {
        var expired = subscriptionRepository
                .findExpiredPilots(java.time.Instant.now());

        expired.forEach(sub -> {
            sub.suspend();
            subscriptionRepository.save(sub);
            log.info("Suspended expired pilot for tenant={}", sub.getTenantId());
        });

        if (!expired.isEmpty()) {
            log.info("Suspended {} expired pilot subscriptions", expired.size());
        }
    }

    private Subscription findSubscription(TenantId tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "No subscription found for tenant: " + tenantId
                ));
    }
}
