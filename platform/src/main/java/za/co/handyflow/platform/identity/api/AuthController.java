package za.co.handyflow.platform.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.identity.application.IdentityFacade;
import za.co.handyflow.platform.identity.application.internal.PasswordResetService;
import za.co.handyflow.platform.identity.dto.request.*;
import za.co.handyflow.platform.identity.dto.response.AuthResponse;
import za.co.handyflow.platform.shared.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login and password management")
public class AuthController {

    private final IdentityFacade        identityFacade;
    private final PasswordResetService  passwordResetService;

    @PostMapping("/register")
    @Operation(summary = "Register a new company and owner account")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = identityFacade.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email, password and company slug")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = identityFacade.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // ── B4: Forgot password ───────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email — always returns 200 to prevent user enumeration")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req) {
        // WHY always return 200?
        // If we return 404 when the email isn't found, attackers can enumerate
        // valid email addresses. Always returning success prevents this.
        passwordResetService.requestReset(req);
        return ResponseEntity.ok(ApiResponse.success(
                "If that email is registered, a reset link has been sent.", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using the token from the email link")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req);
        return ResponseEntity.ok(ApiResponse.success(
                "Password reset successfully. Please log in with your new password.", null));
    }
}
