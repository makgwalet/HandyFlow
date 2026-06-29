// security/application/internal/GuardAuthService.java

package za.co.handyflow.platform.security.application.internal;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.GuardToken;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.GuardTokenRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * GuardAuthService — handles guard authentication, enrollment, and token lifecycle.
 *
 * Guard authentication is intentionally separate from the main user authentication
 * (email + password → tenant JWT) for three reasons:
 *
 * 1. Identity shape: guards identify by phone + PIN, not email + password.
 *    Mixing them into the same auth flow would require nullable email/password
 *    on User, or guards being User records — both are wrong architecturally.
 *
 * 2. Authority scope: guard JWTs contain only guard-level authorities
 *    (SECURITY_GUARD, SECURITY_SCAN) and are explicitly rejected by any
 *    endpoint that requires tenant-level authorities (USER_READ, etc.).
 *    This is enforced by the JWT claims: guard tokens carry `guardId` and
 *    `role: GUARD` rather than the `permissions` array that user JWTs carry.
 *
 * 3. Revocability: guard tokens are tracked in security_guard_tokens so
 *    supervisors can invalidate them immediately (stolen device, suspension).
 *    User tokens are not currently tracked (stateless JWTs) — adding tracking
 *    there is a separate concern.
 *
 * Token lifetime: 13 hours (one 12-hour shift + 1 hour buffer for overlap).
 * In Phase 2 this becomes tied to the open device session duration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardAuthService {

    private static final int TOKEN_HOURS      = 13;   // one shift + buffer
    private static final int MAX_PIN_FAILURES = 5;
    private static final int LOCKOUT_MINUTES  = 30;

    private final GuardRepository      guardRepository;
    private final GuardTokenRepository tokenRepository;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    // ── Guard Login ────────────────────────────────────────────────────────────

    /**
     * Authenticates a guard and issues a short-lived JWT.
     *
     * Flow:
     *   1. Find guard by phone (across all tenants — phone is unique per person)
     *   2. Check pin_hash is set (guard has been enrolled)
     *   3. Check pin_locked_until (too many failures)
     *   4. BCrypt verify PIN
     *   5. Check guard status (only ACTIVE guards can log in)
     *   6. Check pin_must_change (force PIN change before issuing session)
     *   7. Issue JWT + persist token record
     *
     * WHY reject non-ACTIVE guards at login rather than just issuing a token?
     * A SUSPENDED guard whose token was missed by the revoke-all job should not
     * be able to refresh their session by logging in again.  The status check at
     * login is the second line of defence after token revocation.
     */
    @Transactional
    public GuardLoginResponse login(GuardLoginRequest req) {
        // 1. Look up by phone
        Guard guard = guardRepository.findActiveByPhone(req.phone())
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid phone number or PIN",
                        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));

        // 2. PIN enrolled?
        if (guard.getPinHash() == null) {
            throw new HandyFlowException(
                    "This guard account has not been enrolled yet. " +
                            "Please contact your supervisor to complete enrollment.",
                    HttpStatus.FORBIDDEN, "NOT_ENROLLED");
        }

        // 3. Locked?
        if (guard.isPinLocked()) {
            throw new HandyFlowException(
                    "Too many incorrect PINs. Account locked for " + LOCKOUT_MINUTES + " minutes.",
                    HttpStatus.TOO_MANY_REQUESTS, "PIN_LOCKED");
        }

        // 4. Verify PIN
        if (!BCrypt.checkpw(req.pin(), guard.getPinHash())) {
            boolean locked = guard.recordPinFailure();
            guardRepository.save(guard);
            String msg = locked
                    ? "Too many incorrect PINs. Account locked for " + LOCKOUT_MINUTES + " minutes."
                    : "Invalid phone number or PIN";
            throw new HandyFlowException(msg, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        // 5. Guard status — only ACTIVE guards can start a session
        if (!"ACTIVE".equals(guard.getStatus())) {
            throw new HandyFlowException(
                    "Your account is currently " + guard.getStatus() +
                            ". Contact your supervisor.",
                    HttpStatus.FORBIDDEN, "GUARD_NOT_ACTIVE");
        }

        // 6. PIN change required?
        if (guard.isPinMustChange()) {
            // Issue a restricted 'must-change' token — the client app must redirect
            // to the PIN change screen before any other guard action is allowed.
            // The token carries mustChangePIN=true in its claims.
            guard.recordPinSuccess();
            guardRepository.save(guard);
            return buildLoginResponse(guard, req.deviceId(), true);
        }

        // 7. PIN expired?
        if (guard.isPinExpired()) {
            throw new HandyFlowException(
                    "Your PIN has expired. Contact your supervisor to reset it.",
                    HttpStatus.FORBIDDEN, "PIN_EXPIRED");
        }

        // All checks passed
        guard.recordPinSuccess();
        guardRepository.save(guard);

        log.info("[Security] Guard login success guardId={} tenantId={}",
                guard.getId(), guard.getTenantId().getValue());

        return buildLoginResponse(guard, req.deviceId(), false);
    }

    // ── Guard Enrollment ───────────────────────────────────────────────────────

    /**
     * Enrolls a guard — sets their initial PIN (supervisor-set), stores the face
     * embedding vector, and registers their device.
     *
     * WHY supervisor-initiated?
     * A guard must never self-enroll.  The supervisor sets the PIN in person
     * so that the first login is supervised — this prevents impersonation at
     * enrollment time, which is the highest-risk moment in any auth lifecycle.
     *
     * Face embedding storage:
     * The embedding is a float vector produced on-device by the Shield app.
     * We store it as a Base64-encoded string here.  Phase 2 will add liveness
     * verification logic; for now we just persist whatever the app sends.
     * The raw capture images are NOT stored — only the mathematical embedding.
     *
     * Idempotent on re-enrollment:
     * A supervisor can re-enroll a guard (new PIN + new face embedding) without
     * deleting and recreating the guard record.  All previous tokens are revoked.
     */
    @Transactional
    public GuardEnrollResponse enroll(UUID guardId, GuardEnrollRequest req,
                                      UUID supervisorId) {
        Guard guard = guardRepository.findByIdForAuth(guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));

        // Set PIN (supervisor-supplied, already hashed by supervisor's client)
        // WHY hash server-side? The supervisor's admin client sends the raw PIN
        // (typed in on a trusted admin device), and we hash it here.  Never store plaintext.
        String pinHash = BCrypt.hashpw(req.pin(), BCrypt.gensalt(12));

        Instant pinExpiresAt = req.pinExpiryDays() != null
                ? Instant.now().plus(req.pinExpiryDays(), ChronoUnit.DAYS)
                : Instant.now().plus(90, ChronoUnit.DAYS);  // default 90-day policy

        guard.setPinHash(pinHash, pinExpiresAt);

        // Store face embedding (Base64 encoded vector from Shield app)
        if (req.faceEmbeddingBase64() != null) {
            guard.setFaceEmbedding(req.faceEmbeddingBase64());
        }

        // Register device
        if (req.deviceHardwareId() != null) {
            guard.setRegisteredDeviceId(req.deviceHardwareId());
        }

        // pin_must_change = false on enrollment (supervisor set it in person)
        guard.clearPinMustChange();

        guardRepository.save(guard);

        // Revoke any existing tokens (clean slate on re-enrollment)
        int revoked = tokenRepository.revokeAllForGuard(
                guard.getId(), Instant.now(), "Re-enrollment by supervisor " + supervisorId);

        log.info("[Security] Guard enrolled guardId={} by supervisor={} tokensRevoked={}",
                guardId, supervisorId, revoked);

        return new GuardEnrollResponse(
                guardId, guard.getFullName(),
                req.faceEmbeddingBase64() != null,
                req.deviceHardwareId() != null,
                pinExpiresAt);
    }

    // ── PIN Change (self-service, requires valid session) ─────────────────────

    /**
     * Called by a guard who knows their current PIN and wants to set a new one.
     * Also called when pin_must_change = true (forced change after supervisor reset).
     */
    @Transactional
    public void changePIN(UUID guardId, GuardChangePinRequest req) {
        Guard guard = guardRepository.findByIdForAuth(guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));

        if (guard.getPinHash() == null) {
            throw new HandyFlowException(
                    "Guard has not been enrolled", HttpStatus.BAD_REQUEST, "NOT_ENROLLED");
        }

        // Verify current PIN
        if (!BCrypt.checkpw(req.currentPin(), guard.getPinHash())) {
            guard.recordPinFailure();
            guardRepository.save(guard);
            throw new HandyFlowException(
                    "Current PIN is incorrect", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        // PIN history check — prevent reuse of last 5 PINs
        if (guard.getPinHistory() != null) {
            String[] history = guard.getPinHistory().replaceAll("[\\[\\]\"]", "").split(",");
            for (String oldHash : history) {
                if (!oldHash.isBlank() && BCrypt.checkpw(req.newPin(), oldHash.trim())) {
                    throw new HandyFlowException(
                            "You cannot reuse one of your last 5 PINs",
                            HttpStatus.BAD_REQUEST, "PIN_REUSE");
                }
            }
        }

        // Validate new PIN format (6 digits)
        if (!req.newPin().matches("\\d{6}")) {
            throw new HandyFlowException(
                    "PIN must be exactly 6 digits", HttpStatus.BAD_REQUEST, "INVALID_PIN_FORMAT");
        }

        String newHash = BCrypt.hashpw(req.newPin(), BCrypt.gensalt(12));
        guard.updatePinWithHistory(newHash);
        guardRepository.save(guard);

        log.info("[Security] Guard PIN changed guardId={}", guardId);
    }

    // ── Token Revocation ───────────────────────────────────────────────────────

    /** Revoke a specific token (logout). */
    @Transactional
    public void revokeToken(UUID jti) {
        tokenRepository.findById(jti).ifPresent(t -> {
            t.revoke("Guard logout");
            tokenRepository.save(t);
        });
    }

    /** Revoke all tokens for a guard — called on suspension or device change. */
    @Transactional
    public int revokeAllTokens(UUID guardId, String reason) {
        int count = tokenRepository.revokeAllForGuard(guardId, Instant.now(), reason);
        log.info("[Security] Revoked {} tokens for guardId={} reason={}", count, guardId, reason);
        return count;
    }

    // ── Token Validation (for GuardJwtFilter) ─────────────────────────────────

    /** Returns the active GuardToken for the given jti, or empty if revoked/expired. */
    @Transactional(readOnly = true)
    public java.util.Optional<GuardToken> validateToken(UUID jti) {
        return tokenRepository.findActive(jti);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private GuardLoginResponse buildLoginResponse(Guard guard, String deviceId,
                                                  boolean mustChangePIN) {
        UUID    jti       = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(TOKEN_HOURS, ChronoUnit.HOURS);

        // Persist token record (makes it revocable)
        GuardToken token = GuardToken.issue(
                guard.getTenantId(), guard.getId(), deviceId, expiresAt);
        // Override the auto-generated id with our jti so they match
        // (GuardToken.id IS the jti — set via reflection-free workaround below)
        tokenRepository.save(token);

        // Build JWT
        String jwt = Jwts.builder()
                .setId(token.getId().toString())               // jti = token record id
                .setSubject(guard.getId().toString())          // sub = guardId
                .claim("tenantId",    guard.getTenantId().getValue().toString())
                .claim("role",        "GUARD")
                .claim("firstName",   guard.getFirstName())
                .claim("lastName",    guard.getLastName())
                .claim("grade",       guard.getGrade())
                .claim("mustChangePIN", mustChangePIN)
                // Guard authorities — explicitly limited vs tenant user permissions
                .claim("authorities", List.of("SECURITY_GUARD", "SECURITY_SCAN"))
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(
                        jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        return new GuardLoginResponse(
                jwt, "Bearer", TOKEN_HOURS * 3600L,
                guard.getId(), guard.getTenantId().getValue(),
                guard.getFullName(), guard.getGrade(),
                guard.getStatus(), mustChangePIN, expiresAt);
    }
}