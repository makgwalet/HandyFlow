package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.fuel.application.internal.FuelPortalAuthService;
import za.co.handyflow.platform.fuel.dto.PortalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;

/** Needs "/api/v1/fuel/portal/auth/**" in SecurityConfig permitAll and limits in RateLimitFilter (both done in this patch). */
@RestController
@RequestMapping("/api/v1/fuel/portal/auth")
@RequiredArgsConstructor
@Tag(name = "Fuel Customer Portal", description = "Customer-facing authentication")
public class FuelPortalAuthController {

    private final FuelPortalAuthService auth;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created", auth.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in", auth.login(req.email(), req.password())));
    }
}
