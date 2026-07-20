package za.co.handyflow.platform.accountant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accountant.application.internal.AccountantPortalAuthService;
import za.co.handyflow.platform.accountant.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.UUID;

/**
 * Closes the "client portal" gap's auth layer.
 * <p>
 * /register and /login live under /auth/** — this exact sub-path is
 * what needs to be added to SecurityConfig's permitAll() list, matching
 * every other module's own public auth-endpoint convention already in
 * that file (/api/v1/admin/auth/login and friends). /accept-invite
 * deliberately sits OUTSIDE /auth/**, so it correctly falls under
 * .anyRequest().authenticated() and requires a valid portal JWT — see
 * PortalJwtFilter.
 */
@RestController
@RequestMapping("/api/v1/accountant/portal")
@RequiredArgsConstructor
@Tag(name = "Accountant Client Portal", description = "Client-facing portal authentication")
public class AccountantPortalAuthController {

    private final AccountantPortalAuthService portalAuthService;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> register(@Valid @RequestBody PortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the client portal")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> login(@Valid @RequestBody PortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }

    @PostMapping("/invites/accept")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Accept an additional invite while already logged in to the portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> acceptAdditionalInvite(
            @Valid @RequestBody AcceptAdditionalInviteRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Invite accepted",
                portalAuthService.acceptAdditionalInvite(getPortalUserId(), req.inviteToken())));
    }

    /** PortalJwtFilter stores the portal user's ID (UUID string) as the Authentication principal. */
    private UUID getPortalUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new HandyFlowException("No portal session", HttpStatus.UNAUTHORIZED, "NO_SESSION");
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }
}