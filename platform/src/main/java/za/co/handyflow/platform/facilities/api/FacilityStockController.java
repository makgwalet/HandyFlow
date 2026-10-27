package za.co.handyflow.platform.facilities.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilities.application.internal.FacilityStockService;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Stock items, the movements ledger, and issuing parts to work orders. */
@RestController
@RequestMapping("/api/v1/facilities/stock")
@RequiredArgsConstructor
public class FacilityStockController {

    private static final String CAN_VIEW = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_TECHNICIAN')";
    private static final String CAN_ISSUE = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_TECHNICIAN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')";

    private final FacilityStockService stockService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<Page<StockItemResponse>>> list(
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean includeInactive,
            @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(stockService.list(TenantContext.getTenantIdAsObject(), q, includeInactive, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<StockItemResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(stockService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> movements(@PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(stockService.movements(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @GetMapping("/reorder-suggestions")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<List<ReorderSuggestionResponse>>> reorderSuggestions() {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(stockService.reorderSuggestions(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StockItemResponse>> create(@Valid @RequestBody UpsertStockItemRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Stock item added",
                stockService.createItem(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StockItemResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpsertStockItemRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Stock item updated",
                stockService.updateItem(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StockItemResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Stock item deactivated", stockService.setActive(TenantContext.getTenantIdAsObject(), id, false)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StockItemResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Stock item reactivated", stockService.setActive(TenantContext.getTenantIdAsObject(), id, true)));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StockItemResponse>> receive(@PathVariable UUID id, @Valid @RequestBody ReceiveStockRequest request,
                                                                  @RequestParam(required = false) UUID storeId) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Stock received", stockService.receive(TenantContext.getTenantIdAsObject(), id, request, storeId)));
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize(CAN_ISSUE)
    public ResponseEntity<ApiResponse<StockItemResponse>> issue(@PathVariable UUID id, @Valid @RequestBody IssueStockRequest request,
                                                                @RequestParam(required = false) UUID storeId) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Stock issued to work order", stockService.issue(TenantContext.getTenantIdAsObject(), id, request, storeId)));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize(CAN_ISSUE)
    public ResponseEntity<ApiResponse<StockItemResponse>> returnStock(@PathVariable UUID id, @Valid @RequestBody ReturnStockRequest request,
                                                                      @RequestParam(required = false) UUID storeId) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Stock returned", stockService.returnStock(TenantContext.getTenantIdAsObject(), id, request, storeId)));
    }

    @PostMapping("/{id}/adjust")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StockItemResponse>> adjust(@PathVariable UUID id, @Valid @RequestBody AdjustStockRequest request,
                                                                 @RequestParam(required = false) UUID storeId) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted", stockService.adjust(TenantContext.getTenantIdAsObject(), id, request, storeId)));
    }

    @GetMapping("/{id}/levels")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<List<StockLevelResponse>>> levels(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(stockService.levels(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StockItemResponse>> transfer(@PathVariable UUID id, @Valid @RequestBody TransferStockRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Stock transferred", stockService.transfer(TenantContext.getTenantIdAsObject(), id, request)));
    }
}
