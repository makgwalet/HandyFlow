package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.legalpractice.application.internal.LpPortalAuthService;
import za.co.handyflow.platform.legalpractice.dto.LoginRequest;
import za.co.handyflow.platform.legalpractice.dto.LpPortalAuthResponse;
import za.co.handyflow.platform.legalpractice.dto.RegisterViaInviteRequest;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * {@code /register-via-invite} and {@code /login} live under
 * {@code /auth/**} — needs adding to {@code SecurityConfig}'s
 * {@code permitAll()} list AND {@code RateLimitFilter}'s LIMITS array,
 * matching the exact two additions already made for every other
 * portal-auth controller this engagement (Accountant/Recruitment Agency/
 * Booking Agency/Payroll Bureau/HR/Auditor). No {@code FeatureGuard} call
 * and no {@code @PreAuthorize} here, deliberately — this is public,
 * pre-authentication, direct mirror of
 * {@code AuditorPortalAuthController}'s own confirmed shape: neither this
 * controller's requests nor {@code PortalJwtFilter} carry a
 * {@code TenantContext} (see that filter's own Javadoc), so
 * {@code featureGuard.requireModule()} would throw before it could ever
 * run, and there is no authenticated principal yet for
 * {@code @PreAuthorize} to check.
 */
@RestController
@RequestMapping("/api/v1/legal-practice/portal")
@RequiredArgsConstructor
@Tag(name = "Legal Practice Client Portal", description = "Client-facing portal authentication")
public class LpPortalAuthController {

    private final LpPortalAuthService portalAuthService;

    @PostMapping("/auth/register-via-invite")
    @Operation(summary = "Register a portal account by redeeming a firm-issued invite token")
    public ResponseEntity<ApiResponse<LpPortalAuthResponse>> registerViaInvite(@Valid @RequestBody RegisterViaInviteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the legal practice client portal")
    public ResponseEntity<ApiResponse<LpPortalAuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in", portalAuthService.login(req.email(), req.password())));
    }
}
