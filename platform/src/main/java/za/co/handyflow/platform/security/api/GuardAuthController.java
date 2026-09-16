// security/api/GuardAuthController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.GuardAuthService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * GuardAuthController — authentication lifecycle for guards (HandyFlow Shield app).
 *
 * Endpoint groups:
 *
 * 1. PUBLIC — no auth required:
 *      POST /api/v1/auth/guard/login          — issue guard JWT
 *
 * 2. GUARD SESSION — requires a valid guard JWT:
 *      POST /api/v1/guard/auth/change-pin     — self-service PIN change
 *      POST /api/v1/guard/auth/logout         — revoke current token
 *
 * 3. SUPERVISOR (tenant JWT + USER_UPDATE) — admin web app:
 *      POST /api/v1/security/guards/{id}/enrol          — enroll guard (set PIN + face + device)
 *      POST /api/v1/security/guards/{id}/revoke-tokens  — force-revoke all guard sessions
 *
 * Spring Security config additions needed:
 *   .requestMatchers("/api/v1/auth/guard/**").permitAll()   // public login
 *   .requestMatchers("/api/v1/guard/**").hasAuthority("SECURITY_GUARD")  // guard-only endpoints
 *
 * The /api/v1/guard/** path is secured by GuardJwtFilter which validates the
 * guard token against security_guard_tokens (revocation check).
 * The /api/v1/security/** path continues to require a standard tenant JWT.
 */
@Tag(name = "Security - Guard Authentication")
@RestController
@RequiredArgsConstructor
public class GuardAuthController {

    private final GuardAuthService guardAuthService;

    // ── 1. PUBLIC — Guard Login ────────────────────────────────────────────────

