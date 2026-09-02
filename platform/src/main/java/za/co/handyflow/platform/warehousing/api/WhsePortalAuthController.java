package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.warehousing.application.internal.WhsePortalAuthService;
import za.co.handyflow.platform.warehousing.dto.PortalAuthResponse;
import za.co.handyflow.platform.warehousing.dto.PortalLoginRequest;
import za.co.handyflow.platform.warehousing.dto.PortalRegisterRequest;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * /register and /login live under /auth/** — needs adding to
 * SecurityConfig's permitAll() list AND RateLimitFilter's LIMITS array,
 * matching the exact two additions already made for every other portal
 * auth controller in this codebase (same real precedent as
 * CollAgencyPortalAuthController — not re-verified against the live
 * SecurityConfig/RateLimitFilter files this session, since this session
 * has no write access to apply the change directly; flagged here as an
 * action needed at deploy time, same as the NotificationType patch).
 */
@RestController
@RequestMapping("/api/v1/warehousing/portal")
@RequiredArgsConstructor
@Tag(name = "Warehousing Client Portal", description = "Client-facing portal authentication")
public class WhsePortalAuthController {

    private final WhsePortalAuthService portalAuthService;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> register(@Valid @RequestBody PortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the warehousing client portal")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> login(@Valid @RequestBody PortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }
}
