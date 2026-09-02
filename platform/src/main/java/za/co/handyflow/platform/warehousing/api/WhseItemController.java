package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseItemService;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.dto.CreateItemRequest;
import za.co.handyflow.platform.warehousing.dto.ItemResponse;
import za.co.handyflow.platform.warehousing.dto.UpdateItemRequest;

import java.util.List;
import java.util.UUID;

/** A client's SKU/item catalogue — list/create nested under the client, id-based ops flat, same convention as CollAgencyDebtorAccountController. */
@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Items", description = "Per-client SKU/item catalogue")
public class WhseItemController {

    private final WhseItemService itemService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/items")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ItemResponse>>> list(@PathVariable UUID clientId,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                itemService.listForClient(TenantContext.getTenantIdAsObject(), clientId, pageable).map(this::toResponse)));
    }

    @GetMapping("/clients/{clientId}/items/all")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Unpaginated — for the shipment/order line item picker")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> listAll(@PathVariable UUID clientId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                itemService.listAllActiveForClient(TenantContext.getTenantIdAsObject(), clientId)
                        .stream().map(this::toResponse).toList()));
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<ItemResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(toResponse(itemService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/clients/{clientId}/items")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Add a new item/SKU to this client's catalogue")
    public ResponseEntity<ApiResponse<ItemResponse>> create(@PathVariable UUID clientId,
            @Valid @RequestBody CreateItemRequest req) {
        featureGuard.requireModule("warehousing");
        WhseItem item = itemService.create(TenantContext.getTenantIdAsObject(), clientId, req.sku(),
                req.description(), req.uom(), req.storageRatePerUnitPerMonth());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Item created", toResponse(item)));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<ItemResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateItemRequest req) {
        featureGuard.requireModule("warehousing");
        WhseItem item = itemService.update(TenantContext.getTenantIdAsObject(), id, req.description(), req.uom(),
                req.storageRatePerUnitPerMonth());
        return ResponseEntity.ok(ApiResponse.success("Item updated", toResponse(item)));
    }

    @PostMapping("/items/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<ItemResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Item deactivated",
                toResponse(itemService.deactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/items/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<ItemResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Item reactivated",
                toResponse(itemService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        itemService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Item deleted", null));
    }

    private ItemResponse toResponse(WhseItem i) {
        return new ItemResponse(i.getId(), i.getClientId(), i.getSku(), i.getDescription(), i.getUom(),
                i.getStorageRatePerUnitPerMonth(), i.isActive());
    }
}
