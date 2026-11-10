package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseFloorLayout;
import za.co.handyflow.platform.warehousing.application.internal.WhseFloorMapService;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;
import za.co.handyflow.platform.warehousing.dto.LocationResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/floor-map")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Floor map", description = "Where locations sit in the building and what they hold")
public class WhseFloorMapController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    private final WhseFloorMapService service;
    private final FeatureGuard featureGuard;

    public record GenerateRequest(String zone, @NotEmpty List<String> aisles, @Min(1) @Max(99) int bays, @Min(1) @Max(10) int levels,
                                  @NotNull String locationType, @Min(0) int originX, @Min(0) int originY, @Min(1) @Max(10) Integer aisleSpacing,
                                  @Min(0) BigDecimal capacityUnits) {
        WhseFloorLayout.Spec spec() {
            return new WhseFloorLayout.Spec(zone, aisles, bays, levels, locationType, originX, originY, aisleSpacing == null ? 2 : aisleSpacing);
        }
    }

    public record LayoutRequest(String aisle, @Min(1) Integer bay, @Min(1) Integer level, @NotNull String locationType, @Min(0) Integer pickSequence,
                                @Min(0) Integer posX, @Min(0) Integer posY) {}

    @GetMapping
    @PreAuthorize(READ)
    @Operation(summary = "Every location with its map cell and what it holds: stock, held stock, SKUs, earliest expiry, share of capacity")
    public ResponseEntity<ApiResponse<WhseFloorMapService.FloorMap>> map() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.floorMap(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/generate/preview")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Shows the locations a block of racking would create, and any that clash with existing ones")
    public ResponseEntity<ApiResponse<List<WhseFloorMapService.PreviewSlot>>> preview(@Valid @RequestBody GenerateRequest req) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.preview(TenantContext.getTenantIdAsObject(), req.spec())));
    }

    @PostMapping("/generate")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Creates a block of racking (aisles x bays x levels). All or nothing.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> generate(@Valid @RequestBody GenerateRequest req) {
        featureGuard.requireModule("warehousing");
        int n = service.generate(TenantContext.getTenantIdAsObject(), req.spec(), req.capacityUnits(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Locations created", Map.of("created", n)));
    }

    @PutMapping("/locations/{id}/layout")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Places a location on the map, or changes its aisle, bay, level, type and picking order. Empty x and y take it off the map.")
    public ResponseEntity<ApiResponse<LocationResponse>> layout(@PathVariable UUID id, @Valid @RequestBody LayoutRequest req) {
        featureGuard.requireModule("warehousing");
        WhseLocation l = service.setLayout(TenantContext.getTenantIdAsObject(), id, req.aisle(), req.bay(), req.level(), req.locationType(),
                req.pickSequence(), req.posX(), req.posY());
        return ResponseEntity.ok(ApiResponse.success("Layout saved", new LocationResponse(l.getId(), l.getCode(), l.getZone(), l.getDescription(),
                l.getCapacityUnits(), l.isActive(), l.getAisle(), l.getBay(), l.getLevel(), l.getLocationType(), l.getPickSequence(), l.getPosX(), l.getPosY())));
    }
}
