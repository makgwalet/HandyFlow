package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseWarehouseService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Warehouses", description = "The warehouses of this business")
public class WhseWarehouseController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String ADMIN = "hasAuthority('WAREHOUSING_ADMIN')";

    private final WhseWarehouseService service;
    private final FeatureGuard featureGuard;

    public record CreateRequest(@NotNull @Size(max = 20) String code, @NotNull @Size(max = 120) String name, @Size(max = 500) String address) {}
    public record UpdateRequest(@NotNull @Size(max = 120) String name, @Size(max = 500) String address) {}

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WhseWarehouseService.Warehouse>>> list() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<WhseWarehouseService.Warehouse>> create(@Valid @RequestBody CreateRequest r) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Warehouse created",
                service.create(TenantContext.getTenantIdAsObject(), r.code(), r.name(), r.address(), TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<WhseWarehouseService.Warehouse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest r) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Saved", service.update(TenantContext.getTenantIdAsObject(), id, r.name(), r.address(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/make-default")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<WhseWarehouseService.Warehouse>> makeDefault(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Default warehouse set", service.makeDefault(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Switch a warehouse off. Refused for the default warehouse and for one that holds stock.")
    public ResponseEntity<ApiResponse<WhseWarehouseService.Warehouse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Switched off", service.setActive(TenantContext.getTenantIdAsObject(), id, false, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<WhseWarehouseService.Warehouse>> activate(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Switched on", service.setActive(TenantContext.getTenantIdAsObject(), id, true, TenantContext.getCurrentUserId())));
    }
}
