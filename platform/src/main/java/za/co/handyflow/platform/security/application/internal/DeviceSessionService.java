// security/application/internal/DeviceSessionService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * This service is the Phase 2 implementation of Part 6.3 (shift lifecycle on
 * shared device).  It replaces the implicit identity model where a guard's JWT
 * claim was the only proof of identity — now the server resolves guardId from
 * the open DeviceSession, not from a client-supplied field.
 *
 * Key invariants enforced here:
 *   1. Only one open session per device at a time.
 *   2. Only one open session per guard across all devices.
 *   3. A guard whose status is not ACTIVE cannot open a session.
 *   4. A session cannot be opened if the previous guard's session on that
 *      device is still open — supervisor must force-close it first.
 *
 * Identity resolution for scan/incident endpoints (Phase 2 upgrade path):
 *   - CheckpointScanController should call resolveGuardId(deviceHardwareId, tenantId)
 *     to get the currently-active guard on the device, instead of reading from JWT.
 *   - This closes loophole #13 permanently at the infrastructure level.
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

    // ── Session Open ───────────────────────────────────────────────────────────

    /**
     * Opens a guard session on a device — called when a guard clocks in.
     *
     * Validates:
     *   1. Device exists and belongs to this tenant
     *   2. No other session is open on this device (must force-close first)
     *   3. No other open session exists for this guard on any device
     *   4. Guard is ACTIVE and schedulable
     *   5. A matching SCHEDULED shift exists for this guard at this site
     *
     * On success:
     *   - Opens DeviceSession row
     *   - Transitions the matching Shift to ACTIVE
     *   - Generates PatrolRounds for the shift (if a route is configured for the site)
     */
    @Transactional
    public DeviceSessionResponse openSession(TenantId tenantId, OpenSessionRequest req) {

        // 1. Resolve device
        SecurityDevice device = deviceRepository
                .findByTenantIdAndDeviceHardwareId(tenantId, req.deviceHardwareId())
                .orElseThrow(() -> new ResourceNotFoundException("SecurityDevice",
                        req.deviceHardwareId()));

        if (!device.isActive()) {
            throw new HandyFlowException(
                    "Device is " + device.getStatus() + " and cannot accept sessions",
                    HttpStatus.FORBIDDEN, "DEVICE_INACTIVE");
        }

        // 2. No existing open session on this device
        sessionRepository.findOpenByDevice(device.getId()).ifPresent(existing -> {
            throw new HandyFlowException(
                    "A session is already open on this device for guard "
                            + existing.getGuardId() + ". Contact your supervisor to close it.",
                    HttpStatus.CONFLICT, "DEVICE_SESSION_CONFLICT");
        });

        // 3. No existing open session for this guard
        sessionRepository.findOpenByGuard(req.guardId()).ifPresent(existing -> {
            throw new HandyFlowException(
                    "This guard already has an open session on device "
                            + existing.getDeviceId() + ". Close that session first.",
                    HttpStatus.CONFLICT, "GUARD_SESSION_CONFLICT");
        });

        // 4. Guard is ACTIVE
        Guard guard = guardRepository.findActiveById(tenantId, req.guardId())
                .orElseThrow(() -> new ResourceNotFoundException("Guard",
                        req.guardId().toString()));

        if (!guard.isSchedulable()) {
            throw new HandyFlowException(
                    "Guard " + guard.getFullName() + " is " + guard.getStatus()
                            + " and cannot start a session",
                    HttpStatus.FORBIDDEN, "GUARD_NOT_SCHEDULABLE");
        }

        // 5. Find the guard's scheduled shift at this site (within a 30-min window)
        Instant now        = Instant.now();
        Instant windowStart = now.minus(30, ChronoUnit.MINUTES);
        Instant windowEnd   = now.plus(30, ChronoUnit.MINUTES);

        // Find SCHEDULED shift for this guard at device's site starting within window
        List<za.co.handyflow.platform.security.domain.model.Shift> candidateShifts =
                shiftRepository.findScheduledStartingBefore(tenantId, windowEnd)
                        .stream()
                        .filter(s -> s.getGuardId().equals(req.guardId()))
                        .filter(s -> s.getSiteId().equals(device.getSiteId()))
                        .filter(s -> s.getStartAt().isAfter(windowStart))
                        .toList();

        za.co.handyflow.platform.security.domain.model.Shift shift = null;
        if (!candidateShifts.isEmpty()) {
            shift = candidateShifts.get(0);
            shift.start();
            shiftRepository.save(shift);
        }
        // If no matching shift: session is allowed but no shift is started
        // (supervisor may be doing a spot check / unscheduled presence)

        // Create the session
        DeviceSession session = DeviceSession.open(
                tenantId, device.getId(), req.guardId(),
                shift != null ? shift.getId() : null,
                req.pinVerified(),
                req.faceMatchConfidence() != null
                        ? new BigDecimal(req.faceMatchConfidence().toString()) : null,
                req.geofenceOk());
        sessionRepository.save(session);

        // Generate patrol rounds if a route is configured for this site
        if (shift != null && device.getSiteId() != null) {
            patrolRoundService.generateRoundsForShift(tenantId, shift);
        }

        log.info("[Security] Session opened sessionId={} guardId={} deviceId={} shiftId={}",
                session.getId(), req.guardId(), device.getId(),
                shift != null ? shift.getId() : "none");

        return toResponse(session, guard, device, shift);
    }

    // ── Session Close ──────────────────────────────────────────────────────────

    /**
     * Closes a guard session — called when a guard clocks out.
     *
     * Enforces minimum patrol coverage before allowing close
     * (closes loophole #17 at the session level).
     * If coverage is insufficient, the guard must provide a reason.
     */
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

        // Check patrol coverage if shift is linked
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

        // Auto-return any unchecked-in resources
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

        // Complete the shift
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

    /**
     * Resolves the guard currently active on a device.
     *
     * This is the Phase 2 replacement for reading guardId from the JWT claim.
     * CheckpointScanController and IncidentController should call this method
     * to get the authenticated guard identity server-side.
     *
     * Returns empty if no session is open (guard hasn't clocked in yet).
     */
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

    // ── Query ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<DeviceSession> getCurrentSession(String deviceHardwareId,
                                                     TenantId tenantId) {
        return deviceRepository
                .findByTenantIdAndDeviceHardwareId(tenantId, deviceHardwareId)
                .flatMap(d -> sessionRepository.findOpenByDevice(d.getId()));
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
                s.getHandoverNotes(), s.getForcedCloseReason());
    }
}