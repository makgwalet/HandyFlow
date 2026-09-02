package za.co.handyflow.platform.facilities.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilities.application.internal.FacilityPpmScheduleService;
import za.co.handyflow.platform.facilities.dto.CreatePpmScheduleRequest;
import za.co.handyflow.platform.facilities.dto.PpmScheduleResponse;
import za.co.handyflow.platform.facilities.dto.UpdatePpmScheduleRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
public class FacilityPpmScheduleController {

    private final FacilityPpmScheduleService ppmScheduleService;
    private final FeatureGuard featureGuard;

    @GetMapping("/assets/{assetId}/ppm-schedules")
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<List<PpmScheduleResponse>>> getSchedulesForAsset(@PathVariable UUID assetId) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                ppmScheduleService.getSchedulesForAsset(TenantContext.getTenantIdAsObject(), assetId)));
    }

    @PostMapping("/ppm-schedules")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<PpmScheduleResponse>> createSchedule(@Valid @RequestBody CreatePpmScheduleRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("PPM schedule created",
                ppmScheduleService.createSchedule(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/ppm-schedules/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<PpmScheduleResponse>> updateSchedule(
            @PathVariable UUID id, @RequestBody UpdatePpmScheduleRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("PPM schedule updated",
                ppmScheduleService.updateSchedule(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/ppm-schedules/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<PpmScheduleResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("PPM schedule deactivated",
                ppmScheduleService.deactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/ppm-schedules/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<PpmScheduleResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("PPM schedule reactivated",
                ppmScheduleService.reactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/ppm-schedules/{id}")
    @PreAuthorize("hasAuthority('FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        ppmScheduleService.deleteSchedule(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("PPM schedule deleted", null));
    }
}
