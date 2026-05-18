package za.co.handyflow.platform.billing.api;
import za.co.handyflow.platform.billing.application.SubscriptionQueryFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Subscription and plan management")
public class SubscriptionController {

    private final SubscriptionQueryFacade subscriptionQueryFacade;

    @GetMapping("/subscription")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    @Operation(summary = "Get current subscription details")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getSubscription() {
        var tenantId = TenantContext.getTenantIdAsObject();
        var response = subscriptionQueryFacade.getSubscription(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/plans")
    @Operation(summary = "Get all available plans")
    public ResponseEntity<ApiResponse<java.util.List<PlanResponse>>> getPlans() {
        var plans = subscriptionQueryFacade.getAvailablePlans();
        return ResponseEntity.ok(ApiResponse.success(plans));
    }

}
