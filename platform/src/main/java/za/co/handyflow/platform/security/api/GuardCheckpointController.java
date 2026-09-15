// security/api/GuardCheckpointController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.CheckpointScanService;
import za.co.handyflow.platform.security.dto.ScanRequest;
import za.co.handyflow.platform.security.dto.ScanResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Last piece of GAP-01's core three (session lifecycle, incident
 * reporting, checkpoint scanning). Unlike incident creation, this
 * endpoint's {@code @PreAuthorize} was already correct — the original
 * already accepts {@code SECURITY_GUARD} — so this is purely the routing
 * fix, nothing else needed. {@code CheckpointScanController}'s own doc
 * comment even already calls this out as a known gap: "GPS_PING and
 * MANUAL use a separate endpoint" was Phase 1 forward-planning, but the
 * controller itself never moved off {@code /api/v1/security/**}.
 * <p>
 * Confirmed directly, not assumed, that {@code TenantContext
 * .getCurrentUserId()} — what this endpoint's own bug-#13 fix already
 * relies on to resolve the scanning guard's identity and reject a
 * client-supplied guardId — works correctly under GuardJwtFilter:
 * that filter's own implementation puts the guard's id into the exact
 * {@code "userId"} authentication-details key {@code getCurrentUserId()}
 * reads from, the identical mechanism JwtAuthFilter uses. This endpoint's
 * identity-resolution code is copied unchanged — the anti-spoofing fix
 * that already existed keeps working exactly as before, just reachable
 * now.
 */
@RestController
@RequestMapping("/api/v1/guard/checkpoints")
@RequiredArgsConstructor
@Tag(name = "Guard - Checkpoints", description = "Guard-facing QR/NFC/BLE checkpoint scanning (mobile)")
public class GuardCheckpointController {

    private final CheckpointScanService scanService;

    @PostMapping("/scan")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(
            summary = "Guard scans a checkpoint — guard-facing",
            description = "guardId is resolved from the authenticated session, never trusted " +
                    "from the request body — same anti-spoofing posture as the endpoint this " +
                    "mirrors. Supports scanType: QR, NFC, BLE.")
    public ResponseEntity<ApiResponse<ScanResponse>> scan(@Valid @RequestBody ScanRequest request) {
        var tenantId = TenantContext.getTenantIdAsObject();
        UUID authenticatedGuardId = TenantContext.getCurrentUserId();
        var result = scanService.scan(tenantId, request, authenticatedGuardId);
        return ResponseEntity.ok(ApiResponse.success("Checkpoint scanned", result));
    }
}