    @PostMapping("/api/v1/auth/guard/login")
    @Operation(
            summary = "Guard login — issue a guard session JWT",
            description = """
            Authenticates a guard by phone number and PIN.
            Returns a short-lived JWT (13 hours = one shift + buffer) scoped
            to guard-level authorities only (SECURITY_GUARD, SECURITY_SCAN).

            If mustChangePIN = true in the response, the guard app MUST redirect
            to the PIN change screen before allowing any other guard action.

            Failure handling:
            - Wrong PIN: returns 401. After 5 consecutive failures the account
              is locked for 30 minutes (lockout enforced server-side, not by this endpoint).
            - Suspended/terminated guard: returns 403 with status in error message.
            - Not enrolled (no PIN set): returns 403 with enrollment instructions.

            Add to Spring Security permitAll():
                .requestMatchers("/api/v1/auth/guard/**").permitAll()
            """)
    public ResponseEntity<ApiResponse<GuardLoginResponse>> guardLogin(
            @Valid @RequestBody GuardLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Login successful",
                guardAuthService.login(req)));
    }

    // FIX (guard device lockdown, product owner's own explicit design):
    // public for the same reason login() is — a guard who just lost
    // their device has no session to authenticate with. Scoped entirely
    // by the code itself (6-digit, single-use, 15-minute expiry), not by
    // anything the caller supplies about who they are.
    @PostMapping("/api/v1/auth/guard/activate-device")
    @Operation(
            summary = "Redeem a supervisor-issued device replacement code — guard-facing, public",
            description = """
            The guard enters/scans the 6-digit code their supervisor gave them
            after initiating a device replacement. On success: the old device
            is marked REPLACED, the new device is bound and ACTIVE, and every
            existing session for this guard is revoked — same as a full
            re-enrollment.

            Add to Spring Security permitAll():
                .requestMatchers("/api/v1/auth/guard/**").permitAll()
            (already covers this path — same prefix as guard login.)
            """)
    public ResponseEntity<ApiResponse<GuardEnrollResponse>> activateDeviceReplacement(
            @Valid @RequestBody ActivateDeviceReplacementRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Device activated",
                guardAuthService.activateDeviceReplacement(
                        req.code(), req.newDeviceHardwareId(), req.newDeviceName())));
    }

    // ── 2. GUARD SESSION — requires valid guard JWT ────────────────────────────

    @PostMapping("/api/v1/guard/auth/change-pin")
    @Operation(
            summary = "Guard self-service PIN change",
            description = """
            Requires a valid guard session token (not a supervisor token).
            The guard provides their current PIN and new PIN.
            New PIN must be exactly 6 digits and must not match the last 5 PINs.
            On success, all existing guard tokens remain valid — the PIN change
            does not force a logout of other sessions.
            """)
    public ResponseEntity<ApiResponse<Void>> changePIN(
            @Valid @RequestBody GuardChangePinRequest req) {
        UUID guardId = TenantContext.getCurrentUserId();
        guardAuthService.changePIN(guardId, req);
        return ResponseEntity.ok(ApiResponse.success("PIN changed successfully", null));
    }

    @PostMapping("/api/v1/guard/auth/logout")
    @Operation(
            summary = "Guard logout — revoke the current session token",
            description = """
            Revokes the guard's current JWT by marking it revoked in
            security_guard_tokens. Subsequent requests with this token are
            rejected even if the JWT has not expired yet.
            The guard app should delete the token from local storage after calling this.
            """)
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader) {
        // Extract jti from the Authorization header — GuardJwtFilter already validated it
        // The jti is stored in TenantContext as the current token ID by the filter
        // Fallback: parse the JWT to get jti without re-verifying (already verified by filter)
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String jti   = extractJtiFromToken(token);
        if (jti != null) {
            guardAuthService.revokeToken(UUID.fromString(jti));
        }
        return ResponseEntity.ok(ApiResponse.success("Logged out", null));
    }

    // ── 3. SUPERVISOR — Admin web app endpoints ────────────────────────────────

    @PostMapping("/api/v1/security/guards/{id}/enrol")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Enroll a guard — supervisor sets PIN, face embedding, and device",
            description = """
            Supervisor-initiated enrollment. Called from the admin web app when a
            new guard joins or when re-enrollment is needed (PIN compromise, new device).

            Sets:
            - Initial PIN (hashed server-side)
            - Face embedding vector (Base64, produced on-device by Shield app)
            - Registered device hardware ID (for Phase 2 device binding)

            After enrollment:
            - pin_must_change is set to false (supervisor set PIN in person)
            - All existing guard tokens are revoked (clean slate)
            - Guard can immediately log in with the new PIN

            The initial PIN must be communicated to the guard verbally or via SMS
            to their registered phone (not via email or the kiosk device).
            """)
    public ResponseEntity<ApiResponse<GuardEnrollResponse>> enrollGuard(
            @PathVariable UUID id,
            @Valid @RequestBody GuardEnrollRequest req) {
        UUID supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Guard enrolled",
                guardAuthService.enroll(id, req, supervisorId)));
    }

    @PostMapping("/api/v1/security/guards/{id}/initiate-device-replacement")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Issue a device replacement code — supervisor-facing",
            description = """
            Generates a 6-digit, single-use code valid for 15 minutes. Read it
            to the guard over the phone or show it as a QR — does not change
            anything about the guard's current device until the code is
            actually redeemed via POST /api/v1/auth/guard/activate-device, so
            issuing a code the guard never uses cannot lock them out of their
            existing, working phone.
            """)
    public ResponseEntity<ApiResponse<DeviceReplacementCodeResponse>> initiateDeviceReplacement(
            @PathVariable UUID id) {
        UUID supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Replacement code issued",
                guardAuthService.initiateDeviceReplacement(id, supervisorId)));
    }

    @GetMapping("/api/v1/security/guards/{id}/devices")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(
            summary = "This guard's device history — supervisor-facing",
            description = "Every device this guard has ever been bound to, newest first, " +
                    "each with its own status — answers \"which device was this guard using " +
                    "when this event occurred?\"")
    public ResponseEntity<ApiResponse<List<za.co.handyflow.platform.security.domain.model.SecurityDevice>>> getDeviceHistory(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                guardAuthService.getDeviceHistory(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/api/v1/security/guards/{id}/revoke-tokens")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Revoke all active sessions for a guard",
            description = """
            Force-revokes all active guard JWTs for the specified guard.
            Use when:
            - Guard's device is lost or stolen
            - Guard status changes to SUSPENDED (this is also called automatically
              by GuardService.updateStatus() when status → SUSPENDED/TERMINATED)
            - Supervisor needs to force a re-login for any reason

            The guard will be unable to use any existing token immediately.
            They must log in again after their status/device situation is resolved.
            """)
    public ResponseEntity<ApiResponse<RevokeTokensResponse>> revokeAllTokens(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "Supervisor action") String reason) {
        int count = guardAuthService.revokeAllTokens(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Tokens revoked",
                new RevokeTokensResponse(id, count, reason)));
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    /**
     * Extracts the JWT jti claim without full re-verification.
     * The token has already been verified by GuardJwtFilter at this point.
     * We just need the payload to find the jti for revocation.
     */
    private String extractJtiFromToken(String token) {
        try {
            // JWT is: header.payload.signature — Base64 decode the payload
            String[] parts   = token.split("\\.");
            if (parts.length != 3) return null;
            String payload   = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            // Extract jti from JSON without a full JSON parser
            int jtiStart     = payload.indexOf("\"jti\":\"") + 7;
            if (jtiStart < 7) return null;
            int jtiEnd       = payload.indexOf("\"", jtiStart);
            return payload.substring(jtiStart, jtiEnd);
        } catch (Exception e) {
            return null;
        }
    }
}
