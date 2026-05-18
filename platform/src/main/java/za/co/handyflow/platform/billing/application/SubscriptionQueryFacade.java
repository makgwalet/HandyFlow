package za.co.handyflow.platform.billing.application;

import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.billing.api.PlanResponse;
import za.co.handyflow.platform.billing.api.SubscriptionResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

public interface SubscriptionQueryFacade {
    SubscriptionResponse getSubscription(TenantId tenantId);
    List<PlanResponse> getAvailablePlans();
}
