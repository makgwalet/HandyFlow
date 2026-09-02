package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseLocationService;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;
import za.co.handyflow.platform.warehousing.dto.LocationResponse;
import za.co.handyflow.platform.warehousing.dto.UpsertLocationRequest;

import java.util.List;
import java.util.UUID;

/** The operator's own warehouse location/bin structure — not per-client, see WhseLocation's own Javadoc. */
@RestController
@RequestMapping("/api/v1/warehousing/locations")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Locations", description = "The operator's own warehouse location/bin structure")
public class WhseLocationController {

    private final WhseLocationService locationService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> list() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                locationService.listAll(TenantContext.getTenantIdAsObject()).stream().map(this::toResponse).toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<LocationResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(locationService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Create a new warehouse location/bin")
    public ResponseEntity<ApiResponse<LocationResponse>> create(@Valid @RequestBody UpsertLocationRequest req) {
        featureGuard.requireModule("warehousing");
        WhseLocation location = locationService.create(TenantContext.getTenantIdAsObject(), req.code(), req.zone(),
                req.description(), req.capacityUnits());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Location created", toResponse(location)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<LocationResponse>> update(@PathVariable UUID id,
                                                                  @Valid @RequestBody UpsertLocationRequest req) {
        featureGuard.requireModule("warehousing");
        WhseLocation location = locationService.update(TenantContext.getTenantIdAsObject(), id, req.code(),
                req.zone(), req.description(), req.capacityUnits());
        return ResponseEntity.ok(ApiResponse.success("Location updated", toResponse(location)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<LocationResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Location deactivated",
                toResponse(locationService.deactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<LocationResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Location reactivated",
                toResponse(locationService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        locationService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Location deleted", null));
    }

    private LocationResponse toResponse(WhseLocation l) {
        return new LocationResponse(l.getId(), l.getCode(), l.getZone(), l.getDescription(), l.getCapacityUnits(),
                l.isActive());
    }
}
