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

/**
 * FIX (HandyFlow BOS Discovery doc, Section 60/64): markPastDue()/reinstate()
 * were gated by hasAuthority('BILLING_MANAGE') — a permission every
 * tenant's own ADMIN role holds by default via
 * RoleService.createDefaultAdminRole() (permissionRepository.findAll(),
 * no filtering). Combined with both methods resolving their target via
 * TenantContext.getTenantIdAsObject() — the CALLER's own tenant — this
 * meant a tenant's own admin could self-reinstate a suspended
 * subscription with no ops involvement at all, despite both methods'
 * own doc comments stating they're meant to be "Called by HandyFlow ops."
 * Changed to hasRole('SUPERADMIN'), matching AdminController's own
 * class-level standard for every other genuine platform-ops action.
 * <p>
 * CAVEAT: these two endpoints resolve the target tenant via TenantContext,
 * which may not be populated at all under admin authentication (a
 * different auth path than the tenant-scoped JwtAuthFilter this
 * TenantContext usage assumes — see PortalJwtFilter's own documented
 * reasoning for why a different auth path deliberately does NOT populate
 * TenantContext). If it doesn't resolve here either, these two endpoints
 * are now correctly secured but may be functionally unreachable by
 * anyone, including real superadmins — that's an acceptable outcome for
 * this fix (over-restrictive is safe; under-restrictive was the actual
 * vulnerability), but worth testing directly rather than assuming this
 * is the complete fix. AdminInvoiceController.markPaid() already covers
 * the real "restore a PAST_DUE subscription" ops workflow correctly
 * (via @PathVariable, not TenantContext) — if these two turn out to be
 * dead after this change, that's confirmation they were redundant with
 * that path all along, not a new problem this fix introduced.
 */
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

    // Previously no way to change a tenant's plan at all after creation —
    // see Subscription.changePlan()'s own Javadoc for the full reasoning
    // (no proration, doesn't touch modules). This one stays BILLING_MANAGE
    // deliberately — it's a genuine tenant self-service action (any admin
    // upgrading/downgrading their own plan is correct and expected),
    // unlike past-due/reinstate below, which are ops-only actions that
    // happened to share the same permission by mistake.
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
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Mark subscription past due — starts 7-day grace period. Called by HandyFlow ops when invoice is unpaid. " +
            "PLATFORM-STAFF ONLY — see class-level Javadoc for why this can't be BILLING_MANAGE.")
    public ResponseEntity<ApiResponse<Void>> markPastDue() {
        var tenantId = TenantContext.getTenantIdAsObject();
        String[] details = fetchTenantDetails(tenantId.getValue());
        subscriptionService.markPastDue(tenantId, details[1], details[0]);
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription marked past due. 7-day grace period started.", null));
    }

    @PostMapping("/subscription/reinstate")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Reinstate subscription after payment received — restores full access. " +
            "PLATFORM-STAFF ONLY — see class-level Javadoc for why this can't be BILLING_MANAGE.")
    public ResponseEntity<ApiResponse<Void>> reinstate() {
        var tenantId = TenantContext.getTenantIdAsObject();
        String[] details = fetchTenantDetails(tenantId.getValue());
        subscriptionService.reinstate(tenantId, details[1], details[0]);
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription reinstated. Full access restored.", null));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Returns [tenantName, ownerEmail].
     * <p>
     * UNVERIFIED TAIL: the query body below (LIMIT clause, exception
     * handling for a tenant with no users yet, exact return shape on a
     * miss) is reconstructed from a truncated source view — I confirmed
     * the SELECT/JOIN/WHERE clause up to "WHERE t.id = ?" directly, but
     * not what comes after. Written here as a reasonable, defensive
     * completion (LIMIT 1 since a tenant can have multiple users and this
     * only wants one contact email; a caught exception falling back to
     * safe defaults rather than throwing, since a missing tenant/user
     * shouldn't block markPastDue/reinstate from running). CONFIRM THIS
     * MATCHES THE REAL METHOD BODY before trusting this file as
     * byte-perfect — this is the one part of this file I'd flag as
     * "very likely close" rather than "confirmed," unlike everything
     * else in this class.
     */
    private String[] fetchTenantDetails(java.util.UUID tenantId) {
        try {
            var row = jdbc.queryForMap(
                    """
                    SELECT t.name, u.email
                    FROM tenants t
                    JOIN users u ON u.tenant_id = t.id
                    WHERE t.id = ?
                    LIMIT 1
                    """, tenantId);
            return new String[]{(String) row.get("name"), (String) row.get("email")};
        } catch (Exception e) {
            log.warn("Could not resolve tenant details for tenant={}: {}", tenantId, e.getMessage());
            return new String[]{"your company", "unknown"};
        }
    }
}