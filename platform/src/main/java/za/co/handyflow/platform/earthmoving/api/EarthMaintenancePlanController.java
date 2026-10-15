package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthMaintenancePlanService;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/maintenance-plans")
@RequiredArgsConstructor
@Tag(name = "Earthmoving - Maintenance plans", description = "Service intervals by hours or days")
public class EarthMaintenancePlanController {

    private final EarthMaintenancePlanService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> list(@RequestParam(required = false) UUID assetId) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), assetId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<PlanResponse>> create(@Valid @RequestBody CreatePlanRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Plan created",
                service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<PlanResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdatePlanRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/scan")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    @Operation(summary = "Check every active plan now and raise work orders for those that are due")
    public ResponseEntity<ApiResponse<PlanScanResponse>> scan() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.scanNow(TenantContext.getTenantIdAsObject())));
    }
}
