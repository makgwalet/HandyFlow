package za.co.handyflow.platform.billing.api;

import za.co.handyflow.platform.billing.application.SubscriptionQueryFacade;
import za.co.handyflow.platform.billing.application.internal.SubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.dto.ChangePlanRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Subscription and plan management")
public class SubscriptionController {

    private final SubscriptionQueryFacade subscriptionQueryFacade;
    private final SubscriptionService     subscriptionService;
    private final JdbcTemplate            jdbc;

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

    // NEW: previously no way to change a tenant's plan at all after
    // creation — see Subscription.changePlan()'s own Javadoc for the full
    // reasoning (no proration, doesn't touch modules). Same permission as
    // this controller's other subscription-mutating endpoints below.
    @PostMapping("/subscription/change-plan")
    @PreAuthorize("hasAuthority('BILLING_MANAGE')")
    @Operation(summary = "Change the tenant's plan — takes effect immediately, no proration " +
            "(no invoicing system exists yet to make mid-cycle proration meaningful)")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> changePlan(
            @Valid @RequestBody ChangePlanRequest req) {
        var tenantId = TenantContext.getTenantIdAsObject();
        subscriptionService.changePlan(tenantId, req.planId());
        var response = subscriptionQueryFacade.getSubscription(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Plan changed successfully", response));
    }

    // ── B5: Payment locking ───────────────────────────────────────────────────

    @PostMapping("/subscription/past-due")
    @PreAuthorize("hasAuthority('BILLING_MANAGE')")
    @Operation(summary = "Mark subscription past due — starts 7-day grace period. Called by HandyFlow ops when invoice is unpaid.")
    public ResponseEntity<ApiResponse<Void>> markPastDue() {
        var tenantId = TenantContext.getTenantIdAsObject();
        String[] details = fetchTenantDetails(tenantId.getValue());
        subscriptionService.markPastDue(tenantId, details[1], details[0]);
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription marked past due. 7-day grace period started.", null));
    }

    @PostMapping("/subscription/reinstate")
    @PreAuthorize("hasAuthority('BILLING_MANAGE')")
    @Operation(summary = "Reinstate subscription after payment received — restores full access.")
    public ResponseEntity<ApiResponse<Void>> reinstate() {
        var tenantId = TenantContext.getTenantIdAsObject();
        String[] details = fetchTenantDetails(tenantId.getValue());
        subscriptionService.reinstate(tenantId, details[1], details[0]);
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription reinstated. Full access restored.", null));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Returns [tenantName, ownerEmail] */
    private String[] fetchTenantDetails(java.util.UUID tenantId) {
        try {
            var row = jdbc.queryForMap(
                    """
                    SELECT t.name, u.email
                    FROM tenants t
                    JOIN users u ON u.tenant_id = t.id
                    WHERE t.id = ? AND u.deleted_at IS NULL
                    ORDER BY u.created_at
                    LIMIT 1
                    """, tenantId);
            return new String[]{
                    (String) row.getOrDefault("name",  "HandyFlow Tenant"),
                    (String) row.getOrDefault("email", "")
            };
        } catch (Exception e) {
            log.warn("Could not fetch tenant details: {}", e.getMessage());
            return new String[]{"HandyFlow Tenant", ""};
        }
    }
}