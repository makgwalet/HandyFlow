package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgInventoryItemService;
import za.co.handyflow.platform.agriculture.application.internal.AgPurchasesService;
import za.co.handyflow.platform.agriculture.dto.InventoryItemResponse;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SetSupplierRequest;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierOption;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierSpendResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Suppliers for Agriculture's stock (ADR-001, W7). Supply Chain owns suppliers; the rule is the owning module's own: listing suppliers needs SCM_READ, and spend by
 * supplier (cost data) needs AGRICULTURE_FINANCE as well. Only identity (id and name) crosses from Supply Chain; never banking or contact details.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Suppliers", description = "Link stock receipts to Supply Chain suppliers and see what they cost")
public class AgSupplierController {

    private final AgPurchasesService purchasesService;
    private final AgInventoryItemService inventoryService;
    private final FeatureGuard featureGuard;

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE') and hasAuthority('SCM_READ')")
    @Operation(summary = "Active suppliers from Supply Chain that stock can be bought from (id and name only)")
    public ResponseEntity<ApiResponse<List<SupplierOption>>> suppliers() {
        featureGuard.requireModule("agriculture");
        featureGuard.requireModule("supplychain");
        return ResponseEntity.ok(ApiResponse.success(purchasesService.suppliers(TenantContext.getTenantIdAsObject())));
    }

    @PatchMapping("/inventory-items/{id}/supplier")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Set an item's usual supplier (a Supply Chain supplier that is ACTIVE); null clears it")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> setUsualSupplier(@PathVariable UUID id, @RequestBody SetSupplierRequest request) {
        featureGuard.requireModule("agriculture");
        if (request.supplierId() != null) featureGuard.requireModule("supplychain");
        return ResponseEntity.ok(ApiResponse.success("Supplier updated", inventoryService.setUsualSupplier(TenantContext.getTenantIdAsObject(), id, request.supplierId())));
    }

    @GetMapping("/farms/{farmId}/purchases/by-supplier")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE') and hasAuthority('SCM_READ')")
    @Operation(summary = "What the farm's stock receipts cost, by supplier, in a date range (default the last 365 days)",
            description = "The cost of stock received into Agriculture inventory; not Supply Chain purchase orders or invoices.")
    public ResponseEntity<ApiResponse<SupplierSpendResponse>> bySupplier(@PathVariable UUID farmId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("agriculture");
        featureGuard.requireModule("supplychain");
        return ResponseEntity.ok(ApiResponse.success(purchasesService.bySupplier(TenantContext.getTenantIdAsObject(), farmId, from, to)));
    }
}
