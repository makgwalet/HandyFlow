package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvPortalAuthService;
import za.co.handyflow.platform.trainingprovider.dto.PortalAuthResponse;
import za.co.handyflow.platform.trainingprovider.dto.PortalLoginRequest;
import za.co.handyflow.platform.trainingprovider.dto.PortalRegisterRequest;

/**
 * NOTE: /register and /login live under /auth/** — needs adding to
 * SecurityConfig's permitAll() list AND RateLimitFilter's LIMITS array,
 * the same two additions every other module's own portal-auth
 * controller in this codebase needed (accountant, payroll bureau,
 * recruitment agency, booking agency, collections agency, warehousing)
 * — flagged here, not applied (no write access to those files this
 * session).
 */
@RestController
@RequestMapping("/api/v1/training-provider/portal")
@RequiredArgsConstructor
@Tag(name = "Training Provider Client Portal", description = "Client-facing portal authentication")
public class TrainProvPortalAuthController {

    private final TrainProvPortalAuthService portalAuthService;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> register(@Valid @RequestBody PortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the training provider client portal")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> login(@Valid @RequestBody PortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }
}
