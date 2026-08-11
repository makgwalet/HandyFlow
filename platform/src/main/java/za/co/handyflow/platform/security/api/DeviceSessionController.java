// security/api/DeviceSessionController.java

package za.co.handyflow.platform.security.api;

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
 * DeviceSessionController — Phase 2 session lifecycle and resource custody.
 *
 * CHANGE: added POST /{sessionId}/location -- GPS ping ingestion (backend
 * pass 1 of the real-GPS-map feature; see GuardLocationService for the full
 * design rationale). Guard-facing, same access pattern as checkpoint scans
 * and resource checkout: no explicit @PreAuthorize beyond the standard
 * tenant-scoped JwtAuthFilter, matching every other guard-facing endpoint
 * on this controller. guardId/shiftId/siteId are resolved server-side from
 * the session, never trusted from the request body.
 */
@Tag(name = "Security - Device Sessions")
@RestController
@RequestMapping("/api/v1/security/sessions")
@RequiredArgsConstructor
public class DeviceSessionController {

    private final DeviceSessionService  sessionService;
    private final GuardLocationService  guardLocationService;

    // ── Queries ────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(
            summary = "List device sessions for this tenant — paginated, newest first",
            description = "Used by the admin web app's Device Sessions tab to show active " +
                    "(open) and recent (closed) sessions.")
    public ResponseEntity<ApiResponse<Page<DeviceSessionResponse>>> getSessions(
            @PageableDefault(size = 100) Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.getSessions(tenantId, pageable)));
    }

    @GetMapping("/current")
    @Operation(
            summary = "Get the currently open session on a device",
            description = "Used by the kiosk lock screen to determine whether to show " +
                    "'Start Shift' or the active session's home screen on app launch.")
    public ResponseEntity<ApiResponse<DeviceSession>> getCurrentSession(
            @RequestParam String deviceHardwareId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return sessionService.getCurrentSession(deviceHardwareId, tenantId)
                .map(s -> ResponseEntity.ok(ApiResponse.success(s)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success((DeviceSession) null)));
    }

    @GetMapping("/resolve-guard")
    @Operation(
            summary = "Resolve the currently active guard on a device",
            description = """
            Phase 2 identity resolution endpoint — used internally by
            CheckpointScanController and IncidentController to get the
            authenticated guard server-side instead of trusting a JWT claim
            or request body field. Returns empty if no session is open.
            """)
    public ResponseEntity<ApiResponse<UUID>> resolveGuardId(
            @RequestParam String deviceHardwareId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return sessionService.resolveGuardId(deviceHardwareId, tenantId)
                .map(id -> ResponseEntity.ok(ApiResponse.success(id)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success((UUID) null)));
    }

    // ── Session Lifecycle ──────────────────────────────────────────────────────

    @PostMapping("/open")
    @Operation(
            summary = "Open a guard session on a device (clock in)",
            description = """
            Called by the Shield app after PIN entry + face liveness capture.
            Validates: device exists and is ACTIVE, no other session open on this
            device, no other open session for this guard anywhere, guard is ACTIVE.
            Finds the guard's matching SCHEDULED shift within a 30-minute window
            and transitions it to ACTIVE. Generates patrol rounds if a route is
            configured for the site.
            """)
    public ResponseEntity<ApiResponse<DeviceSessionResponse>> openSession(
            @Valid @RequestBody OpenSessionRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(sessionService.openSession(tenantId, req)));
    }

    @PostMapping("/{sessionId}/close")
    @Operation(
            summary = "Close a guard session (clock out)",
            description = """
            Called by the Shield app at end of shift. Enforces minimum patrol
            coverage (loophole #17) — if patrol rounds are missing, the guard
            must provide incompletePatrolReason. Auto-blocks if resources
            (radios/keys/firearms) are still checked out and resourcesReturned
            is false. On success, completes the linked shift.
            """)
    public ResponseEntity<ApiResponse<DeviceSessionResponse>> closeSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CloseSessionRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.closeSession(tenantId, sessionId, req)));
    }

    @PostMapping("/{sessionId}/force-close")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Supervisor force-closes a stuck session",
            description = """
            Use when a guard forgot to clock out and the device is needed by
            the next guard. Logged separately from a normal close (forcedCloseBy
            + forcedCloseReason) so the audit trail shows this wasn't the
            guard's own action. Does NOT enforce patrol coverage checks —
            the supervisor is explicitly overriding, not completing normally.
            """)
    public ResponseEntity<ApiResponse<DeviceSessionResponse>> forceCloseSession(
            @PathVariable UUID sessionId,
            @RequestParam String reason) {
        TenantId tenantId    = TenantContext.getTenantIdAsObject();
        UUID     supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.forceCloseSession(tenantId, sessionId, supervisorId, reason)));
    }

    // ── GPS location ping (new) ──────────────────────────────────────────────

    @PostMapping("/{sessionId}/location")
    @Operation(
            summary = "Record a GPS ping for the guard on this open session",
            description = """
            Called by the guard app roughly every 5 minutes while a session
            is open (backend pass 1 of the real-GPS-map feature -- no read
            endpoint for "current locations" exists yet). guardId, shiftId,
            and siteId are all resolved server-side from the session/device,
            never trusted from the request body, same posture as checkpoint
            scanning. Fails with 400 SESSION_NOT_OPEN if the session is
            closed or doesn't belong to this tenant.
            """)
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
            summary = "Check out a resource (radio, key, firearm, vehicle) for this session",
            description = "Optionally requires witnessedBy (a second guard's ID) for " +
                    "high-risk items like firearms, per site configuration.")
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
            summary = "Return a checked-out resource",
            description = "Records condition on return (GOOD/DAMAGED/MISSING). " +
                    "A session cannot close while resources remain checked out " +
                    "unless resourcesReturned is explicitly set on the close request.")
    public ResponseEntity<ApiResponse<ResourceCustody>> returnResource(
            @PathVariable UUID custodyId,
            @Valid @RequestBody ReturnResourceRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.returnResource(tenantId, custodyId, req)));
    }
}