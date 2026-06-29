// security/api/CheckpointScanController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.CheckpointScanService;
import za.co.handyflow.platform.security.dto.ScanRequest;
import za.co.handyflow.platform.security.dto.ScanResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/checkpoints")
@RequiredArgsConstructor
@Tag(name = "Security - Checkpoints", description = "QR/NFC/BLE checkpoint scanning")
public class CheckpointScanController {

    private final CheckpointScanService scanService;
    private final FeatureGuard          featureGuard;

    @PostMapping("/scan")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Guard scans a checkpoint — logs timestamp, scan method, and optional GPS",
            description = "guardId is resolved from the authenticated session, NOT from the request body. " +
                    "A client-supplied guardId in the request body is ignored to prevent identity spoofing (bug #13). " +
                    "Supports scanType: QR, NFC, BLE. GPS_PING and MANUAL use a separate endpoint (Phase 1)."
    )
    public ResponseEntity<ApiResponse<ScanResponse>> scan(
            @Valid @RequestBody ScanRequest request) {
        featureGuard.requireModule("security");

        var tenantId = TenantContext.getTenantIdAsObject();

        // ── Bug #13 fix ────────────────────────────────────────────────────────
        // The original implementation passed req.guardId() directly to the service.
        // This means any authenticated user with a valid tenant token could submit
        // a scan claiming to be a DIFFERENT guard — the classic "ghost guard" fraud
        // where a colleague scans on someone else's behalf from across the city.
        //
        // Fix: resolve the guard identity from the authenticated session.
        // TenantContext.getCurrentUserId() returns the UUID of the principal who
        // made this HTTP request — the guard who is actually holding the phone.
        //
        // WHY keep guardId in ScanRequest at all?
        // For the admin web UI: a supervisor creating a manual scan on behalf of a guard
        // needs to specify guardId. We pass it separately to the service, which applies
        // different validation depending on whether the caller is a guard or a supervisor.
        // For now (Phase 0): always use the session identity. Phase 1: add role check.
        UUID authenticatedGuardId = TenantContext.getCurrentUserId();

        var result = scanService.scan(tenantId, request, authenticatedGuardId);
        return ResponseEntity.ok(ApiResponse.success("Checkpoint scanned", result));
    }
}
// Note: append above the final closing brace — done via separate file below
