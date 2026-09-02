package za.co.handyflow.platform.collectionsagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyPortalAuthService;
import za.co.handyflow.platform.collectionsagency.dto.PortalAuthResponse;
import za.co.handyflow.platform.collectionsagency.dto.PortalLoginRequest;
import za.co.handyflow.platform.collectionsagency.dto.PortalRegisterRequest;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * /register and /login live under /auth/** — needs adding to
 * SecurityConfig's permitAll() list AND RateLimitFilter's LIMITS array,
 * matching the exact two additions already made for every other portal
 * auth controller in this codebase (Accountant/Booking Agency/
 * Recruitment Agency/Payroll Bureau). Uses the /auth/register and
 * /auth/login shape directly, not a bare /register — same resolved
 * convention every sibling controller already uses.
 */
@RestController
@RequestMapping("/api/v1/collections-agency/portal")
@RequiredArgsConstructor
@Tag(name = "Collections Agency Client Portal", description = "Client-facing portal authentication")
public class CollAgencyPortalAuthController {

    private final CollAgencyPortalAuthService portalAuthService;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> register(@Valid @RequestBody PortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the collections agency client portal")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> login(@Valid @RequestBody PortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }
}
