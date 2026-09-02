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
import za.co.handyflow.platform.agriculture.application.internal.AgFarmService;
import za.co.handyflow.platform.agriculture.dto.AssignManagerRequest;
import za.co.handyflow.platform.agriculture.dto.CreateFarmRequest;
import za.co.handyflow.platform.agriculture.dto.FarmResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateFarmRequest;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agriculture/farms")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Farms", description = "Top-level farm register")
public class AgFarmController {

    private final AgFarmService farmService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "List farms, optionally filtered by status")
    public ResponseEntity<ApiResponse<Page<FarmResponse>>> getFarms(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                farmService.getFarms(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<FarmResponse>> getFarm(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                farmService.getFarm(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Register a new farm")
    public ResponseEntity<ApiResponse<FarmResponse>> createFarm(@Valid @RequestBody CreateFarmRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Farm registered",
                farmService.createFarm(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<FarmResponse>> updateFarm(
            @PathVariable UUID id, @Valid @RequestBody UpdateFarmRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Farm updated",
                farmService.updateFarm(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/{id}/manager")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Assign (or clear, with a null managerId) a farm manager — validated against HR")
    public ResponseEntity<ApiResponse<FarmResponse>> assignManager(
            @PathVariable UUID id, @RequestBody AssignManagerRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Manager assigned",
                farmService.assignManager(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<FarmResponse>> deactivateFarm(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Farm deactivated",
                farmService.deactivateFarm(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<FarmResponse>> reactivateFarm(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Farm reactivated",
                farmService.reactivateFarm(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteFarm(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        farmService.deleteFarm(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Farm deleted", null));
    }
}
