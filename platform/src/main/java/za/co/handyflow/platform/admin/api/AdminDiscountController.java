package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.admin.application.internal.AdminDiscountService;
import za.co.handyflow.platform.shared.ApiResponse;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin Discounts", description = "Volume discounts, partnership pricing, redemption history")
public class AdminDiscountController {

    private final AdminDiscountService discountService;

    // ── Volume tiers ──────────────────────────────────────────────────────────

    @GetMapping("/discounts/volume")
    @Operation(summary = "List all volume discount tiers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVolumeTiers() {
        return ResponseEntity.ok(ApiResponse.success(discountService.getVolumeTiers()));
    }

    @PostMapping("/discounts/volume")
    @Operation(summary = "Create a new volume discount tier")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createVolumeTier(
            @RequestBody Map<String, Object> req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Volume tier created",
                discountService.createVolumeTier(
                        Integer.parseInt(req.get("minModules").toString()),
                        new BigDecimal(req.get("discountPct").toString()),
                        req.getOrDefault("description", "").toString(),
                        getAdminId(), getAdminEmail())));
    }

    @PutMapping("/discounts/volume/{id}")
    @Operation(summary = "Update a volume discount tier")
    public ResponseEntity<ApiResponse<Void>> updateVolumeTier(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> req) {
        discountService.updateVolumeTier(
                id,
                new BigDecimal(req.get("discountPct").toString()),
                req.getOrDefault("description", "").toString(),
                Boolean.parseBoolean(req.getOrDefault("active", "true").toString()),
                getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Volume tier updated", null));
    }

    @DeleteMapping("/discounts/volume/{id}")
    @Operation(summary = "Delete a volume discount tier")
    public ResponseEntity<ApiResponse<Void>> deleteVolumeTier(@PathVariable UUID id) {
        discountService.deleteVolumeTier(id, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Volume tier deleted", null));
    }

    // ── Partnerships ──────────────────────────────────────────────────────────

    @GetMapping("/discounts/partnerships")
    @Operation(summary = "List all partnership pricing agreements")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPartnerships() {
        return ResponseEntity.ok(ApiResponse.success(discountService.getPartnerships()));
    }

    @PostMapping("/discounts/partnerships")
    @Operation(summary = "Create a partnership pricing agreement")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPartnership(
            @RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<String> slugs = (List<String>) req.get("tenantSlugs");
        return ResponseEntity.status(201).body(ApiResponse.success("Partnership created",
                discountService.createPartnership(
                        req.get("partnerName").toString(),
                        req.get("contactEmail") != null ? req.get("contactEmail").toString() : null,
                        new BigDecimal(req.get("discountPct").toString()),
                        req.getOrDefault("appliesTo", "ALL").toString(),
                        req.get("moduleKey") != null ? req.get("moduleKey").toString() : null,
                        slugs,
                        req.get("validFrom") != null ? req.get("validFrom").toString() : null,
                        req.get("validTo")   != null ? req.get("validTo").toString()   : null,
                        req.get("notes")     != null ? req.get("notes").toString()     : null,
                        getAdminId(), getAdminEmail())));
    }

    @DeleteMapping("/discounts/partnerships/{id}")
    @Operation(summary = "Deactivate a partnership agreement")
    public ResponseEntity<ApiResponse<Void>> deactivatePartnership(@PathVariable UUID id) {
        discountService.deactivatePartnership(id, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Partnership deactivated", null));
    }

    // ── Preview discount for a tenant/module ─────────────────────────────────

    @GetMapping("/discounts/preview")
    @Operation(summary = "Preview best discount for a tenant + module — used in admin tenant detail page")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewDiscount(
            @RequestParam UUID    tenantId,
            @RequestParam String  moduleKey,
            @RequestParam(required = false) String code) {

        AdminDiscountService.DiscountResult result =
                discountService.resolveDiscount(tenantId, moduleKey, code);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("tenantId",       tenantId);
        resp.put("moduleKey",      moduleKey);
        resp.put("discountPct",    result.pct());
        resp.put("discountSource", result.source());
        resp.put("discountLabel",  result.label());
        resp.put("hasDiscount",    result.hasDiscount());
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    // ── Redemption history ────────────────────────────────────────────────────

    @GetMapping("/discounts/redemptions")
    @Operation(summary = "Discount code redemption history — global or per-tenant")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRedemptions(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                discountService.getRedemptions(tenantId, limit)));
    }

    @GetMapping("/discounts/stats")
    @Operation(summary = "Discount program stats — redemption counts, total discount given, top codes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(discountService.getDiscountStats()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID getAdminId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return UUID.fromString(auth.getPrincipal().toString());
    }

    @SuppressWarnings("unchecked")
    private String getAdminEmail() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof java.util.Map) {
            var details = (java.util.Map<String, String>) auth.getDetails();
            String email = details.get("email");
            if (email != null && !email.isBlank()) return email;
        }
        return "unknown-admin";
    }
}
