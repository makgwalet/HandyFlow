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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * GuardAuthService — handles guard authentication, enrollment, and token lifecycle.
 *
 * CHANGE (V214): login() now resolves the guard via resolveGuardForLogin(),
 * which accepts EITHER phone OR employeeCode as the identifier (previously
 * phone was the only option). This is for the ~1000-guard-per-tenant case
 * where most guards will never have a reliable registered phone number and
 * need to log in with their tenant-issued employee code instead. See the
 * V214 migration and GuardService.generateEmployeeCode() for why employee
 * codes are globally unique (same "resolve identity before we know the
 * tenant" requirement phone lookup already has).
 *
 * Guard authentication is intentionally separate from the main user authentication
 * (email + password → tenant JWT) for three reasons:
 *
 * 1. Identity shape: guards identify by phone/employeeCode + PIN, not email + password.
 * 2. Authority scope: guard JWTs contain only guard-level authorities.
 * 3. Revocability: guard tokens are tracked in security_guard_tokens.
 *
 * Token lifetime: 13 hours (one 12-hour shift + 1 hour buffer for overlap).
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

    @Transactional
    public GuardLoginResponse login(GuardLoginRequest req) {
        // 1. Look up by phone or employee code (V214)
        Guard guard = resolveGuardForLogin(req);

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
                    : invalidCredentialsMessage(req);
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

    /**
     * Resolves the guard identity for login, trying phone first (backward
     * compatible with every existing guard-app build) then falling back to
     * employeeCode. Neither present is a 400, not a 401 -- that's a client
     * bug (missing field), not a failed auth attempt.
     */
    private Guard resolveGuardForLogin(GuardLoginRequest req) {
        if (req.phone() != null && !req.phone().isBlank()) {
            return guardRepository.findActiveByPhone(req.phone())
                    .orElseThrow(() -> new HandyFlowException(
                            "Invalid phone number or PIN",
                            HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        }
        if (req.employeeCode() != null && !req.employeeCode().isBlank()) {
            return guardRepository.findActiveByEmployeeCode(req.employeeCode().trim().toUpperCase())
                    .orElseThrow(() -> new HandyFlowException(
                            "Invalid employee code or PIN",
                            HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        }
        throw new HandyFlowException(
                "Either phone or employeeCode is required to log in",
                HttpStatus.BAD_REQUEST, "MISSING_IDENTIFIER");
    }

    private String invalidCredentialsMessage(GuardLoginRequest req) {
        boolean usedCode = req.phone() == null || req.phone().isBlank();
        return usedCode ? "Invalid employee code or PIN" : "Invalid phone number or PIN";
    }

    // ── Guard Enrollment ───────────────────────────────────────────────────────

    @Transactional
    public GuardEnrollResponse enroll(UUID guardId, GuardEnrollRequest req,
                                      UUID supervisorId) {
        Guard guard = guardRepository.findByIdForAuth(guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));

        String pinHash = BCrypt.hashpw(req.pin(), BCrypt.gensalt(12));

        Instant pinExpiresAt = req.pinExpiryDays() != null
                ? Instant.now().plus(req.pinExpiryDays(), ChronoUnit.DAYS)
                : Instant.now().plus(90, ChronoUnit.DAYS);

        guard.setPinHash(pinHash, pinExpiresAt);

        if (req.faceEmbeddingBase64() != null) {
            guard.setFaceEmbedding(req.faceEmbeddingBase64());
        }

        if (req.deviceHardwareId() != null) {
            guard.setRegisteredDeviceId(req.deviceHardwareId());
        }

        guard.clearPinMustChange();

        guardRepository.save(guard);

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

    @Transactional
    public void changePIN(UUID guardId, GuardChangePinRequest req) {
        Guard guard = guardRepository.findByIdForAuth(guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));

        if (guard.getPinHash() == null) {
            throw new HandyFlowException(
                    "Guard has not been enrolled", HttpStatus.BAD_REQUEST, "NOT_ENROLLED");
        }

        if (!BCrypt.checkpw(req.currentPin(), guard.getPinHash())) {
            guard.recordPinFailure();
            guardRepository.save(guard);
            throw new HandyFlowException(
                    "Current PIN is incorrect", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

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

    @Transactional
    public void revokeToken(UUID jti) {
        tokenRepository.findById(jti).ifPresent(t -> {
            t.revoke("Guard logout");
            tokenRepository.save(t);
        });
    }

    @Transactional
    public int revokeAllTokens(UUID guardId, String reason) {
        int count = tokenRepository.revokeAllForGuard(guardId, Instant.now(), reason);
        log.info("[Security] Revoked {} tokens for guardId={} reason={}", count, guardId, reason);
        return count;
    }

    // ── Token Validation (for GuardJwtFilter) ─────────────────────────────────

    @Transactional(readOnly = true)
    public java.util.Optional<GuardToken> validateToken(UUID jti) {
        return tokenRepository.findActive(jti);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private GuardLoginResponse buildLoginResponse(Guard guard, String deviceId,
                                                  boolean mustChangePIN) {
        Instant expiresAt = Instant.now().plus(TOKEN_HOURS, ChronoUnit.HOURS);

        GuardToken token = GuardToken.issue(
                guard.getTenantId(), guard.getId(), deviceId, expiresAt);
        tokenRepository.save(token);

        String jwt = Jwts.builder()
                .setId(token.getId().toString())
                .setSubject(guard.getId().toString())
                .claim("tenantId",    guard.getTenantId().getValue().toString())
                .claim("role",        "GUARD")
                .claim("firstName",   guard.getFirstName())
                .claim("lastName",    guard.getLastName())
                .claim("grade",       guard.getGrade())
                .claim("mustChangePIN", mustChangePIN)
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