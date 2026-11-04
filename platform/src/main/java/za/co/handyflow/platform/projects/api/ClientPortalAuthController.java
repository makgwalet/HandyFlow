package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.clientportal.ClientPortalAuthService;
import za.co.handyflow.platform.projects.dto.ClientPortalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * Open endpoints of the Projects client portal: read an invite, accept it, log in. Permitted in SecurityConfig and
 * rate-limited in RateLimitFilter (register 5 an hour, login 10 per 10 minutes, invite-info 30 per 10 minutes per IP).
 * The path holds "/portal/" so the shared PortalJwtFilter handles the logged-in endpoints next to it, and it sits outside
 * /api/v1/projects so the staff project-access interceptor never sees portal calls.
 */
@RestController
@RequestMapping("/api/v1/project-client/portal/auth")
@RequiredArgsConstructor
@Tag(name = "Projects Client Portal - Auth", description = "Invite acceptance and login for client contacts")
public class ClientPortalAuthController {

    private final ClientPortalAuthService auth;

    @GetMapping("/invite-info")
    @Operation(summary = "Who an invite is for, and whether that email already has a portal login")
    public ResponseEntity<ApiResponse<InviteInfo>> inviteInfo(@RequestParam String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer")
                .body(ApiResponse.success(auth.inviteInfo(token)));
    }

    @PostMapping("/register")
    @Operation(summary = "Accept an invite: create a login, or confirm the existing one with its password")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Invite accepted", auth.register(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in to the Projects client portal")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("Logged in", auth.login(req.email(), req.password())));
    }
}
