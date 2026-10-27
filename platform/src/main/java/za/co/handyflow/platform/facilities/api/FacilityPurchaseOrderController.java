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
import za.co.handyflow.platform.facilities.application.internal.FacilityPurchaseOrderService;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Purchase orders for stock: raise, approve, receive goods, cancel or close short. */
@RestController
@RequestMapping("/api/v1/facilities/purchase-orders")
@RequiredArgsConstructor
public class FacilityPurchaseOrderController {

    private static final String CAN_VIEW = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')";

    private final FacilityPurchaseOrderService orderService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<Page<PurchaseOrderResponse>>> list(@RequestParam(required = false) String status,
                                                                          @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(orderService.list(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(orderService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> create(@Valid @RequestBody PurchaseOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Purchase order created",
                orderService.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> update(@PathVariable UUID id, @Valid @RequestBody PurchaseOrderRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Purchase order updated", orderService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> approve(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Purchase order approved", orderService.approve(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> receive(@PathVariable UUID id, @Valid @RequestBody ReceiveOrderRequest request,
                                                                       @RequestParam(required = false) UUID storeId) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Goods received", orderService.receive(TenantContext.getTenantIdAsObject(), id, request, storeId)));
    }

    @GetMapping("/{id}/goods-receipts")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<java.util.List<GoodsReceiptResponse>>> goodsReceipts(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(orderService.goodsReceipts(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Purchase order cancelled", orderService.cancel(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    @PostMapping("/{id}/close-short")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> closeShort(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Purchase order closed short", orderService.closeShort(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }
}
