package za.co.handyflow.platform.facilities.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilities.application.internal.FacilityAssetLookupService;
import za.co.handyflow.platform.facilities.application.internal.FacilitySyncService;
import za.co.handyflow.platform.facilities.application.internal.FacilityWorkOrderService;
import za.co.handyflow.platform.facilities.dto.AssetLookupResponse;
import za.co.handyflow.platform.facilities.dto.SyncRequest;
import za.co.handyflow.platform.facilities.dto.SyncResponse;
import za.co.handyflow.platform.facilities.dto.WorkOrderChangesResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.UUID;

/** Endpoints for the field app: scan an asset, upload what was done offline, pull what changed. */
@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
@Tag(name = "Facilities - Field", description = "QR asset lookup and offline sync")
public class FacilityFieldController {

    private static final String CAN_READ = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_TECHNICIAN','FACILITIES_INSPECT')";
    private static final String CAN_SYNC = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_TECHNICIAN','FACILITIES_INSPECT')";

    private final FacilityAssetLookupService lookupService;
    private final FacilitySyncService syncService;
    private final FacilityWorkOrderService workOrderService;
    private final FeatureGuard featureGuard;

    @GetMapping("/assets/lookup")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Resolve a scanned QR payload, asset id, link or asset tag to the asset and what is happening to it")
    public ResponseEntity<ApiResponse<AssetLookupResponse>> lookup(@RequestParam String code) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(lookupService.lookup(TenantContext.getTenantIdAsObject(), code)));
    }

    @GetMapping("/assets/{id}/qr")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "The text to encode in this asset's QR label")
    public ResponseEntity<ApiResponse<String>> qrPayload(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(lookupService.qrPayload(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/sync")
    @PreAuthorize(CAN_SYNC)
    @Operation(summary = "Upload operations queued offline. Safe to resend: each opId is applied at most once")
    public ResponseEntity<ApiResponse<SyncResponse>> sync(@Valid @RequestBody SyncRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(syncService.process(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/sync/work-orders")
    @PreAuthorize(CAN_SYNC)
    @Operation(summary = "Work orders changed since an instant (inclusive), for refreshing the device's copy")
    public ResponseEntity<ApiResponse<WorkOrderChangesResponse>> changes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(defaultValue = "100") int limit) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(workOrderService.getChangedSince(TenantContext.getTenantIdAsObject(), since, limit)));
    }
}
