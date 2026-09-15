// security/api/GuardSessionController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.DeviceSessionService;
import za.co.handyflow.platform.security.application.internal.GuardLocationService;
import za.co.handyflow.platform.security.domain.model.DeviceSession;
import za.co.handyflow.platform.security.domain.model.ResourceCustody;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * GAP-01 fix, highest-leverage piece: the foundational "start my shift"
 * flow. Every other guard-facing action (checkpoint scans, incident
 * reports, resource checkout, duress — now fixed separately in
 * GuardDuressController) is meaningless without a guard first being able
 * to open a session at all, which — same routing problem as duress —
 * a guard's own JWT could not reach, since {@code DeviceSessionController}
 * lives under {@code /api/v1/security/**}, outside GuardJwtFilter's
 * {@code /api/v1/guard/**} coverage.
 * <p>
 * Deliberately does NOT change any identity-resolution logic —
 * {@code DeviceSessionService}'s own methods already resolve the acting
 * guard from {@code deviceHardwareId} (open) or the session itself
 * (close/location), matching the exact same device-centric identity
 * pattern {@link GuardGateAccessController} already established for gate
 * access, not from a JWT subject claim. This controller only changes
 * which filter authenticates the request — same fix shape as duress and
 * gate access, applied a third time, consistently.
 * <p>
 * Session lifecycle and location-ping endpoints carry no
 * {@code @PreAuthorize} here either, mirroring {@code
 * DeviceSessionController}'s own confirmed-deliberate posture (see that
 * class's own doc comment) — standard authentication is the only gate;
 * identity/authorization is resolved from device/session state inside the
 * service layer, not a Spring Security authority claim.
 * <p>
 * Resource custody (checkout/return) is included below, completing the
 * migration — checkpoint scanning was migrated separately in
 * {@link GuardCheckpointController} since it lived on its own controller
 * originally.
 */
@Tag(name = "Guard - Sessions", description = "Guard-facing shift lifecycle and GPS location ping (mobile)")
@RestController
@RequestMapping("/api/v1/guard/sessions")
@RequiredArgsConstructor
public class GuardSessionController {

    private final DeviceSessionService sessionService;
    private final GuardLocationService guardLocationService;

    @PostMapping("/open")
    @Operation(
            summary = "Open a guard session on a device (clock in) — guard-facing",
            description = "Same validation and behaviour as the tenant-JWT-surfaced " +
                    "endpoint this mirrors (device ACTIVE, no other session open on this " +
                    "device or for this guard, guard ACTIVE, matches a SCHEDULED shift within " +
                    "a 30-minute window). Identity resolved from deviceHardwareId in the " +
                    "request body, not a JWT claim.")
    public ResponseEntity<ApiResponse<DeviceSessionResponse>> openSession(
            @Valid @RequestBody OpenSessionRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(sessionService.openSession(tenantId, req)));
    }

    @PostMapping("/{sessionId}/close")
    @Operation(
            summary = "Close a guard session (clock out) — guard-facing",
            description = "Same patrol-coverage and resource-return enforcement as the " +
                    "tenant-JWT-surfaced endpoint this mirrors.")
    public ResponseEntity<ApiResponse<DeviceSessionResponse>> closeSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CloseSessionRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.closeSession(tenantId, sessionId, req)));
    }

    @GetMapping("/current")
    @Operation(
            summary = "Get the currently open session on a device — guard-facing",
            description = "Used by the kiosk lock screen to decide whether to show " +
                    "'Start Shift' or the active session's home screen on app launch.")
    public ResponseEntity<ApiResponse<DeviceSession>> getCurrentSession(
            @RequestParam String deviceHardwareId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return sessionService.getCurrentSession(deviceHardwareId, tenantId)
                .map(s -> ResponseEntity.ok(ApiResponse.success(s)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success((DeviceSession) null)));
    }

    @PostMapping("/{sessionId}/location")
    @Operation(
            summary = "Record a GPS ping for the guard on this open session — guard-facing",
            description = "Called roughly every 5 minutes while a session is open. " +
                    "guardId/shiftId/siteId all resolved server-side from the session, never " +
                    "trusted from the request body. Fails with 400 SESSION_NOT_OPEN if the " +
                    "session is closed or doesn't belong to this tenant.")
    public ResponseEntity<ApiResponse<Void>> recordLocationPing(
            @PathVariable UUID sessionId,
            @Valid @RequestBody RecordLocationPingRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        guardLocationService.recordPing(tenantId, sessionId, req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Resource Custody ───────────────────────────────────────────────────────

    @PostMapping("/{sessionId}/resources/checkout")
    @Operation(
            summary = "Check out a resource (radio, key, firearm, vehicle) — guard-facing",
            description = "Optionally requires witnessedBy (a second guard's ID) for " +
                    "high-risk items like firearms, per site configuration. Same posture as " +
                    "the endpoint this mirrors.")
    public ResponseEntity<ApiResponse<ResourceCustody>> checkoutResource(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CheckoutResourceRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        sessionService.checkoutResource(tenantId, sessionId, req)));
    }

    @PostMapping("/resources/{custodyId}/return")
    @Operation(
            summary = "Return a checked-out resource — guard-facing",
            description = "Records condition on return (GOOD/DAMAGED/MISSING). A session " +
                    "cannot close while resources remain checked out unless resourcesReturned " +
                    "is explicitly set on the close request.")
    public ResponseEntity<ApiResponse<ResourceCustody>> returnResource(
            @PathVariable UUID custodyId,
            @Valid @RequestBody ReturnResourceRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.returnResource(tenantId, custodyId, req)));
    }
}
