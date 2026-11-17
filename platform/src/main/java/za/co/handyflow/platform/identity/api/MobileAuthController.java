package za.co.handyflow.platform.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.identity.application.IdentityFacade;
import za.co.handyflow.platform.identity.application.internal.RefreshTokenService;
import za.co.handyflow.platform.identity.dto.request.LoginRequest;
import za.co.handyflow.platform.identity.dto.request.MobileLoginRequest;
import za.co.handyflow.platform.identity.dto.request.MobileRefreshRequest;
import za.co.handyflow.platform.identity.dto.response.AuthResponse;
import za.co.handyflow.platform.identity.dto.response.MobileSession;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * Sign-in for native apps (B1). It uses the same login and the same refresh-token rotation, reuse detection and
 * revocation as the web, and only differs in where the refresh token travels: the body, not a cookie.
 * The web endpoints are untouched.
 */
@RestController
@RequestMapping("/api/v1/auth/mobile")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Native app sign-in and refresh")
public class MobileAuthController {

    private final IdentityFacade identityFacade;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    @Operation(summary = "Native app login: returns the access token and a refresh token in the body")
    public ResponseEntity<ApiResponse<MobileSession>> login(@Valid @RequestBody MobileLoginRequest req, HttpServletRequest http) {
        AuthResponse a = identityFacade.login(new LoginRequest(req.email(), req.password(), req.tenantSlug()));
        RefreshTokenService.IssuedToken t = refreshTokenService.issue(a.userId(), a.tenantId(), req.deviceId(), ip(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Login successful", new MobileSession(
                a.accessToken(), a.tokenType(), a.expiresIn(), a.userId(), a.tenantId(), a.email(), a.firstName(), a.lastName(),
                a.permissions(), a.subscriptionStatus(), t.rawToken(), t.expiresAt())));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token. The refresh token rotates: keep the new one, the old one is dead.")
    public ResponseEntity<ApiResponse<MobileSession>> refresh(@Valid @RequestBody MobileRefreshRequest req, HttpServletRequest http) {
        RefreshTokenService.RefreshResult r = refreshTokenService.refresh(req.refreshToken(), req.deviceId(), ip(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Session refreshed", new MobileSession(
                r.accessToken(), "Bearer", r.accessTokenExpiresInSeconds(), r.userId(), r.tenantId(), r.email(), r.firstName(), r.lastName(),
                r.permissions(), null, r.newRawRefreshToken(), r.newRefreshTokenExpiresAt())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revokes this phone's refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody MobileRefreshRequest req) {
        refreshTokenService.revokeToken(req.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out", null));
    }

    private static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
