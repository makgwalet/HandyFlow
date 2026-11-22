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
import za.co.handyflow.platform.security.domain.model.SecurityDevice;
import za.co.handyflow.platform.security.domain.model.DeviceActivationCode;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.GuardTokenRepository;
import za.co.handyflow.platform.security.domain.repository.SecurityDeviceRepository;
import za.co.handyflow.platform.security.domain.repository.DeviceActivationCodeRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * GuardAuthService — handles guard authentication, enrollment, and token lifecycle.
 *
 * FIX (guard device lockdown, product owner's own explicit design): a
 * real, separate finding made while building the requested device-state/
 * replacement feature — Guard.registeredDeviceId was set at enrollment
 * but login() never actually checked it against the calling device.
 * "Ensure revoked devices cannot authenticate" (the product owner's own
 * requirement) was structurally impossible until this was fixed — see
 * login()'s own step 4.5 for the enforcement, added in a
 * backward-compatible way (only enforced when registeredDeviceId is
 * already set, so a guard enrolled without ever binding a device stays
 * unrestricted). SecurityDevice now also carries a guardId and a full
 * state machine (PENDING/ACTIVE/REVOKED/LOST/BLOCKED/REPLACED/
 * DECOMMISSIONED) for genuine per-guard device history, and
 * initiateDeviceReplacement()/activateDeviceReplacement() implement the
 * supervisor-authorized replacement workflow the product owner
 * specified in place of open self-registration.
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
    // FIX (guard device lockdown, product owner's own explicit design):
    // see this class's own updated header comment for the fuller story.
    private final SecurityDeviceRepository         deviceRepository;
    private final DeviceActivationCodeRepository   activationCodeRepository;

    /** Optional, so existing constructor callers keep working. Used to slow PIN guessing on a locked account. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.RateLimiter rateLimiter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TenantAccessService tenantAccess;

    /** Used to close the outgoing guard's session on a shared-device handover (optional so existing constructors keep working). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.domain.repository.DeviceSessionRepository sessionRepository;

    private final java.security.SecureRandom random = new java.security.SecureRandom();

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

        // 2.5. Shared site device? A registered, active shared device (a phone kept at a post) is used by whichever guard
        //      is on duty: the guard's own personal-device tie does not apply there, and neither does its lock bypass.
        var requestDevice = req.deviceId() == null ? java.util.Optional.<SecurityDevice>empty()
                : deviceRepository.findByTenantIdAndDeviceHardwareId(guard.getTenantId(), req.deviceId());
        boolean sharedLogin = SharedDeviceRules.isSharedLogin(requestDevice.isPresent(),
                requestDevice.map(d -> d.getDeviceType() == SecurityDevice.DeviceType.SHARED_SITE_DEVICE).orElse(false),
                requestDevice.map(SecurityDevice::isActive).orElse(false));
        String tiedDevice = SharedDeviceRules.effectiveRegisteredDevice(sharedLogin, guard.getRegisteredDeviceId());

        // 3. Locked? Only the guard's own enrolled device may still try, and only a few times, so a stranger who knows
        //    the phone number cannot keep a guard out of their account.
        boolean locked = guard.isPinLocked();
        if (LoginRules.lockBlocks(locked, tiedDevice, req.deviceId())
                || (locked && rateLimiter != null
                    && !rateLimiter.tryConsume("guard:locked-device:" + guard.getId(), 10, LOCKOUT_MINUTES * 60_000L))) {
            throw new HandyFlowException(
                    "Too many incorrect PINs. Account locked for " + LOCKOUT_MINUTES + " minutes.",
                    HttpStatus.TOO_MANY_REQUESTS, "PIN_LOCKED");
        }

        // 4. Verify PIN
        if (!BCrypt.checkpw(req.pin(), guard.getPinHash())) {
            if (locked) {   // from the enrolled device: refuse without extending the lock
                throw new HandyFlowException(invalidCredentialsMessage(req), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
            }
            boolean nowLocked = guard.recordPinFailure();
            guardRepository.save(guard);
            String msg = nowLocked
                    ? "Too many incorrect PINs. Account locked for " + LOCKOUT_MINUTES + " minutes."
                    : invalidCredentialsMessage(req);
            throw new HandyFlowException(msg, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        // 4.5. FIX (guard device lockdown, product owner's own explicit
        // design — "ensure revoked devices cannot authenticate"):
        // registeredDeviceId was previously captured at enrollment but
        // never actually checked here — see this class's own header
        // comment for the fuller story on why this is a real, separate
        // finding, not just the feature the product owner asked for.
        // Backward-compatible by construction: only enforced when
        // registeredDeviceId is actually set (a guard enrolled without
        // ever binding a device stays unrestricted, matching today's
        // behaviour exactly), and only when req.deviceId() is present —
        // a caller that doesn't send one can't be checked against.
        switch (DeviceLock.check(tiedDevice, req.deviceId())) {
            case MISMATCH -> throw new HandyFlowException(
                    "This account is enrolled to a different device. Contact your supervisor to replace your device.",
                    HttpStatus.FORBIDDEN, "DEVICE_MISMATCH");
            // Leaving the device id out used to skip the lock entirely.
            case REQUIRED -> throw new HandyFlowException(
                    "This account is locked to a device. Log in from the app on your enrolled device.",
                    HttpStatus.FORBIDDEN, "DEVICE_REQUIRED");
            case OK -> { }
        }
        if (req.deviceId() != null) {
            deviceRepository.findByTenantIdAndDeviceHardwareId(guard.getTenantId(), req.deviceId())
                    .filter(d -> !d.canAuthenticate())
                    .ifPresent(d -> {
                        throw new HandyFlowException(
                                "This device is " + d.getStatus() + " and can no longer be used. Contact your supervisor.",
                                HttpStatus.FORBIDDEN, "DEVICE_" + d.getStatus());
                    });
        }

        // 4.9. Company standing. A cancelled tenant cannot log in at all; a suspended one can (so a guard mid-shift can
        //      still close out) but GuardJwtFilter then limits what the session may do.
        if (tenantAccess != null
                && tenantAccess.accessFor(guard.getTenantId().getValue()) == TenantAccessRules.Access.BLOCKED) {
            throw new HandyFlowException(
                    "Your company's HandyFlow account is no longer active. Contact your supervisor.",
                    HttpStatus.FORBIDDEN, "TENANT_SUSPENDED");
        }

        // 5. Guard status — only ACTIVE guards can start a session
        if (!"ACTIVE".equals(guard.getStatus())) {
            throw new HandyFlowException(
                    "Your account is currently " + guard.getStatus() +
                            ". Contact your supervisor.",
                    HttpStatus.FORBIDDEN, "GUARD_NOT_ACTIVE");
        }

        // 5.5. Shared device handover: whoever was signed in on this device is signed out now. Done only after this guard
        //      has passed every check, so a failed attempt never kicks the guard on duty.
        if (sharedLogin) {
            int ended = tokenRepository.revokeActiveOnDevice(guard.getTenantId(), req.deviceId(), Instant.now(),
                    "Shared device handover");
            if (ended > 0) log.info("[Security] Shared device handover device={} sessionsEnded={}", req.deviceId(), ended);
            // The previous guard's device session must end too, or the next guard cannot start one on this device.
            // Their shift is left as it is (the overtime/no-show checks flag it) and anything still in their custody stays
            // checked out to them: a handover ends the session, it does not pretend resources were returned.
            if (sessionRepository != null && requestDevice.isPresent()) {
                sessionRepository.findOpenByDevice(requestDevice.get().getId())
                        .filter(open -> HandoverRules.shouldClose(open.getGuardId(), guard.getId()))
                        .ifPresent(open -> {
                            open.forceClose(guard.getId(), HandoverRules.REASON);
                            sessionRepository.save(open);
                            log.warn("[Security] Shared device handover closed session={} of guard={} (next guard={})",
                                    open.getId(), open.getGuardId(), guard.getId());
                        });
            }
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
            // A phone can belong to guards in more than one tenant; the PIN says which account is meant.
            var pick = LoginRules.pickByPhone(guardRepository.findAllActiveByPhone(req.phone()),
                    g -> g.getPinHash() != null && req.pin() != null && BCrypt.checkpw(req.pin(), g.getPinHash()));
            return switch (pick.kind()) {
                case ONE -> pick.guard();
                case AMBIGUOUS -> throw new HandyFlowException(
                        "This phone number is registered more than once. Log in with your employee code instead.",
                        HttpStatus.CONFLICT, "AMBIGUOUS_IDENTIFIER");
                case NONE -> throw new HandyFlowException(
                        "Invalid phone number or PIN", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
            };
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
    public GuardEnrollResponse enroll(TenantId tenantId, UUID guardId, GuardEnrollRequest req,
                                      UUID supervisorId) {
        // Tenant-scoped: a guard id from another tenant is "not found" (security audit 1.3).
        Guard guard = guardRepository.findByIdAndTenantForAuth(guardId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));

        // The supervisor no longer chooses the guard's PIN: one is generated, shown once, and must be changed at first
        // login. A PIN sent by an older client is still accepted (unless it is one of the first guesses) but must also be
        // changed at first login, so the supervisor never knows the PIN the guard actually uses.
        String suppliedPin = req.pin();
        if (suppliedPin != null && PinRules.weak(suppliedPin)) {
            throw new HandyFlowException("That PIN is too easy to guess. Leave it blank to generate one.",
                    HttpStatus.BAD_REQUEST, "WEAK_PIN");
        }
        String temporaryPin = suppliedPin == null ? PinRules.generate(random) : null;
        String pinHash = BCrypt.hashpw(suppliedPin != null ? suppliedPin : temporaryPin, BCrypt.gensalt(12));

        Instant pinExpiresAt = req.pinExpiryDays() != null
                ? Instant.now().plus(req.pinExpiryDays(), ChronoUnit.DAYS)
                : Instant.now().plus(90, ChronoUnit.DAYS);

        guard.setPinHash(pinHash, pinExpiresAt);

        // A face template is kept only with the guard's recorded consent, and only if it looks like a template.
        var faceDecision = FaceDataRules.decide(req.faceEmbeddingBase64(), req.faceConsent());
        if (faceDecision == FaceDataRules.Decision.REJECT_INVALID) {
            throw new HandyFlowException("The face data is not a valid template",
                    HttpStatus.BAD_REQUEST, "INVALID_FACE_DATA");
        }
        boolean faceStored = faceDecision == FaceDataRules.Decision.STORE;
        if (faceStored) {
            guard.storeFaceEmbedding(req.faceEmbeddingBase64().trim(), Instant.now());
        } else if (faceDecision == FaceDataRules.Decision.SKIP_NO_CONSENT) {
            log.warn("[Security] Face data sent for guard={} without consent: not stored", guardId);
        }

        String previousDeviceId = guard.getRegisteredDeviceId();
        if (req.deviceHardwareId() != null) {
            // A refusal below throws, and the transaction rolls back the PIN and device changes made above.
            guard.setRegisteredDeviceId(req.deviceHardwareId());

            // FIX (guard device lockdown): the registeredDeviceId string
            // above is kept as the fast lookup login() checks, but this
            // is now also recorded as a proper SecurityDevice row —
            // giving genuine history (multiple rows over a guard's
            // employment, each with its own state) rather than a single
            // field that silently gets overwritten on every
            // re-enrollment with no record of what it used to be.
            // Started ACTIVE, not PENDING — enrollment is already a
            // supervisor-witnessed action (this whole method requires a
            // supervisorId), so there's no separate confirmation step
            // needed the way the activation-code replacement flow below
            // genuinely does.
            var known = deviceRepository.findByTenantIdAndDeviceHardwareId(guard.getTenantId(), req.deviceHardwareId());
            var action = EnrollDeviceRules.decide(known.isPresent(),
                    known.map(d -> d.getDeviceType().name()).orElse(null),
                    known.map(d -> d.getStatus().name()).orElse(null),
                    known.map(SecurityDevice::getGuardId).orElse(null), guard.getId());
            switch (action) {
                case REFUSE_SHARED -> throw new HandyFlowException(
                        "That is a shared site device. Use the shared-device login, not a personal enrolment.",
                        HttpStatus.CONFLICT, "DEVICE_IS_SHARED");
                case REFUSE_UNAVAILABLE -> throw new HandyFlowException(
                        "That device was withdrawn from service (lost, revoked, blocked or decommissioned). Enrol the guard on a different device.",
                        HttpStatus.CONFLICT, "DEVICE_UNAVAILABLE");
                case REFUSE_OTHER_GUARD -> throw new HandyFlowException(
                        "That device is in use by another guard. Replace or revoke it for them first.",
                        HttpStatus.CONFLICT, "DEVICE_IN_USE");
                case CREATE -> {
                    SecurityDevice d = SecurityDevice.createPendingForGuard(
                            guard.getTenantId(), guard.getId(), req.deviceHardwareId(), null);
                    d.activate();
                    deviceRepository.save(d);
                }
                case REBIND -> {
                    SecurityDevice d = known.get();
                    log.info("[Security] Device {} handed from guard {} to guard {}", req.deviceHardwareId(), d.getGuardId(), guard.getId());
                    d.rebindTo(guard.getId());
                    d.activate();
                    deviceRepository.save(d);
                }
                case KEEP -> { known.get().activate(); deviceRepository.save(known.get()); }
            }
            // The guard's earlier phone is no longer the one they use: close its row as REPLACED.
            if (EnrollDeviceRules.retirePrevious(previousDeviceId, req.deviceHardwareId())) {
                deviceRepository.findByTenantIdAndDeviceHardwareId(guard.getTenantId(), previousDeviceId)
                        .filter(d -> guard.getId().equals(d.getGuardId()))
                        .ifPresent(d -> { d.markReplaced(); deviceRepository.save(d); });
            }
        }

        // pinMustChange stays true (set by setPinHash): the guard picks their own PIN at first login.

        guardRepository.save(guard);

        int revoked = tokenRepository.revokeAllForGuard(
                guard.getId(), Instant.now(), "Re-enrollment by supervisor " + supervisorId);

        log.info("[Security] Guard enrolled guardId={} by supervisor={} tokensRevoked={}",
                guardId, supervisorId, revoked);

        return new GuardEnrollResponse(
                guardId, guard.getFullName(),
                faceStored,
                req.deviceHardwareId() != null,
                pinExpiresAt,
                temporaryPin);
    }

    // ── Device Replacement (supervisor-authorized, per the product owner's
    //    own explicit design) ──────────────────────────────────────────────

    /**
     * FIX (guard device lockdown): backs the supervisor-facing "Guard →
     * Devices" history view — every SecurityDevice row this guard has
     * ever been bound to, newest first, with each row's own status
     * (ACTIVE, REVOKED, REPLACED, etc.) so a supervisor can genuinely
     * answer "which device was this guard using when this event
     * occurred?" per the product owner's own stated reason for wanting
     * history at all.
     */
    @Transactional(readOnly = true)
    public List<SecurityDevice> getDeviceHistory(TenantId tenantId, UUID guardId) {
        return deviceRepository.findByGuard(tenantId, guardId);
    }

    /**
     * Supervisor-facing: "Guard loses phone. Guard contacts supervisor.
     * Supervisor initiates: Replace Device." Generates a short-lived,
     * single-use code the supervisor reads to the guard over the phone
     * or shows as a QR — the realistic scenario this exists for. Does
     * NOT touch the guard's current device yet; that only happens when
     * the code is actually redeemed (activateDeviceReplacement()) —
     * issuing a code the guard never uses must not lock them out of
     * their existing, working phone.
     */
    @Transactional
    public DeviceReplacementCodeResponse initiateDeviceReplacement(TenantId tenantId, UUID guardId, UUID supervisorId) {
        Guard guard = guardRepository.findByIdAndTenantForAuth(guardId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));
        DeviceActivationCode code = DeviceActivationCode.issue(guard.getTenantId(), guardId, supervisorId);
        activationCodeRepository.save(code);
        log.info("[Security] Device replacement code issued guardId={} by supervisor={}", guardId, supervisorId);
        return new DeviceReplacementCodeResponse(code.getCode(), code.getExpiresAt());
    }

    /**
     * Guard-facing: the guard enters/scans the code on their new device.
     * Verifies guard + code + expiry + old-device status (per the
     * product owner's own spec) before making any change. On success:
     * old device -> REVOKED, new device -> ACTIVE,
     * Guard.registeredDeviceId repointed — matching the product owner's
     * own "Old device -> REVOKED, New device -> ACTIVE" instruction
     * exactly. All existing sessions for this guard are also revoked,
     * same as full re-enrollment — a device replacement is exactly the
     * kind of event that should force every other active session closed.
     */
    @Transactional
    public GuardEnrollResponse activateDeviceReplacement(String code, String newDeviceHardwareId,
                                                          String newDeviceName) {
        // FIX: this method is deliberately reachable with no prior
        // authentication at all — that IS the point (a guard who just
        // lost their device has no session to authenticate with). The
        // code itself is the only thing scoping the search: lookup is
        // by code alone across all tenants (findUnusedByCodeAnyTenant),
        // never a tenantId trusted from the caller — everything
        // downstream then uses the found row's own tenantId.
        DeviceActivationCode activation = activationCodeRepository.findUnusedByCodeAnyTenant(code)
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid or already-used code", HttpStatus.NOT_FOUND, "INVALID_CODE"));

        try {
            activation.redeem(newDeviceHardwareId);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "CODE_EXPIRED");
        }
        activationCodeRepository.save(activation);

        Guard guard = guardRepository.findByIdForAuth(activation.getGuardId())
                .orElseThrow(() -> new ResourceNotFoundException("Guard", activation.getGuardId().toString()));

        String oldDeviceId = guard.getRegisteredDeviceId();
        if (oldDeviceId != null) {
            deviceRepository.findByTenantIdAndDeviceHardwareId(guard.getTenantId(), oldDeviceId)
                    .ifPresent(SecurityDevice::markReplaced);
        }

        guard.setRegisteredDeviceId(newDeviceHardwareId);
        guardRepository.save(guard);

        SecurityDevice newDevice = SecurityDevice.createPendingForGuard(
                guard.getTenantId(), guard.getId(), newDeviceHardwareId, newDeviceName);
        newDevice.activate();
        deviceRepository.save(newDevice);

        int revoked = tokenRepository.revokeAllForGuard(
                guard.getId(), Instant.now(), "Device replacement — new device enrolled");

        log.info("[Security] Device replaced guardId={} oldDevice={} newDevice={} tokensRevoked={}",
                guard.getId(), oldDeviceId, newDeviceHardwareId, revoked);

        return new GuardEnrollResponse(guard.getId(), guard.getFullName(), false, true, null);
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
        if (PinRules.weak(req.newPin())) {
            throw new HandyFlowException(
                    "That PIN is too easy to guess (no repeated digits, runs like 123456, or repeated pairs)",
                    HttpStatus.BAD_REQUEST, "WEAK_PIN");
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

    /** Supervisor-facing: only for a guard of this tenant (security audit 1.3). Not found otherwise, and nothing is revoked. */
    @Transactional
    public int revokeAllTokens(TenantId tenantId, UUID guardId, String reason) {
        guardRepository.findByIdAndTenantForAuth(guardId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));
        return revokeAllTokens(guardId, reason);
    }

    /** Internal: the caller must already have established that the guard belongs to the current tenant (e.g. GuardService.updateStatus). */
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