package za.co.handyflow.platform.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.identity.application.IdentityFacade;
import za.co.handyflow.platform.identity.application.internal.PasswordResetService;
import za.co.handyflow.platform.identity.application.internal.RefreshTokenService;
import za.co.handyflow.platform.identity.application.internal.EmailVerificationService;
import za.co.handyflow.platform.identity.dto.request.*;
import za.co.handyflow.platform.identity.dto.response.AuthResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.RefreshCookieUtil;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login and password management")
public class AuthController {

    private final IdentityFacade        identityFacade;
    private final PasswordResetService  passwordResetService;
    // NEW: additive dependencies for the refresh-token architecture.
    // Deliberately called from here, AFTER identityFacade.register()/
    // login() return their existing AuthResponse unchanged — see
    // RefreshTokenService's own class-level comment for why this wasn't
    // wired into IdentityFacade/AuthService directly instead.
    private final RefreshTokenService   refreshTokenService;
    private final RefreshCookieUtil     refreshCookieUtil;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    @Operation(summary = "Register a new company and owner account")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthResponse response = identityFacade.register(request);
        issueRefreshCookie(response, httpRequest, httpResponse);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email, password and company slug")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthResponse response = identityFacade.login(request);
        issueRefreshCookie(response, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // ── Refresh token architecture ─────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the httpOnly refresh cookie for a new access token — rotates the refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = RefreshCookieUtil.COOKIE_NAME, required = false) String rawRefreshToken,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new HandyFlowException("No active session", HttpStatus.UNAUTHORIZED, "NO_REFRESH_TOKEN");
        }

        RefreshTokenService.RefreshResult result = refreshTokenService.refresh(
                rawRefreshToken, extractDeviceFingerprint(httpRequest),
                extractIp(httpRequest), httpRequest.getHeader("User-Agent"));

        // Rotation: the OLD cookie value is now dead (revoked inside
        // refreshTokenService.refresh() itself) — this sets the NEW one.
        ResponseCookie cookie = refreshCookieUtil.build(
                result.newRawRefreshToken(), result.newRefreshTokenExpiresAt());
        httpResponse.addHeader("Set-Cookie", cookie.toString());

        // FIX (shape, not a bug): the raw refresh token and its expiry
        // exist only inside RefreshResult, server-side — never in this
        // response body. It's already been set as an httpOnly cookie
        // above; returning it in JSON too would defeat the entire point
        // of httpOnly by making it readable from JS.
        AuthResponse body = new AuthResponse(
                result.accessToken(), "Bearer", result.accessTokenExpiresInSeconds(),
                result.userId(), result.tenantId(), result.email(),
                result.firstName(), result.lastName(), result.permissions(),
                null // subscriptionStatus: refresh doesn't currently re-check
                // Tenant.status the way login does. Left null rather
                // than guessing — if a suspended/cancelled tenant
                // needs to be caught mid-session (not just at login),
                // that's a real, separate piece of work: this
                // endpoint would need a TenantRepository lookup added
                // alongside the existing User one.
        );
        return ResponseEntity.ok(ApiResponse.success("Session refreshed", body));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out — revokes the current refresh token and clears the cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshCookieUtil.COOKIE_NAME, required = false) String rawRefreshToken,
            HttpServletResponse httpResponse) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeToken(rawRefreshToken);
        }
        httpResponse.addHeader("Set-Cookie", refreshCookieUtil.clear().toString());
        return ResponseEntity.ok(ApiResponse.success("Logged out", null));
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

    // ── Email verification ──────────────────────────────────────────────────
    // NEW feature — deliberately non-blocking. See EmailVerificationService's
    // own class-level comment for why this never gates login or app usage.

    @PostMapping("/verify-email")
    @Operation(summary = "Verify an account's email using the token from the welcome email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest req) {
        emailVerificationService.verifyEmail(req.token());
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully.", null));
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private void issueRefreshCookie(AuthResponse response, HttpServletRequest request,
                                    HttpServletResponse httpResponse) {
        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(
                response.userId(), response.tenantId(),
                extractDeviceFingerprint(request), extractIp(request),
                request.getHeader("User-Agent"));
        ResponseCookie cookie = refreshCookieUtil.build(issued.rawToken(), issued.expiresAt());
        httpResponse.addHeader("Set-Cookie", cookie.toString());
    }

    // Prefers X-Forwarded-For (first hop) when present, since a real
    // deployment sits behind a load balancer/reverse proxy and
    // getRemoteAddr() alone would just report the proxy's own address for
    // every request. Falls back to getRemoteAddr() for direct/local
    // connections where no proxy is in front.
    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // NEW, currently always empty: the actual fingerprint (user-agent +
    // screen resolution + timezone + language, per the original device-
    // binding recommendation) has to be computed client-side in the
    // browser and sent as a header — that's frontend work not yet built.
    // This reads the header now so the backend is ready for it the moment
    // it exists, without needing another round of changes here. Until
    // then, every RefreshToken row's device_fingerprint column is simply
    // null, which is a real, honest gap — not a security regression, but
    // not the mitigation it's meant to be yet either.
    private String extractDeviceFingerprint(HttpServletRequest request) {
        return request.getHeader("X-Device-Fingerprint");
    }
}
