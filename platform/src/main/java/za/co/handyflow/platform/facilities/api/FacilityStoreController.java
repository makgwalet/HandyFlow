package za.co.handyflow.platform.facilities.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilities.application.internal.FacilityStoreService;
import za.co.handyflow.platform.facilities.dto.StoreRequest;
import za.co.handyflow.platform.facilities.dto.StoreResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Stores: where stock is held. Anyone who can see stock can list them; managing them is MANAGE/ADMIN. */
@RestController
@RequestMapping("/api/v1/facilities/stores")
@RequiredArgsConstructor
@Tag(name = "Facilities - Stores", description = "Storerooms, vans and cupboards that hold stock")
public class FacilityStoreController {

    private static final String CAN_VIEW = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_TECHNICIAN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')";

    private final FacilityStoreService storeService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<List<StoreResponse>>> list() {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(storeService.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StoreResponse>> create(@Valid @RequestBody StoreRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Store added", storeService.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StoreResponse>> update(@PathVariable UUID id, @Valid @RequestBody StoreRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Store updated", storeService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/make-default")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StoreResponse>> makeDefault(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Default store changed", storeService.makeDefault(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StoreResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Store deactivated", storeService.setActive(TenantContext.getTenantIdAsObject(), id, false)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<StoreResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Store reactivated", storeService.setActive(TenantContext.getTenantIdAsObject(), id, true)));
    }
}
