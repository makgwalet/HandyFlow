package za.co.handyflow.platform.supplychain.api;

import za.co.handyflow.platform.billing.FeatureGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.supplychain.application.internal.InvoiceMatchingService;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoice;
import za.co.handyflow.platform.supplychain.dto.MatchExceptionResponse;
import za.co.handyflow.platform.supplychain.dto.SupplierInvoiceLineResponse;

import java.util.List;
import java.util.UUID;

/** Match visibility for supplier invoices — kept separate so ScmController stays unchanged. */
@RestController
@RequestMapping("/api/v1/supply-chain/supplier-invoices")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Supplier invoice matching")
public class ScmMatchController {

    private final FeatureGuard featureGuard;
    private final InvoiceMatchingService matchingService;


    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @GetMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Lines captured on a supplier invoice")
    public ResponseEntity<ApiResponse<List<SupplierInvoiceLineResponse>>> getLines(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                matchingService.listLines(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/match-exceptions")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Match exceptions (current and historical) for a supplier invoice")
    public ResponseEntity<ApiResponse<List<MatchExceptionResponse>>> getExceptions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                matchingService.listExceptions(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/rematch")
    @PreAuthorize("hasAuthority('SCM_INVOICE')")
    @Operation(summary = "Re-run three-way matching, e.g. after the missing goods receipt has been posted")
    public ResponseEntity<ApiResponse<ScSupplierInvoice>> rematch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Match re-evaluated",
                matchingService.rematch(TenantContext.getTenantIdAsObject(), id)));
    }
}
