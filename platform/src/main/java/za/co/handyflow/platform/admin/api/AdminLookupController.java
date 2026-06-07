package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.admin.application.internal.AdminLookupService;
import za.co.handyflow.platform.shared.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin Lookups", description = "Holidays, tax tables, discounts, module catalogue")
public class AdminLookupController {

    private final AdminLookupService lookupService;

    // ── Public Holidays ───────────────────────────────────────────────────────

    @GetMapping("/lookups/holidays")
    @Operation(summary = "List SA public holidays — optionally filter by year")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHolidays(
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.success(lookupService.getHolidays(year)));
    }

    @PostMapping("/lookups/holidays")
    @Operation(summary = "Add a public holiday — date must be unique")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addHoliday(
            @RequestBody Map<String, String> req) {
        LocalDate date = LocalDate.parse(req.get("date"));
        String name    = req.get("name");
        return ResponseEntity.status(201).body(ApiResponse.success("Holiday added",
                lookupService.addHoliday(date, name, getAdminId(), getAdminEmail())));
    }

    @DeleteMapping("/lookups/holidays/{id}")
    @Operation(summary = "Remove a public holiday")
    public ResponseEntity<ApiResponse<Void>> deleteHoliday(@PathVariable UUID id) {
        lookupService.deleteHoliday(id, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Holiday removed", null));
    }

    // ── SARS Tax Tables ───────────────────────────────────────────────────────

    @GetMapping("/lookups/tax-tables")
    @Operation(summary = "Get SARS tax brackets — defaults to current year")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTaxTables(
            @RequestParam(required = false) Integer taxYear) {
        return ResponseEntity.ok(ApiResponse.success(lookupService.getTaxTables(taxYear)));
    }

    @GetMapping("/lookups/tax-rebates")
    @Operation(summary = "Get SARS tax rebates — primary, secondary, tertiary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTaxRebates(
            @RequestParam(required = false) Integer taxYear) {
        return ResponseEntity.ok(ApiResponse.success(lookupService.getTaxRebates(taxYear)));
    }

    @PutMapping("/lookups/tax-tables/{id}")
    @Operation(summary = "Update a tax bracket after budget speech — applies immediately to payroll")
    public ResponseEntity<ApiResponse<Void>> updateTaxBracket(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> req) {
        lookupService.updateTaxBracket(
                id,
                new BigDecimal(req.get("rate").toString()),
                new BigDecimal(req.get("incomeFrom").toString()),
                req.get("incomeTo") != null ? new BigDecimal(req.get("incomeTo").toString()) : null,
                req.get("baseTax") != null ? new BigDecimal(req.get("baseTax").toString()) : null,
                getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Tax bracket updated", null));
    }

    @PutMapping("/lookups/tax-rebates/{id}")
    @Operation(summary = "Update a tax rebate amount after budget speech")
    public ResponseEntity<ApiResponse<Void>> updateTaxRebate(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> req) {
        lookupService.updateTaxRebate(
                id, new BigDecimal(req.get("amount").toString()),
                getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Rebate updated", null));
    }

    // ── Discount Codes ────────────────────────────────────────────────────────

    @GetMapping("/billing/discounts")
    @Operation(summary = "List all discount codes — active and inactive")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDiscounts() {
        return ResponseEntity.ok(ApiResponse.success(lookupService.getDiscounts()));
    }

    @PostMapping("/billing/discounts")
    @Operation(summary = "Create a discount code — PERCENT or FIXED, scoped to ALL/PLAN/MODULE")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createDiscount(
            @RequestBody Map<String, Object> req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Discount created",
                lookupService.createDiscount(
                        req.get("code").toString(),
                        req.getOrDefault("description", "").toString(),
                        req.getOrDefault("discountType", "PERCENT").toString(),
                        new BigDecimal(req.get("value").toString()),
                        req.getOrDefault("appliesTo", "ALL").toString(),
                        req.get("moduleKey") != null ? req.get("moduleKey").toString() : null,
                        req.get("validFrom") != null ? req.get("validFrom").toString() : null,
                        req.get("validTo")   != null ? req.get("validTo").toString()   : null,
                        req.get("maxUses")   != null ? Integer.parseInt(req.get("maxUses").toString()) : null,
                        getAdminId(), getAdminEmail())));
    }

    @DeleteMapping("/billing/discounts/{id}")
    @Operation(summary = "Deactivate a discount code — soft delete, preserves usage history")
    public ResponseEntity<ApiResponse<Void>> deactivateDiscount(@PathVariable UUID id) {
        lookupService.deactivateDiscount(id, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Discount deactivated", null));
    }

    // ── Module Catalogue ──────────────────────────────────────────────────────

    @GetMapping("/modules/catalogue")
    @Operation(summary = "Full module catalogue with admin notes and active flag")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getModuleCatalogue() {
        return ResponseEntity.ok(ApiResponse.success(lookupService.getModuleCatalogue()));
    }

    @PutMapping("/modules/{key}/notes")
    @Operation(summary = "Update internal admin notes for a module")
    public ResponseEntity<ApiResponse<Void>> updateModuleNotes(
            @PathVariable String key,
            @RequestBody Map<String, String> req) {
        lookupService.updateModuleNotes(key, req.get("notes"), getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Notes updated", null));
    }

    @PostMapping("/modules/{key}/deactivate")
    @Operation(summary = "Hide a module from the tenant catalogue — existing subscriptions unaffected")
    public ResponseEntity<ApiResponse<Void>> deactivateModule(@PathVariable String key) {
        lookupService.setModuleActive(key, false, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Module hidden from catalogue", null));
    }

    @PostMapping("/modules/{key}/activate")
    @Operation(summary = "Make a module visible in the tenant catalogue again")
    public ResponseEntity<ApiResponse<Void>> activateModuleCatalogue(@PathVariable String key) {
        lookupService.setModuleActive(key, true, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Module visible in catalogue", null));
    }

    @PostMapping("/modules")
    @Operation(summary = "Create a new platform module — catalogue entry + permissions + ADMIN grants")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> createModule(
            @RequestBody java.util.Map<String, Object> req) {

        @SuppressWarnings("unchecked")
        java.util.List<String> extras =
                (java.util.List<String>) req.getOrDefault("extraPermissions", java.util.List.of());

        return ResponseEntity.status(201).body(ApiResponse.success("Module created",
                lookupService.createModule(
                        req.get("key").toString().toUpperCase(),
                        req.get("name").toString(),
                        req.getOrDefault("description", "").toString(),
                        new java.math.BigDecimal(req.get("monthlyPrice").toString()),
                        req.get("icon") != null ? req.get("icon").toString() : null,
                        req.getOrDefault("category", "OTHER").toString(),
                        req.get("sortOrder") != null
                                ? Integer.parseInt(req.get("sortOrder").toString()) : null,
                        extras,
                        getAdminId(), getAdminEmail())));
    }

    @GetMapping("/permissions")
    @Operation(summary = "List all system permissions — used by new-module permission picker")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.success(lookupService.getAllPermissions()));
    }

    @GetMapping("/modules/stats")
    @Operation(summary = "Per-module active/trial tenant counts and MRR contribution")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getModuleStats() {
        return ResponseEntity.ok(ApiResponse.success(lookupService.getModuleStats()));
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
