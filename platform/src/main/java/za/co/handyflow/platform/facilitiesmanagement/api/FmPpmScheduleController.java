package za.co.handyflow.platform.facilitiesmanagement.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPpmScheduleService;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmPpmScheduleRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPpmScheduleResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmPpmScheduleRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Chunk 5: a maintenance planner can hold FACILITIESMANAGEMENT_PPM_MANAGE to manage schedules; deletion stays ADMIN-only. */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement")
@RequiredArgsConstructor
public class FmPpmScheduleController {

    private static final String CAN_MANAGE = "hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_PPM_MANAGE')";

    private final FmPpmScheduleService ppmScheduleService;
    private final FeatureGuard featureGuard;

    @GetMapping("/assets/{assetId}/ppm-schedules")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_PPM_MANAGE')")
    public ResponseEntity<ApiResponse<List<FmPpmScheduleResponse>>> getSchedulesForAsset(@PathVariable UUID assetId) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                ppmScheduleService.getSchedulesForAsset(TenantContext.getTenantIdAsObject(), assetId)));
    }

    @PostMapping("/ppm-schedules")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FmPpmScheduleResponse>> createSchedule(@Valid @RequestBody CreateFmPpmScheduleRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("PPM schedule created",
                ppmScheduleService.createSchedule(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/ppm-schedules/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FmPpmScheduleResponse>> updateSchedule(
            @PathVariable UUID id, @RequestBody UpdateFmPpmScheduleRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("PPM schedule updated",
                ppmScheduleService.updateSchedule(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/ppm-schedules/{id}/deactivate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FmPpmScheduleResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("PPM schedule deactivated",
                ppmScheduleService.deactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/ppm-schedules/{id}/reactivate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FmPpmScheduleResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("PPM schedule reactivated",
                ppmScheduleService.reactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/ppm-schedules/{id}")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        ppmScheduleService.deleteSchedule(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("PPM schedule deleted", null));
    }
}