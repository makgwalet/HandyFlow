package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPortalAuthService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalAuthResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalLoginRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalRegisterRequest;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * NOTE: /register and /login live under /auth/** — needs adding to
 * SecurityConfig's permitAll() list AND RateLimitFilter's LIMITS array,
 * the same two additions every other module's own portal-auth controller
 * in this codebase needed (accountant, payroll bureau, recruitment agency,
 * booking agency, collections agency, warehousing, training provider) —
 * flagged here, not applied (no write access to those files this
 * session). No FeatureGuard on these endpoints, matching the confirmed
 * convention of every sibling portal-auth controller (TrainProvPortalAuthController,
 * CollAgencyPortalAuthController): the module gate applies to staff-facing
 * endpoints, not to an unauthenticated client registering/logging in.
 */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/portal")
@RequiredArgsConstructor
@Tag(name = "Facilities Management Client Portal", description = "Client-facing portal authentication")
public class FmPortalAuthController {

    private final FmPortalAuthService portalAuthService;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<FmPortalAuthResponse>> register(@Valid @RequestBody FmPortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the facilities management client portal")
    public ResponseEntity<ApiResponse<FmPortalAuthResponse>> login(@Valid @RequestBody FmPortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }
}
