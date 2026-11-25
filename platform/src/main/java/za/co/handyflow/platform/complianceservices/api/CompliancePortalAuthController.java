package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.complianceservices.application.internal.CompliancePortalAuthService;
import za.co.handyflow.platform.complianceservices.dto.PortalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * Open (no staff login) by design: {@code /portal/auth/**} is permitted in SecurityConfig and rate-limited in
 * RateLimitFilter (register 5 an hour, login 10 in 10 minutes), like every other client portal.
 */
@RestController
@RequestMapping("/api/v1/compliance-services/portal")
@RequiredArgsConstructor
@Tag(name = "Compliance Services Client Portal", description = "Client-facing portal sign-in")
public class CompliancePortalAuthController {

    private final CompliancePortalAuthService auth;

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created", auth.register(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in", auth.login(req.email(), req.password())));
    }
}
