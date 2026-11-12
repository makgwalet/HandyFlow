// security/application/internal/DeviceSessionService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.*;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DeviceSessionService — manages the full shift session lifecycle on a device.
 *
 * CHANGE: added getSessions() (tenant-wide paginated list), backing the new
 * GET /sessions endpoint on DeviceSessionController. Closes the audit gap:
 * "DeviceSessionsTab.tsx calls GET /api/v1/security/sessions?size=100...
 * but DeviceSessionController has no such list endpoint... the 'Active'/
 * 'Recent' session views in this tab are always empty in production,
 * silently." Everything else below is unchanged from the original.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    private final SecurityDeviceRepository  deviceRepository;
    private final DeviceSessionRepository   sessionRepository;
    private final ResourceCustodyRepository custodyRepository;
    private final GuardRepository           guardRepository;
    private final ShiftRepository           shiftRepository;
    private final PatrolRoundService        patrolRoundService;
    private final SiteRepository            siteRepository;

    // ── Session Open ───────────────────────────────────────────────────────────

    /**
     * Staff route (tenant JWT): a supervisor opens a session on a guard's behalf, so the
     * guard and the check results come from the request. Unchanged behaviour.
     */
    @Transactional
    public DeviceSessionResponse openSession(TenantId tenantId, OpenSessionRequest req) {
        return doOpen(tenantId, req.deviceHardwareId(), req.guardId(), req.pinVerified(),
                req.faceMatchConfidence() != null
                        ? new BigDecimal(req.faceMatchConfidence().toString()) : null,
                device -> req.geofenceOk());
    }

    /**
     * Guard route (guard token). The guard is the token's subject, never the request body.
     * The PIN was verified by the server when the token was issued, so that flag is set
     * here, not accepted from the phone. The geofence is computed here from the phone's
     * reported position and the site's saved position. The face score is not stored:
     * a score computed on the phone proves nothing (assessment R1).
     */
    @Transactional
    public DeviceSessionResponse openGuardSession(TenantId tenantId, UUID authenticatedGuardId,
                                                  GuardOpenSessionRequest req) {
        return doOpen(tenantId, req.deviceHardwareId(), authenticatedGuardId, true, null,
                device -> evaluateGeofence(tenantId, device, req));
    }

    private Boolean evaluateGeofence(TenantId tenantId, SecurityDevice device,
                                     GuardOpenSessionRequest req) {
        if (device.getSiteId() == null) return null;
        return siteRepository.findActiveById(tenantId, device.getSiteId())
                .map(site -> SessionGeofence.evaluate(site.getLatitude(), site.getLongitude(),
                        req.latitude(), req.longitude(), req.accuracyMetres()))
                .orElse(null);
    }

    private DeviceSessionResponse doOpen(TenantId tenantId, String deviceHardwareId, UUID guardId,
                                         boolean pinVerified, BigDecimal faceConfidence,
                                         java.util.function.Function<SecurityDevice, Boolean> geofence) {

        SecurityDevice device = deviceRepository
                .findByTenantIdAndDeviceHardwareId(tenantId, deviceHardwareId)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityDevice",
                        deviceHardwareId));

        if (!device.isActive()) {
            throw new HandyFlowException(
                    "Device is " + device.getStatus() + " and cannot accept sessions",
                    HttpStatus.FORBIDDEN, "DEVICE_INACTIVE");
        }

        sessionRepository.findOpenByDevice(device.getId()).ifPresent(existing -> {
            throw new HandyFlowException(
                    "A session is already open on this device for guard "
                            + existing.getGuardId() + ". Contact your supervisor to close it.",
                    HttpStatus.CONFLICT, "DEVICE_SESSION_CONFLICT");
        });

        sessionRepository.findOpenByGuard(guardId).ifPresent(existing -> {
            throw new HandyFlowException(
                    "This guard already has an open session on device "
                            + existing.getDeviceId() + ". Close that session first.",
                    HttpStatus.CONFLICT, "GUARD_SESSION_CONFLICT");
        });

        Guard guard = guardRepository.findActiveById(tenantId, guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard",
                        guardId.toString()));

        if (!guard.isSchedulable()) {
            throw new HandyFlowException(
                    "Guard " + guard.getFullName() + " is " + guard.getStatus()
                            + " and cannot start a session",
                    HttpStatus.FORBIDDEN, "GUARD_NOT_SCHEDULABLE");
        }

        Instant now        = Instant.now();
        Instant windowStart = now.minus(30, ChronoUnit.MINUTES);
        Instant windowEnd   = now.plus(30, ChronoUnit.MINUTES);

        List<za.co.handyflow.platform.security.domain.model.Shift> candidateShifts =
                shiftRepository.findScheduledStartingBefore(tenantId, windowEnd)
                        .stream()
                        .filter(s -> s.getGuardId().equals(guardId))
                        .filter(s -> s.getSiteId().equals(device.getSiteId()))
                        .filter(s -> s.getStartAt().isAfter(windowStart))
                        .toList();

        za.co.handyflow.platform.security.domain.model.Shift shift = null;
        if (!candidateShifts.isEmpty()) {
            shift = candidateShifts.get(0);
            shift.start();
            shiftRepository.save(shift);
        }

        DeviceSession session = DeviceSession.open(
                tenantId, device.getId(), guardId,
                shift != null ? shift.getId() : null,
                pinVerified, faceConfidence, geofence.apply(device));
        sessionRepository.save(session);

        if (shift != null && device.getSiteId() != null) {
            patrolRoundService.generateRoundsForShift(tenantId, shift);
        }

        log.info("[Security] Session opened sessionId={} guardId={} deviceId={} shiftId={}",
                session.getId(), guardId, device.getId(),
                shift != null ? shift.getId() : "none");

        return toResponse(session, guard, device, shift);
    }

    // ── Ownership (guard route) ────────────────────────────────────────────────

    /** A guard may act only on their own session. Another guard's session looks like it does not exist. */
    @Transactional(readOnly = true)
    public void assertSessionOwnedBy(TenantId tenantId, UUID sessionId, UUID guardId) {
        sessionRepository.findById(sessionId)
                .filter(s -> s.getTenantId().equals(tenantId) && s.getGuardId().equals(guardId))
                .orElseThrow(() -> new ResourceNotFoundException("DeviceSession", sessionId.toString()));
    }

    @Transactional(readOnly = true)
    public void assertCustodyOwnedBy(TenantId tenantId, UUID custodyId, UUID guardId) {
        custodyRepository.findById(custodyId)
                .filter(c -> c.getTenantId().equals(tenantId) && guardId.equals(c.getGuardId()))
                .orElseThrow(() -> new ResourceNotFoundException("ResourceCustody", custodyId.toString()));
    }

    /** Guard route close: ownership checked, PIN claim taken from the token, face score dropped. */
    @Transactional
    public DeviceSessionResponse closeGuardSession(TenantId tenantId, UUID authenticatedGuardId,
                                                   UUID sessionId, CloseSessionRequest req) {
        assertSessionOwnedBy(tenantId, sessionId, authenticatedGuardId);
        return closeSession(tenantId, sessionId, new CloseSessionRequest(
                true, null, req.handoverNotes(), req.resourcesReturned(), req.incompletePatrolReason()));
    }

    // ── Session Close ──────────────────────────────────────────────────────────

    @Transactional
    public DeviceSessionResponse closeSession(TenantId tenantId, UUID sessionId,
                                              CloseSessionRequest req) {
        DeviceSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("DeviceSession",
                        sessionId.toString()));

        if (session.isClosed()) {
            throw new HandyFlowException(
                    "Session is already closed", HttpStatus.BAD_REQUEST, "SESSION_ALREADY_CLOSED");
        }

        if (session.getShiftId() != null) {
            List<PatrolRound> rounds = patrolRoundService.getRoundsForShift(
                    session.getShiftId());
            long missedRounds = rounds.stream()
                    .filter(r -> r.getStatus() == PatrolRound.RoundStatus.MISSED
                            || r.getStatus() == PatrolRound.RoundStatus.EXPECTED)
                    .count();
            if (missedRounds > 0 && (req.incompletePatrolReason() == null
                    || req.incompletePatrolReason().isBlank())) {
                throw new HandyFlowException(
                        missedRounds + " patrol round(s) were not completed. "
                                + "Provide a reason for the incomplete patrol before clocking out.",
                        HttpStatus.BAD_REQUEST, "INCOMPLETE_PATROL");
            }
        }

        List<ResourceCustody> openCustody =
                custodyRepository.findCheckedOutByGuard(session.getGuardId());
        if (!openCustody.isEmpty() && !req.resourcesReturned()) {
            throw new HandyFlowException(
                    openCustody.size() + " resource(s) not returned: " +
                            openCustody.stream().map(ResourceCustody::getResourceRef)
                                    .reduce((a, b) -> a + ", " + b).orElse(""),
                    HttpStatus.BAD_REQUEST, "RESOURCES_NOT_RETURNED");
        }

        session.close(req.pinVerified(),
                req.faceMatchConfidence() != null
                        ? new BigDecimal(req.faceMatchConfidence().toString()) : null,
                req.handoverNotes());
        sessionRepository.save(session);

        if (session.getShiftId() != null) {
            shiftRepository.findById(session.getShiftId()).ifPresent(shift -> {
                if (shift.getStatus() == za.co.handyflow.platform.security.domain.model.ShiftStatus.ACTIVE) {
                    shift.complete();
                    shiftRepository.save(shift);
                }
            });
        }

        log.info("[Security] Session closed sessionId={} guardId={} duration={}min",
                sessionId, session.getGuardId(), session.durationMinutes());

        Guard guard = guardRepository.findById(session.getGuardId()).orElse(null);
        SecurityDevice device = deviceRepository.findById(session.getDeviceId()).orElse(null);
        return toResponse(session, guard, device, null);
    }

    // ── Supervisor Force-Close ─────────────────────────────────────────────────

    @Transactional
    public DeviceSessionResponse forceCloseSession(TenantId tenantId, UUID sessionId,
                                                   UUID supervisorId, String reason) {
        DeviceSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("DeviceSession",
                        sessionId.toString()));

        if (session.isClosed()) {
            throw new HandyFlowException(
                    "Session is already closed", HttpStatus.BAD_REQUEST, "SESSION_ALREADY_CLOSED");
        }

        session.forceClose(supervisorId, reason);
        sessionRepository.save(session);

        log.warn("[Security] Session force-closed sessionId={} by supervisor={} reason={}",
                sessionId, supervisorId, reason);

        return toResponse(session, null, null, null);
    }

    // ── Identity Resolution (Phase 2 key method) ───────────────────────────────

    @Transactional(readOnly = true)
    public Optional<UUID> resolveGuardId(String deviceHardwareId, TenantId tenantId) {
        return deviceRepository
                .findByTenantIdAndDeviceHardwareId(tenantId, deviceHardwareId)
                .flatMap(d -> sessionRepository.findOpenByDevice(d.getId()))
                .map(DeviceSession::getGuardId);
    }

    // ── Resource Custody ───────────────────────────────────────────────────────

    @Transactional
    public ResourceCustody checkoutResource(TenantId tenantId, UUID sessionId,
                                            CheckoutResourceRequest req) {
        DeviceSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getTenantId().equals(tenantId) && s.isOpen())
                .orElseThrow(() -> new ResourceNotFoundException("DeviceSession",
                        sessionId.toString()));

        ResourceCustody custody = ResourceCustody.checkout(
                tenantId, sessionId, session.getGuardId(), session.getShiftId(),
                ResourceCustody.ResourceType.valueOf(req.resourceType()),
                req.resourceRef(), req.witnessedBy(), req.notes());
        return custodyRepository.save(custody);
    }

    @Transactional
    public ResourceCustody returnResource(TenantId tenantId, UUID custodyId,
                                          ReturnResourceRequest req) {
        ResourceCustody custody = custodyRepository.findById(custodyId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("ResourceCustody",
                        custodyId.toString()));

        custody.returnResource(
                ResourceCustody.ConditionOnReturn.valueOf(req.condition()),
                req.notes());
        return custodyRepository.save(custody);
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<DeviceSession> getCurrentSession(String deviceHardwareId,
                                                     TenantId tenantId) {
        return deviceRepository
                .findByTenantIdAndDeviceHardwareId(tenantId, deviceHardwareId)
                .flatMap(d -> sessionRepository.findOpenByDevice(d.getId()));
    }

    /**
     * Tenant-wide paginated session list, newest-first. Backs GET /sessions
     * (added to close the DeviceSessionsTab.tsx audit gap — see class
     * javadoc). Resolves device/guard/shift names the same way every other
     * toResponse() call in this class already does, per-row.
     */
    @Transactional(readOnly = true)
    public Page<DeviceSessionResponse> getSessions(TenantId tenantId, Pageable pageable) {
        return sessionRepository.findByTenant(tenantId, pageable)
                .map(s -> {
                    Guard guard = guardRepository.findById(s.getGuardId()).orElse(null);
                    SecurityDevice device = deviceRepository.findById(s.getDeviceId()).orElse(null);
                    za.co.handyflow.platform.security.domain.model.Shift shift = s.getShiftId() != null
                            ? shiftRepository.findById(s.getShiftId()).orElse(null)
                            : null;
                    return toResponse(s, guard, device, shift);
                });
    }

    // ── Response mapping ───────────────────────────────────────────────────────

    private DeviceSessionResponse toResponse(DeviceSession s, Guard guard,
                                             SecurityDevice device,
                                             za.co.handyflow.platform.security.domain.model.Shift shift) {
        return new DeviceSessionResponse(
                s.getId(), s.getDeviceId(),
                device != null ? device.getDeviceName() : null,
                s.getGuardId(),
                guard != null ? guard.getFullName() : null,
                s.getShiftId(),
                shift != null ? shift.getStartAt() + " – " + shift.getEndAt() : null,
                s.getStartedAt(), s.getEndedAt(),
                s.isOpen(), s.durationMinutes(),
                s.getHandoverNotes(), s.getForcedCloseReason(),
                shift != null ? shift.getSiteId() : null);
    }

    /**
     * GAP-07 fix support: resolves the CALLING guard's own current site
     * from their open session/shift — never a free siteId parameter the
     * caller supplies, per the mobile gap report's own suggested fix
     * ("scoped to the guard's own site via their open DeviceSession, not
     * a free siteId parameter"). findOpenByGuard() is not itself
     * tenant-scoped at the query level, but guardId is already
     * tenant-unique (a Guard record belongs to exactly one tenant), so
     * this is safe without an extra tenant check — same reasoning
     * already relied on implicitly everywhere else a bare guardId is
     * used as a lookup key in this service.
     */
    @Transactional(readOnly = true)
    public UUID resolveCurrentSiteForGuard(UUID guardId) {
        DeviceSession session = sessionRepository.findOpenByGuard(guardId)
                .orElseThrow(() -> new HandyFlowException(
                        "No open session — clock in before accessing site-scoped data",
                        HttpStatus.CONFLICT, "NO_OPEN_SESSION"));
        if (session.getShiftId() == null) {
            throw new HandyFlowException(
                    "Your open session has no linked shift — no site to resolve",
                    HttpStatus.CONFLICT, "NO_SHIFT");
        }
        return shiftRepository.findById(session.getShiftId())
                .map(za.co.handyflow.platform.security.domain.model.Shift::getSiteId)
                .orElseThrow(() -> new HandyFlowException(
                        "Shift not found for your open session", HttpStatus.CONFLICT, "SHIFT_NOT_FOUND"));
    }
}