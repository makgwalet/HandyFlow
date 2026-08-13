package za.co.handyflow.platform.recruitmentagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruitmentagency.application.internal.RecruitmentAgencyPortalAuthService;
import za.co.handyflow.platform.recruitmentagency.dto.PortalAuthResponse;
import za.co.handyflow.platform.recruitmentagency.dto.PortalLoginRequest;
import za.co.handyflow.platform.recruitmentagency.dto.PortalRegisterRequest;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * NOTE: /register and /login live under /auth/** — needs adding to
 * SecurityConfig's permitAll() list AND RateLimitFilter's LIMITS array,
 * matching the exact two additions already made for both the accountant
 * portal (Section 43) and Payroll Bureau's portal (Section 55/58) —
 * same shape of public, invite-gated, unauthenticated endpoint, same
 * abuse surface. Also matches accountant's own /auth/accept-invite URL
 * convention (Section 56's resolved decision) — this module's frontend
 * routes should use /auth/accept-invite too, not the bare
 * /accept-invite shape Payroll Bureau's first draft used before that
 * fix.
 */
@RestController
@RequestMapping("/api/v1/recruitment-agency/portal")
@RequiredArgsConstructor
@Tag(name = "Recruitment Agency Client Portal", description = "Client-facing portal authentication")
public class RecruitmentAgencyPortalAuthController {

    private final RecruitmentAgencyPortalAuthService portalAuthService;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> register(@Valid @RequestBody PortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the recruitment agency client portal")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> login(@Valid @RequestBody PortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }
}