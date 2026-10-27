package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmAssetLookupService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmSyncService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmWorkOrderService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmAssetLookupResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSyncRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSyncResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmWorkOrderChangesResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.UUID;

/** Endpoints for the FM field app: scan an asset, upload what was done offline, pull what changed. */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Field", description = "QR asset lookup and offline sync")
public class FmFieldController {

    private static final String CAN_READ = "hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_TECHNICIAN')";
    private static final String CAN_SYNC = "hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_TECHNICIAN')";

    private final FmAssetLookupService lookupService;
    private final FmSyncService syncService;
    private final FmWorkOrderService workOrderService;
    private final FeatureGuard featureGuard;

    @GetMapping("/assets/lookup")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Resolve a scanned QR payload, asset id, link or asset tag to the asset and what is happening to it")
    public ResponseEntity<ApiResponse<FmAssetLookupResponse>> lookup(@RequestParam String code) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(lookupService.lookup(TenantContext.getTenantIdAsObject(), code)));
    }

    @GetMapping("/assets/{id}/qr")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "The text to encode in this asset's QR label")
    public ResponseEntity<ApiResponse<String>> qrPayload(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(lookupService.qrPayload(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/sync")
    @PreAuthorize(CAN_SYNC)
    @Operation(summary = "Upload operations queued offline. Safe to resend: each opId is applied at most once")
    public ResponseEntity<ApiResponse<FmSyncResponse>> sync(@Valid @RequestBody FmSyncRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(syncService.process(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/sync/work-orders")
    @PreAuthorize(CAN_SYNC)
    @Operation(summary = "Work orders changed since an instant (inclusive), for refreshing the device's copy")
    public ResponseEntity<ApiResponse<FmWorkOrderChangesResponse>> changes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(defaultValue = "100") int limit) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(workOrderService.getChangedSince(TenantContext.getTenantIdAsObject(), since, limit)));
    }
}
