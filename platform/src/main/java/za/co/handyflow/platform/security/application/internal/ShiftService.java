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
import za.co.handyflow.platform.security.domain.repository.CheckpointLogRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
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
}
