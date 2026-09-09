package za.co.handyflow.platform.property.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.property.application.internal.PropPortalAuthService;
import za.co.handyflow.platform.property.dto.PortalAuthResponse;
import za.co.handyflow.platform.property.dto.PortalLoginRequest;
import za.co.handyflow.platform.property.dto.PortalRegisterRequest;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * /register and /login live under /auth/** — added to SecurityConfig's
 * permitAll() list AND RateLimitFilter's LIMITS array as part of this
 * same change (confirmed directly by checking, not just assumed —
 * unlike the four sibling portals whose equivalent entries were found
 * missing earlier this session).
 */
@RestController
@RequestMapping("/api/v1/property/portal")
@RequiredArgsConstructor
@Tag(name = "Property Tenant Portal", description = "Tenant-facing portal authentication")
public class PropPortalAuthController {

    private final PropPortalAuthService portalAuthService;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> register(@Valid @RequestBody PortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the tenant portal")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> login(@Valid @RequestBody PortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }
}
