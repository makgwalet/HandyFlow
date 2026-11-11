package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.admin.application.internal.ModuleTierService;
import za.co.handyflow.platform.admin.application.internal.TierPricing.Period;
import za.co.handyflow.platform.shared.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tier price book and tenant tier subscriptions, for any module. */
@RestController
@RequestMapping("/api/v1/admin/pricing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin Pricing", description = "Module tier price book and tenant tier subscriptions")
public class AdminPricingController {

    private final ModuleTierService tiers;

    public record PriceRequest(String tierCode, String period, BigDecimal amount, LocalDate effectiveFrom, String note) {}
    public record AssignRequest(String tierCode, String period, LocalDate effectiveFrom, BigDecimal negotiatedAmount, String note) {}
    public record CancelRequest(LocalDate endDate, String note) {}

    @GetMapping("/modules/{moduleKey}/tiers")
    @Operation(summary = "Tiers defined for a module")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> tiers(@PathVariable String moduleKey) {
        return ResponseEntity.ok(ApiResponse.success(tiers.tiers(moduleKey)));
    }

    @GetMapping("/modules/{moduleKey}/prices")
    @Operation(summary = "Price history for a module, approved or not")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> prices(@PathVariable String moduleKey) {
        return ResponseEntity.ok(ApiResponse.success(tiers.prices(moduleKey)));
    }

    @PostMapping("/modules/{moduleKey}/prices")
    @Operation(summary = "Propose a tier price. It is not billed until approved.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> propose(@PathVariable String moduleKey,
                                                                    @RequestBody PriceRequest req, Authentication auth) {
        UUID id = tiers.proposePrice(moduleKey, req.tierCode(), period(req.period()), req.amount(),
                req.effectiveFrom(), adminId(auth), req.note());
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id)));
    }

    @PostMapping("/prices/{id}/approve")
    @Operation(summary = "Approve a proposed price so it can be billed")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable UUID id, Authentication auth) {
        tiers.approvePrice(id, adminId(auth));
        return ResponseEntity.ok(ApiResponse.success("OK", null));
    }

    @DeleteMapping("/prices/{id}")
    @Operation(summary = "Withdraw an unapproved price")
    public ResponseEntity<ApiResponse<Void>> withdraw(@PathVariable UUID id) {
        tiers.withdrawPrice(id);
        return ResponseEntity.ok(ApiResponse.success("OK", null));
    }

    @GetMapping("/tenants/{tenantId}/modules/{moduleKey}/subscription")
    @Operation(summary = "Tier subscription history for a tenant and module")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history(@PathVariable UUID tenantId, @PathVariable String moduleKey) {
        return ResponseEntity.ok(ApiResponse.success(tiers.history(tenantId, moduleKey)));
    }

    @PostMapping("/tenants/{tenantId}/modules/{moduleKey}/subscription")
    @Operation(summary = "Put a tenant on a tier (upgrade now, downgrade at period end)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assign(@PathVariable UUID tenantId, @PathVariable String moduleKey,
                                                                   @RequestBody AssignRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(tiers.assign(tenantId, moduleKey, req.tierCode(), period(req.period()),
                req.effectiveFrom(), req.negotiatedAmount(), adminId(auth), req.note())));
    }

    @PostMapping("/tenants/{tenantId}/modules/{moduleKey}/subscription/cancel")
    @Operation(summary = "End a tenant's tier subscription on a date")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID tenantId, @PathVariable String moduleKey,
                                                    @RequestBody CancelRequest req, Authentication auth) {
        tiers.cancel(tenantId, moduleKey, req.endDate(), adminId(auth), req.note());
        return ResponseEntity.ok(ApiResponse.success("OK", null));
    }

    private static Period period(String p) {
        try { return Period.valueOf(p == null ? "MONTHLY" : p.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new za.co.handyflow.platform.shared.HandyFlowException(
                "Billing period must be MONTHLY or ANNUAL", org.springframework.http.HttpStatus.BAD_REQUEST, "PERIOD_INVALID"); }
    }

    private static UUID adminId(Authentication auth) {
        try { return UUID.fromString(String.valueOf(auth.getPrincipal())); }
        catch (Exception e) { return null; }
    }
}
