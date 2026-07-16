package za.co.handyflow.platform.billing.application.internal;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.billing.api.PlanResponse;
import za.co.handyflow.platform.billing.api.SubscriptionResponse;
import za.co.handyflow.platform.billing.application.SubscriptionQueryFacade;
import za.co.handyflow.platform.billing.domain.model.Plan;
import za.co.handyflow.platform.billing.domain.model.Subscription;
import za.co.handyflow.platform.billing.domain.repository.PlanRepository;
import za.co.handyflow.platform.billing.domain.repository.SubscriptionRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

@Service
@RequiredArgsConstructor
class SubscriptionQueryFacadeImpl implements SubscriptionQueryFacade {
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    @Transactional(readOnly = true)
    @Override
    public SubscriptionResponse getSubscription(TenantId tenantId) {
        Subscription sub = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription", tenantId.toString()));

        boolean suspended = "SUSPENDED".equals(sub.getStatus().toString())
                || "CANCELLED".equals(sub.getStatus().toString());

        Long graceDaysRemaining = "PAST_DUE".equals(sub.getStatus().toString())
                ? sub.graceDaysRemaining()
                : null;

        return new SubscriptionResponse(
                sub.getId(),
                sub.getPlan().getName(),
                sub.getPlan().getDisplayName(),
                sub.getStatus().toString(),
                sub.getPilotEndsAt(),
                sub.pilotDaysRemaining(),
                sub.getCurrentPeriodEnd(),
                sub.getPlan().priceInRands(),
                sub.getPastDueSince(),       // null unless PAST_DUE
                graceDaysRemaining,          // null unless PAST_DUE
                suspended                    // true if SUSPENDED or CANCELLED
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> getAvailablePlans() {
        return planRepository.findAllByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toPlanResponse)
                .toList();
    }

    // NEW: backs the fix to UserManagementService.inviteUser() — confirmed
    // that method previously had zero user-count check against the
    // tenant's actual plan limit, meaning any tenant could invite
    // unlimited users regardless of what they're paying for.
    @Override
    @Transactional(readOnly = true)
    public int getMaxUsers(TenantId tenantId) {
        Subscription sub = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription", tenantId.toString()));
        return sub.getPlan().getMaxUsers();
    }

    private PlanResponse toPlanResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDisplayName(),
                plan.getDescription(),
                plan.priceInRands(),
                plan.getMaxUsers(),
                plan.getIncludedModuleCount(),
                plan.getIncludedModules(),
                plan.getFeatures()
        );
    }
}