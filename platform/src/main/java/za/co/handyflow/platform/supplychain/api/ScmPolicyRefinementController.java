package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.supplychain.application.internal.ScmPolicyOverrideService;
import za.co.handyflow.platform.supplychain.application.internal.ScmSavingsService;
import za.co.handyflow.platform.supplychain.dto.PolicyRefinementDtos.*;

import java.time.LocalDate;
import java.util.UUID;

/** Category tolerances, supplier VAT overrides, exchange rates, and the contract-savings report. */
@RestController
@RequestMapping("/api/v1/supply-chain")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Procurement policy refinements and savings")
public class ScmPolicyRefinementController {

    private static final String CAN_READ = "hasAnyAuthority('SCM_READ','SCM_ADMIN')";
    private static final String CAN_MANAGE = "hasAuthority('SCM_ADMIN')";

    private final FeatureGuard featureGuard;
    private final ScmPolicyOverrideService overrides;
    private final ScmSavingsService savings;

    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    private static UUID tid() { return TenantContext.getTenantIdAsObject().getValue(); }
    private static UUID user() { return TenantContext.getCurrentUserId(); }
    private static String userName() { return TenantContext.getCurrentUserName(); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T body) { return ResponseEntity.ok(ApiResponse.success(m, body)); }

    @GetMapping("/procurement-policy/overrides")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Category tolerances, supplier VAT overrides and exchange rates")
    public ResponseEntity<ApiResponse<Overview>> overview() { return ok("Success", overrides.overview(tid())); }

    @PutMapping("/procurement-policy/category-tolerance/{categoryId}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Set a catalogue category's price-increase tolerance")
    public ResponseEntity<ApiResponse<Change>> setTolerance(@PathVariable UUID categoryId, @RequestBody ToleranceRequest body) {
        return ok("Tolerance saved", overrides.setCategoryTolerance(tid(), categoryId, body.tolerancePct(), user(), userName()));
    }

    @DeleteMapping("/procurement-policy/category-tolerance/{categoryId}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Remove a category's tolerance so it uses the company's")
    public ResponseEntity<ApiResponse<Change>> clearTolerance(@PathVariable UUID categoryId) {
        return ok("Tolerance removed", overrides.clearCategoryTolerance(tid(), categoryId, user(), userName()));
    }

    @PutMapping("/procurement-policy/supplier-vat/{supplierId}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Set a supplier's VAT recoverability; restates that supplier's budget commitments")
    public ResponseEntity<ApiResponse<Change>> setSupplierVat(@PathVariable UUID supplierId, @RequestBody SupplierVatRequest body) {
        return ok("Supplier VAT saved", overrides.setSupplierVat(tid(), supplierId, body.vatRecoverablePct(), body.note(), user(), userName()));
    }

    @DeleteMapping("/procurement-policy/supplier-vat/{supplierId}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Remove a supplier's VAT override so it uses the company's")
    public ResponseEntity<ApiResponse<Change>> clearSupplierVat(@PathVariable UUID supplierId) {
        return ok("Supplier VAT removed", overrides.clearSupplierVat(tid(), supplierId, user(), userName()));
    }

    @PostMapping("/exchange-rates")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Add or replace an exchange rate to the base currency; restates budget commitments")
    public ResponseEntity<ApiResponse<Change>> addRate(@RequestBody RateRequest body) {
        return ok("Rate saved", overrides.addRate(tid(), body.currency(), body.rate(), body.effectiveFrom(), user(), userName()));
    }

    @DeleteMapping("/exchange-rates/{id}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Delete an exchange rate; restates budget commitments")
    public ResponseEntity<ApiResponse<Change>> deleteRate(@PathVariable UUID id) {
        return ok("Rate deleted", overrides.deleteRate(tid(), id, user(), userName()));
    }

    @GetMapping("/savings/contracts")
    @PreAuthorize("hasAnyAuthority('SCM_READ','SCM_CONTRACT_READ','SCM_CONTRACT_MANAGE','SCM_ADMIN')")
    @Operation(summary = "Savings against catalogue list price and contract leakage, in the base currency")
    public ResponseEntity<ApiResponse<SavingsReport>> savings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok("Success", savings.report(tid(), from, to));
    }
}
