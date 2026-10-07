package za.co.handyflow.platform.compliancetender.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.compliancetender.application.internal.TenderRateService;
import za.co.handyflow.platform.compliancetender.dto.ImportTenderRatesRequest;
import za.co.handyflow.platform.compliancetender.dto.SaveTenderRateRequest;
import za.co.handyflow.platform.compliancetender.dto.TenderRateImportResult;
import za.co.handyflow.platform.compliancetender.dto.TenderRateResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** The company's costs, so like the price schedule itself, even reading needs MANAGE or ADMIN. */
@RestController
@RequestMapping("/api/v1/compliance/rates")
@RequiredArgsConstructor
@Tag(name = "Compliance - Rates library", description = "Reusable costs for tender pricing, entered by hand or imported from a supplier's price list")
public class TenderRateController {

    private final TenderRateService rates;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Every rate in the library, active or not")
    public ResponseEntity<ApiResponse<List<TenderRateResponse>>> list() {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(rates.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Add a rate")
    public ResponseEntity<ApiResponse<TenderRateResponse>> create(@Valid @RequestBody SaveTenderRateRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Rate added",
                rates.create(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Change a rate (a new cost keeps the old one as the previous cost)")
    public ResponseEntity<ApiResponse<TenderRateResponse>> update(@PathVariable UUID id, @Valid @RequestBody SaveTenderRateRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Rate updated",
                rates.update(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Remove a rate (lines already copied onto tenders are not affected)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        rates.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Import a supplier's price list (CSV). With dryRun, only reports what would change")
    public ResponseEntity<ApiResponse<TenderRateImportResult>> importCsv(@Valid @RequestBody ImportTenderRatesRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                rates.importCsv(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }
}
