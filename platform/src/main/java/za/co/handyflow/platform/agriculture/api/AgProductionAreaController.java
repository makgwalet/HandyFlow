package za.co.handyflow.platform.agriculture.api;

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
import za.co.handyflow.platform.agriculture.application.internal.AgProductionAreaService;
import za.co.handyflow.platform.agriculture.dto.ChangeAreaStatusRequest;
import za.co.handyflow.platform.agriculture.dto.CreateProductionAreaRequest;
import za.co.handyflow.platform.agriculture.dto.ProductionAreaResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateProductionAreaRequest;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Production Areas", description = "Camps, fields, paddocks, houses, pens, ponds, orchards")
public class AgProductionAreaController {

    private final AgProductionAreaService areaService;
    private final FeatureGuard featureGuard;

    @GetMapping("/farms/{farmId}/production-areas")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<ProductionAreaResponse>>> getAreasForFarm(
            @PathVariable UUID farmId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                areaService.getAreasForFarm(TenantContext.getTenantIdAsObject(), farmId, status, pageable)));
    }

    @PostMapping("/farms/{farmId}/production-areas")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Add a production area to a farm")
    public ResponseEntity<ApiResponse<ProductionAreaResponse>> createArea(
            @PathVariable UUID farmId, @Valid @RequestBody CreateProductionAreaRequest request) {
        featureGuard.requireModule("agriculture");
        if (!farmId.equals(request.farmId())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("farmId in path and body must match"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Production area created",
                areaService.createArea(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/production-areas/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<ProductionAreaResponse>> getArea(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                areaService.getArea(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/production-areas/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<ProductionAreaResponse>> updateArea(
            @PathVariable UUID id, @Valid @RequestBody UpdateProductionAreaRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Production area updated",
                areaService.updateArea(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/production-areas/{id}/status")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<ProductionAreaResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody ChangeAreaStatusRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                areaService.changeStatus(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/production-areas/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteArea(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        areaService.deleteArea(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Production area deleted", null));
    }
}
