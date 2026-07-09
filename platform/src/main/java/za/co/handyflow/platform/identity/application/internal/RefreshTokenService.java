package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.model.RefreshToken;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.identity.domain.repository.RefreshTokenRepository;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.JwtService;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * NEW: the platform previously issued a single 24-hour access token with
 * no refresh mechanism and no revocation path at all — a stolen token was
 * simply valid, in full, for a full day, with nothing anyone could do
 * about it. This is the core of the fix: short-lived access tokens (a
 * separate config change from this class) kept alive via rotation, with
 * real revocation and automatic full-session termination if a
 * already-rotated-away token is ever presented again — the standard
 * signal that a token has been stolen and is being used by two parties
 * at once.
 * <p>
 * Deliberately NOT wired into AuthService/IdentityFacade/UserManagement-
 * Service's existing return types — this is called additively, from the
 * controller layer, after those existing methods return exactly what they
 * always have. See the class-level reasoning in whichever controller
 * calls this for why: changing a cross-module facade's return type risks
 * breaking a caller elsewhere in the platform this class can't see.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository         userRepository;
    private final JwtService jwtService;
    private final RefreshTokenSecuritySweep securitySweep;

    // FIX: was reading app.security.refresh-token.expiration-days (a key
    // that doesn't exist anywhere in application.yaml, silently falling
    // back to a default of 30) instead of the actual
    // app.security.jwt.refresh-expiration-ms property that was already
    // sitting in the real config, evidently intended for exactly this.
    // Two disconnected refresh-expiry settings would otherwise coexist —
    // this one doing nothing, the real one unused.
    @Value("${app.security.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public record IssuedToken(String rawToken, Instant expiresAt) {}

    // Deliberately shaped like AuthResponse (userId/email/permissions and
    // all) rather than just returning a bare new access token. The whole
    // point of doing a fresh User lookup on every refresh (see refresh()
    // below) is that permissions could have changed since original login
    // — if this only returned a token and the frontend kept its old
    // cached user/permissions untouched, the JWT itself would correctly
    // carry fresh permissions for server-side checks, but the frontend's
    // own client-side hasPermission() gate would silently stay stale.
    // Returning the full picture lets the frontend just call the same
    // setAuth(token, user) it already uses after login.
    public record RefreshResult(String accessToken, long accessTokenExpiresInSeconds,
                                UUID userId, UUID tenantId, String email,
                                String firstName, String lastName, java.util.Set<String> permissions,
                                String newRawRefreshToken, Instant newRefreshTokenExpiresAt) {}

    // ── Issue (called after login/register/accept-invitation) ────────────────

    @Transactional
    public IssuedToken issue(UUID userId, UUID tenantId, String deviceFingerprint,
                             String ipAddress, String userAgent) {
        String rawToken = generateRawToken();
        RefreshToken rt = RefreshToken.create(
                userId, tenantId, hash(rawToken),
                deviceFingerprint, ipAddress, userAgent, refreshExpirationMs);
        refreshTokenRepository.save(rt);
        return new IssuedToken(rawToken, rt.getExpiresAt());
    }

    // ── Refresh (rotation + reuse/theft detection) ────────────────────────────

    @Transactional
    public RefreshResult refresh(String rawToken, String deviceFingerprint,
                                 String ipAddress, String userAgent) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid session. Please log in again.",
                        HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));

        if (existing.isRevoked()) {
            // A token that was already rotated away is being presented
            // again — the legitimate client should never do this, since
            // it always moves forward to the token it was given in
            // exchange. This means either the original token or its
            // replacement has leaked to a second party, and we can't tell
            // which. The only safe response is to kill every active
            // session for this user, not just this one token, and force
            // a fresh login everywhere.
            // FIX: was calling the repository directly here, then
            // throwing on the very next line within the same
            // @Transactional method — the throw's rollback silently
            // undid this revocation too. Confirmed via real testing: a
            // token that should have died here kept working afterward.
            // See RefreshTokenSecuritySweep's own comment for the full
            // mechanism.
            int revoked = securitySweep.revokeAllActiveSessionsForUser(existing.getUserId());
            log.error("SECURITY: refresh token reuse detected for user={} tenant={} — revoked {} active session(s)",
                    existing.getUserId(), existing.getTenantId(), revoked);
            throw new HandyFlowException(
                    "This session has been terminated for security reasons. Please log in again.",
                    HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSE_DETECTED");
        }

        if (existing.isExpired()) {
            throw new HandyFlowException("Session expired. Please log in again.",
                    HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED");
        }

        // Fresh lookup, not a reuse of whatever was true at original
        // login — a user's role or permissions could have changed, or
        // they could have been deactivated, since they last authenticated.
        // A stale access token living up to 24h was already a real risk
        // here; refresh is the one place this platform can actually
        // correct course without waiting for that token to expire.
        User user = userRepository.findByIdAndTenantId(existing.getUserId(), TenantId.of(existing.getTenantId()))
                .orElseThrow(() -> new HandyFlowException(
                        "User not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (!user.isActive()) {
            // Same fix as the reuse-detection branch above — this must
            // survive the exception thrown immediately after it.
            securitySweep.revokeAllActiveSessionsForUser(user.getId());
            throw new HandyFlowException("This account has been deactivated",
                    HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE");
        }

        existing.recordUse();

        // Rotate: a brand new refresh token is issued, and this one is
        // marked consumed — it can never be exchanged for an access token
        // again. If it's ever presented after this point, that's the
        // reuse branch above firing on whatever call comes next.
        String newRawToken = generateRawToken();
        String newHash = hash(newRawToken);
        RefreshToken newToken = RefreshToken.create(
                user.getId(), existing.getTenantId(), newHash,
                deviceFingerprint, ipAddress, userAgent, refreshExpirationMs);
        refreshTokenRepository.save(newToken);

        existing.revoke(newHash);
        refreshTokenRepository.save(existing);

        String newAccessToken = jwtService.generateToken(
                user.getId(), existing.getTenantId(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getPermissionNames());

        return new RefreshResult(
                newAccessToken, jwtService.getExpirationSeconds(),
                user.getId(), existing.getTenantId(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getPermissionNames(),
                newRawToken, newToken.getExpiresAt());
    }

    // ── Revocation ─────────────────────────────────────────────────────────────

    /** "Sign out everywhere" — explicit user action, not a security response. */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        // Routed through the same class as the security-response paths
        // above, even though this specific method doesn't throw
        // afterward today — consistency matters here specifically,
        // since the whole bug just fixed was "this looked fine until an
        // exception got added nearby." Better that every revoke-all call
        // site uses the one mechanism proven to survive that, rather
        // than some going through it and some going direct to the
        // repository.
        int revoked = securitySweep.revokeAllActiveSessionsForUser(userId);
        log.info("Revoked {} active session(s) for user={}", revoked, userId);
    }

    /** Single-device logout — revokes only the one token presented, leaving other devices signed in. */
    @Transactional
    public void revokeToken(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(rt -> !rt.isRevoked())
                .ifPresent(rt -> {
                    rt.revoke();
                    refreshTokenRepository.save(rt);
                });
    }

    @Transactional(readOnly = true)
    public long countActiveSessions(UUID userId) {
        return refreshTokenRepository.countActiveSessionsForUser(userId, Instant.now());
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    // Same 64-char random-token convention already used throughout this
    // codebase (PasswordResetToken, UserInvitation both do exactly this).
    private String generateRawToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    // SHA-256 — never store the raw token anywhere. A DB read (backup
    // leak, read-replica exposure, SQL injection limited to SELECT)
    // should not by itself be enough to forge a session.
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM — this branch is
            // unreachable in practice, but the checked exception still
            // needs handling.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}