package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.supplychain.application.internal.SupplierPortalAuthService;
import za.co.handyflow.platform.supplychain.dto.SupplierPortalDtos.AuthResponse;
import za.co.handyflow.platform.supplychain.dto.SupplierPortalDtos.LoginRequest;
import za.co.handyflow.platform.supplychain.dto.SupplierPortalDtos.RegisterRequest;

/**
 * Public by design: /register and /login cannot require a login. They live under /auth/**, which needs a permitAll entry in
 * SecurityConfig and a rate limit in RateLimitFilter, like every other portal. The invite token is the proof of who is registering.
 */
@RestController
@RequestMapping("/api/v1/supplier/portal/auth")
@RequiredArgsConstructor
@Tag(name = "Supplier Portal", description = "Supplier-facing authentication")
public class SupplierPortalAuthController {

    private final SupplierPortalAuthService service;

    @PostMapping("/register")
    @Operation(summary = "Create a supplier portal account from an invite token")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                service.register(req.inviteToken(), req.password(), req.fullName())));
    }

    /** inviteToken is optional: present when an existing portal user accepts a further invite while logging in. */
    @PostMapping("/login")
    @Operation(summary = "Log in to the supplier portal")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginWithInvite req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in", service.login(req.email(), req.password(), req.inviteToken())));
    }

    public record LoginWithInvite(String email, String password, String inviteToken) {}
}
