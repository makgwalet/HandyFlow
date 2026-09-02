package za.co.handyflow.platform.bookkeeping.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.bookkeeping.application.internal.BkPortalAuthService;
import za.co.handyflow.platform.bookkeeping.dto.BkPortalAuthResponse;
import za.co.handyflow.platform.bookkeeping.dto.BkPortalLoginRequest;
import za.co.handyflow.platform.bookkeeping.dto.BkPortalRegisterRequest;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * NOTE: /register and /login live under /auth/** — needs adding to
 * SecurityConfig's permitAll() list AND RateLimitFilter's LIMITS array,
 * the same two additions every other module's own portal-auth controller
 * in this codebase needed — flagged here, not applied (no write access to
 * those files this session). No FeatureGuard on these endpoints, matching
 * the confirmed convention of every sibling portal-auth controller (
 * FmPortalAuthController, TrainProvPortalAuthController): the module gate
 * applies to staff-facing endpoints, not to an unauthenticated client
 * registering/logging in.
 */
@RestController
@RequestMapping("/api/v1/bookkeeping/portal")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping Client Portal", description = "Client-facing portal authentication")
public class BkPortalAuthController {

    private final BkPortalAuthService portalAuthService;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<BkPortalAuthResponse>> register(@Valid @RequestBody BkPortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the bookkeeping client portal")
    public ResponseEntity<ApiResponse<BkPortalAuthResponse>> login(@Valid @RequestBody BkPortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }
}
