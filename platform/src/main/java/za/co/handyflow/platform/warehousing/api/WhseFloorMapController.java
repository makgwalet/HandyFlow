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
    public ResponseEntity<ApiResponse<WhseFloorMapService.FloorMap>> map(@RequestParam(required = false) UUID warehouseId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.floorMap(TenantContext.getTenantIdAsObject(), warehouseId)));
    }

    @PostMapping("/generate/preview")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Shows the locations a block of racking would create, and any that clash with existing ones")
    public ResponseEntity<ApiResponse<List<WhseFloorMapService.PreviewSlot>>> preview(@Valid @RequestBody GenerateRequest req, @RequestParam(required = false) UUID warehouseId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.preview(TenantContext.getTenantIdAsObject(), req.spec(), warehouseId)));
    }

    @PostMapping("/generate")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Creates a block of racking (aisles x bays x levels). All or nothing.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> generate(@Valid @RequestBody GenerateRequest req, @RequestParam(required = false) UUID warehouseId) {
        featureGuard.requireModule("warehousing");
        int n = service.generate(TenantContext.getTenantIdAsObject(), req.spec(), req.capacityUnits(), TenantContext.getCurrentUserId(), warehouseId);
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
                l.getCapacityUnits(), l.isActive(), l.getAisle(), l.getBay(), l.getLevel(), l.getLocationType(), l.getPickSequence(), l.getPosX(), l.getPosY(), l.getWarehouseId())));
    }

    public record MoveCellRequest(@Min(0) int fromX, @Min(0) int fromY, @Min(0) int toX, @Min(0) int toY) {}
    public record EditBayRequest(@Min(0) int x, @Min(0) int y, String locationType, @Min(0) BigDecimal capacityUnits, boolean clearCapacity, Boolean active) {}
    public record AddLevelsRequest(@Min(0) int x, @Min(0) int y, @Min(1) @Max(10) int count, @Min(0) BigDecimal capacityUnits) {}
    public record ExtendAisleRequest(@NotNull String aisle, @Min(1) @Max(99) int bays, @Min(0) BigDecimal capacityUnits) {}

    @PostMapping("/cells/move")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Moves every level of a bay to another cell (drag on the map). Codes do not change; the target must be free.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> move(@Valid @RequestBody MoveCellRequest req, @RequestParam(required = false) UUID warehouseId) {
        featureGuard.requireModule("warehousing");
        int n = service.moveCell(TenantContext.getTenantIdAsObject(), req.fromX(), req.fromY(), req.toX(), req.toY(), TenantContext.getCurrentUserId(), warehouseId);
        return ResponseEntity.ok(ApiResponse.success("Moved", Map.of("moved", n)));
    }

    @PostMapping("/cells/edit")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Changes the type, capacity per level or in-use state of every level of a bay. A bay that holds stock cannot be switched off.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> edit(@Valid @RequestBody EditBayRequest req, @RequestParam(required = false) UUID warehouseId) {
        featureGuard.requireModule("warehousing");
        int n = service.editBay(TenantContext.getTenantIdAsObject(), req.x(), req.y(), req.locationType(), req.capacityUnits(), req.clearCapacity(), req.active(),
                TenantContext.getCurrentUserId(), warehouseId);
        return ResponseEntity.ok(ApiResponse.success("Saved", Map.of("changed", n)));
    }

    @PostMapping("/cells/add-levels")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Adds levels on top of a bay")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> addLevels(@Valid @RequestBody AddLevelsRequest req, @RequestParam(required = false) UUID warehouseId) {
        featureGuard.requireModule("warehousing");
        int n = service.addLevels(TenantContext.getTenantIdAsObject(), req.x(), req.y(), req.count(), req.capacityUnits(), TenantContext.getCurrentUserId(), warehouseId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Levels added", Map.of("created", n)));
    }

    @PostMapping("/aisles/extend")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Adds bays to the end of an aisle, like its last bay. New bays are last in the picking order.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> extend(@Valid @RequestBody ExtendAisleRequest req, @RequestParam(required = false) UUID warehouseId) {
        featureGuard.requireModule("warehousing");
        int n = service.extendAisle(TenantContext.getTenantIdAsObject(), req.aisle(), req.bays(), req.capacityUnits(), TenantContext.getCurrentUserId(), warehouseId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Aisle extended", Map.of("created", n)));
    }
}
