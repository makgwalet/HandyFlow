// security/application/internal/ShiftService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.model.ShiftStatus;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * ShiftService — fixes bugs #4, #14, #15, #17.
 *
 * Bug #4  — PUT /shifts/{id} was missing; ShiftsTab.tsx silently 404'd on edit.
 *           Fixed by adding updateShift().
 *
 * Bug #14 — createShift() did not validate that guardId/siteId belong to the
 *           calling tenant.  A crafted request could link a guard/site from
 *           another tenant, corrupting cross-tenant reporting.
 *           Fixed by resolving both via tenant-scoped repository methods.
 *
 * Bug #15 — findOverlapping() had no tenantId filter.  UUID collision across
 *           tenants is extremely unlikely but it's a missing defence-in-depth
 *           check.  Fixed by adding tenantId to the JPQL query.
 *
 * Bug #17 — completeShift() allowed a shift to be closed with zero checkpoint
 *           scans.  A guard could work 8 hours and never patrol.
 *           Fixed by checking countByShiftId against minScanCount on the shift.
 *           minScanCount defaults to 0 (no enforcement) for backward compatibility;
 *           set it per-site when creating shifts to enable enforcement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository         shiftRepository;
    private final GuardRepository         guardRepository;
    private final SiteRepository          siteRepository;
    private final CheckpointLogRepository logRepository;
    private final AuditEventRepository     auditRepository;
    private final DeviceSessionRepository  deviceSessionRepository;
    private final ResourceCustodyRepository resourceCustodyRepository;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ShiftResponse> getShifts(TenantId tenantId, Pageable pageable) {
        return shiftRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @Transactional
    public ShiftResponse createShift(TenantId tenantId, CreateShiftRequest req) {

        // Fix bug #14: validate guard belongs to this tenant AND is schedulable.
        // WHY use isSchedulable()? A SUSPENDED guard must not be assigned to
        // new shifts — only ACTIVE guards are schedulable.  The domain method
        // encapsulates this rule cleanly.
        var guard = guardRepository.findActiveById(tenantId, req.guardId())
                .orElseThrow(() -> new ResourceNotFoundException("Guard", req.guardId().toString()));

        if (!guard.isSchedulable()) {
            throw new HandyFlowException(
                    "Guard " + guard.getFullName() + " is currently " + guard.getStatus()
                            + " and cannot be assigned to a shift",
                    HttpStatus.CONFLICT, "GUARD_NOT_SCHEDULABLE");
        }

        // PSiRA expiry compliance check.
        // WHY block scheduling, not just warn?
        // Deploying a guard with an expired PSiRA registration is a regulatory
        // violation under the Private Security Industry Regulation Act.  The
        // company is liable for criminal penalties.  A hard block here prevents
        // the scheduling mistake before it happens — far better than discovering
        // the guard was deployed without a valid registration during an audit.
        if (guard.getPsiraExpiryDate() != null &&
                guard.getPsiraExpiryDate().isBefore(java.time.LocalDate.now())) {
            throw new HandyFlowException(
                    "Guard " + guard.getFullName() + "'s PSiRA registration expired on "
                            + guard.getPsiraExpiryDate() + ". Renew their registration before scheduling.",
                    HttpStatus.CONFLICT, "PSIRA_EXPIRED");
        }

        // Fix bug #14: validate site belongs to this tenant.
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Site", req.siteId().toString()));

        // Fix bug #15: findOverlapping now includes tenantId filter.
        var overlapping = shiftRepository.findOverlapping(
                tenantId, req.guardId(), req.startAt(), req.endAt());
        if (!overlapping.isEmpty()) {
            throw new HandyFlowException(
                    "Guard " + guard.getFullName() + " already has a shift scheduled "
                            + "that overlaps with " + req.startAt() + " – " + req.endAt(),
                    HttpStatus.CONFLICT, "SHIFT_OVERLAP");
        }

        Shift shift = Shift.create(tenantId, req.siteId(), req.guardId(),
                req.startAt(), req.endAt(), req.notes());
        shiftRepository.save(shift);

        log.info("[Security] Shift created guard={} site={} start={} end={}",
                req.guardId(), req.siteId(), req.startAt(), req.endAt());
        return toResponse(shift);
    }

    /**
     * Updates mutable shift fields (notes, endAt extension).
     *
     * Fixes bug #4: PUT /shifts/{id} was missing entirely.
     * ShiftsTab.tsx called apiClient.put('/api/v1/security/shifts/{id}', body)
     * which 404'd in production — the edit modal appeared to save but did nothing.
     *
     * WHY only notes and endAt?
     * Guard and site cannot change on an existing shift (that changes the
     * nature of the deployment — cancel and create a new shift instead).
     * startAt cannot change once scheduled (the guard knows their start time).
     * Only endAt (overtime extension) and notes (access codes, instructions)
     * are legitimate mid-shift updates.
     */
    @Transactional
    public ShiftResponse updateShift(TenantId tenantId, UUID id, UpdateShiftRequest req) {
        Shift shift = findActive(tenantId, id);

        if (shift.getStatus() == ShiftStatus.COMPLETED
                || shift.getStatus() == ShiftStatus.CANCELLED) {
            throw new HandyFlowException(
                    "Cannot edit a " + shift.getStatus() + " shift",
                    HttpStatus.CONFLICT, "SHIFT_NOT_EDITABLE");
        }

        // Validate new endAt if provided
        if (req.endAt() != null) {
            if (!req.endAt().isAfter(shift.getStartAt())) {
                throw new HandyFlowException(
                        "New end time must be after the shift start time (" + shift.getStartAt() + ")",
                        HttpStatus.BAD_REQUEST, "INVALID_END_TIME");
            }
            // Check that extending endAt doesn't overlap another shift for this guard
            var overlapping = shiftRepository.findOverlapping(
                    tenantId, shift.getGuardId(), shift.getStartAt(), req.endAt());
            // Exclude the shift being updated itself
            overlapping = overlapping.stream()
                    .filter(s -> !s.getId().equals(id))
                    .toList();
            if (!overlapping.isEmpty()) {
                throw new HandyFlowException(
                        "Extending this shift would overlap another scheduled shift for this guard",
                        HttpStatus.CONFLICT, "SHIFT_OVERLAP");
            }
        }

        // Apply updates — domain model currently doesn't have an update() method;
        // use JPQL or a Spring Data @Modifying query as a pragmatic Phase 0 solution.
        // Phase 1: add update() to Shift domain model.
        shiftRepository.updateShift(id,
                req.notes() != null ? req.notes() : shift.getNotes(),
                req.endAt()  != null ? req.endAt()  : shift.getEndAt());

        log.info("[Security] Shift updated id={} notes={} endAt={}",
                id, req.notes() != null, req.endAt() != null);
        return toResponse(shiftRepository.findActiveById(tenantId, id).orElseThrow());
    }

    @Transactional
    public ShiftResponse startShift(TenantId tenantId, UUID id) {
        Shift shift = findActive(tenantId, id);
        shift.start();
        shiftRepository.save(shift);
        log.info("[Security] Shift started id={}", id);
        return toResponse(shift);
    }

    /**
     * Completes a shift with optional scan-count enforcement (fixes bug #17).
     *
     * WHY check scan count?
     * Without this check, a guard can close a shift with zero checkpoint scans.
     * The entire "proof of patrol" premise relies on guards actually scanning
     * checkpoints — allowing completion with none defeats it completely.
     *
     * The enforcement is configurable per shift (minScanCount column on the
     * security_shifts table, added in V50).  Default = 0 means enforcement is
     * off (backward-compatible for existing shifts).  Set minScanCount > 0 when
     * creating a shift for sites where patrol compliance must be verified.
     *
     * Phase 1: set minScanCount from site.patrol_required_scans at shift creation.
     */
    @Transactional
    public ShiftResponse completeShift(TenantId tenantId, UUID id) {
        Shift shift = findActive(tenantId, id);

        // Check scan count against the minimum required for this shift
        int minRequired = shift.getMinScanCount();
        if (minRequired > 0) {
            long actualScans = logRepository.countByShiftId(id);
            if (actualScans < minRequired) {
                throw new HandyFlowException(
                        "Cannot complete shift: only " + actualScans + " of " + minRequired
                                + " required checkpoint scan(s) recorded. "
                                + "Please complete your patrol before ending your shift.",
                        HttpStatus.CONFLICT, "INSUFFICIENT_SCANS");
            }
        }

        shift.complete();
        shiftRepository.save(shift);
        log.info("[Security] Shift completed id={} scans={}",
                id, logRepository.countByShiftId(id));
        return toResponse(shift);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Shift findActive(TenantId tenantId, UUID id) {
        return shiftRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", id.toString()));
    }

    private ShiftResponse toResponse(Shift s) {
        return new ShiftResponse(
                s.getId(), s.getSiteId(), s.getGuardId(),
                s.getStartAt(), s.getEndAt(), s.getStatus().name(),
                s.getNotes(), s.getCreatedAt()
        );
    }


// NOTE: closeOvertime() and pullFromSite() both reach into
// DeviceSessionRepository directly to force-close any open device session
// tied to the shift, using DeviceSession's existing forceClose() domain
// method. I don't have DeviceSessionService.java's content, so I can't
// confirm whether it already does something equivalent when its own
// forceCloseSession() is called on the *session* directly (DeviceSessionController
// exposes that as a separate supervisor action). If DeviceSessionService
// ALSO completes the linked shift as a side effect of closing the session,
// there is a double-completion risk here -- please confirm what
// DeviceSessionService.forceCloseSession() actually does before shipping
// this, or send me the file and I'll reconcile the two paths.

    /**
     * Dismisses a no-show/late alert without changing the shift's status --
     * e.g. the guard called in sick and a replacement is being handled manually
     * outside the system. Purely a record-keeping action.
     */
    @Transactional
    public ShiftResponse dismissNoShow(TenantId tenantId, UUID id, UUID supervisorId,
                                       ShiftSupervisorActionRequest req) {
        Shift shift = findActive(tenantId, id);
        shift.dismissNoShow(supervisorId, req.reason());
        shiftRepository.save(shift);

        writeAudit(tenantId, supervisorId, id, "NO_SHOW_DISMISSED", req.reason());

        log.info("[Security] No-show dismissed shiftId={} by={} reason='{}'",
                id, supervisorId, req.reason());
        return toResponse(shift);
    }

    /**
     * Force-closes an ACTIVE shift that has run into unconfirmed overtime.
     * Bypasses minScanCount enforcement (the guard isn't available to satisfy
     * it) -- this is explicitly NOT the same code path as ShiftService.completeShift().
     * Also force-closes any open DeviceSession tied to this shift, since leaving
     * that dangling would block the device for the next guard's clock-in.
     */
    @Transactional
    public ShiftResponse forceCloseOvertime(TenantId tenantId, UUID id, UUID supervisorId,
                                            ShiftSupervisorActionRequest req) {
        Shift shift = findActive(tenantId, id);
        shift.closeOvertime(supervisorId, req.reason());
        shiftRepository.save(shift);

        deviceSessionRepository.findByShiftId(id).ifPresent(session -> {
            if (session.isOpen()) {
                session.forceClose(supervisorId, "Auto-closed: shift overtime force-closed by supervisor");
                deviceSessionRepository.save(session);
            }
        });

        flagOpenResourceCustody(shift.getGuardId(), id, "overtime force-close");

        writeAudit(tenantId, supervisorId, id, "OVERTIME_FORCE_CLOSED", req.reason());

        log.info("[Security] Overtime force-closed shiftId={} by={} reason='{}'",
                id, supervisorId, req.reason());
        return toResponse(shift);
    }

    /**
     * Supervisor pulls a guard off site mid-shift -- client complaint, guard
     * unwell, redeployment, etc. Distinct from both completeShift() (guard-driven,
     * end of shift) and forceCloseOvertime() (shift already overran) -- this can
     * happen at any point during an ACTIVE shift, including right after it starts.
     */
    @Transactional
    public ShiftResponse pullFromSite(TenantId tenantId, UUID id, UUID supervisorId,
                                      ShiftSupervisorActionRequest req) {
        Shift shift = findActive(tenantId, id);
        shift.pullFromSite(supervisorId, req.reason());
        shiftRepository.save(shift);

        deviceSessionRepository.findByShiftId(id).ifPresent(session -> {
            if (session.isOpen()) {
                session.forceClose(supervisorId, "Auto-closed: guard pulled from site by supervisor");
                deviceSessionRepository.save(session);
            }
        });

        flagOpenResourceCustody(shift.getGuardId(), id, "pulled from site");

        writeAudit(tenantId, supervisorId, id, "GUARD_PULLED_FROM_SITE", req.reason());

        log.warn("[Security] Guard pulled from site shiftId={} guard={} by={} reason='{}'",
                id, shift.getGuardId(), supervisorId, req.reason());
        return toResponse(shift);
    }

// ── Helpers (new) ────────────────────────────────────────────────────────

    /**
     * Logs (does not auto-return) any resources still checked out by this guard
     * when a shift is interrupted mid-way. Auto-returning a firearm without an
     * actual physical return + witness would falsify the armoury chain of
     * custody -- ArmouryService's witnessed-return requirement is a hard
     * compliance rule that a supervisor UI action must not bypass. This just
     * surfaces the gap loudly so a supervisor follows up.
     */
    private void flagOpenResourceCustody(UUID guardId, UUID shiftId, String context) {
        var openItems = resourceCustodyRepository.findCheckedOutByGuard(guardId);
        if (!openItems.isEmpty()) {
            log.warn("[Security] {} still-checked-out resource(s) for guard={} at shift {} ({}) -- "
                            + "these are NOT auto-returned and need manual reconciliation: {}",
                    openItems.size(), guardId, shiftId, context,
                    openItems.stream().map(r -> r.getResourceRef()).toList());
        }
    }

    private void writeAudit(TenantId tenantId, UUID actorId, UUID shiftId, String action, String reason) {
        auditRepository.save(za.co.handyflow.platform.security.domain.model.AuditEvent.record(
                tenantId, actorId,
                za.co.handyflow.platform.security.domain.model.AuditEvent.ActorType.USER,
                "SHIFT", shiftId, action,
                null, null,
                "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}"));
    }
}
