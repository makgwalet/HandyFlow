// security/application/internal/GuardScreeningService.java

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

import java.util.List;
import java.util.UUID;

/**
 * GuardScreeningService — manages guard screening records and the scheduling gate.
 *
 * CHANGE (V213): checkScreeningExpiry() has been REMOVED from this class and
 * moved to its own GuardScreeningComplianceScheduler component, matching the
 * PsiraComplianceScheduler/ArmouryComplianceScheduler pattern and routing
 * through the real notification pipeline instead of log-only. This service
 * now focuses purely on screening-record CRUD and the scheduling gate; see
 * GuardScreeningComplianceScheduler for the expiry alert logic.
 *
 * Screening types: POLYGRAPH, CRIMINAL_RECORD_CHECK, REFERENCE_CHECK,
 *                  DRUG_TEST, PSYCHOMETRIC, CREDIT_CHECK, OTHER
 *
 * Scheduling gate (called by ShiftService.createShift and RotationService.generateSchedule):
 *   Guards with screening_status = FLAGGED or PENDING (for required types) are
 *   blocked from shift assignment until their status is CLEARED.
 *   Currently this is an advisory check (log + warn), not a hard block — the
 *   operator can override.  A site-level "require_screening_clearance" flag
 *   (Phase 3) will make it a hard block for high-security sites.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardScreeningService {

    private final GuardScreeningRepository screeningRepository;
    private final GuardRepository          guardRepository;
    private final SiteRepository           siteRepository;

    // ── Screening Records CRUD ────────────────────────────────────────────────

    /**
     * Creates a new screening record with PENDING result.
     * Called when a supervisor initiates a screening (e.g. "request polygraph
     * before this guard works the Sandton Mall site").
     */
    @Transactional
    public GuardScreeningRecord createScreening(TenantId tenantId, UUID guardId,
                                                CreateScreeningRequest req,
                                                UUID createdBy) {
        guardRepository.findActiveById(tenantId, guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));

        GuardScreeningRecord record = GuardScreeningRecord.create(
                tenantId, guardId,
                GuardScreeningRecord.ScreeningType.valueOf(req.screeningType()),
                GuardScreeningRecord.ScreeningReason.valueOf(req.reason()),
                createdBy);
        record.setRequestDetails(req.provider(), req.requestedAt() != null ? req.requestedAt() : java.time.LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")));
        record = screeningRepository.save(record);

        // Rollup: guard now has at least one PENDING → status = PENDING
        updateScreeningStatus(tenantId, guardId);

        log.info("[Security] Screening created guardId={} type={} reason={}",
                guardId, req.screeningType(), req.reason());
        return record;
    }

    /**
     * Records the result of a completed screening.
     * Called when the external agency returns the result.
     */
    @Transactional
    public GuardScreeningRecord recordResult(TenantId tenantId, UUID screeningId,
                                             RecordScreeningResultRequest req) {
        GuardScreeningRecord record = screeningRepository.findById(screeningId)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("GuardScreeningRecord",
                        screeningId.toString()));

        record.recordResult(
                GuardScreeningRecord.ScreeningResult.valueOf(req.result()),
                req.conductedBy(),
                req.conductedAt(),
                req.nextDueAt(),
                req.reportRef(),
                req.notes());
        screeningRepository.save(record);

        // Update the guard's rollup status
        updateScreeningStatus(tenantId, record.getGuardId());

        log.info("[Security] Screening result recorded screeningId={} result={}",
                screeningId, req.result());
        return record;
    }

    /**
     * Reviewer sign-off on a screening that already has a result. Not clearing needs a written reason.
     * A NOT_CLEARED decision counts as a failed screening for the guard's rollup and the pre-shift gate.
     */
    @Transactional
    public GuardScreeningRecord decide(TenantId tenantId, UUID guardId, UUID screeningId,
                                       DecideScreeningRequest req, UUID by, String byName) {
        GuardScreeningRecord record = findForGuard(tenantId, guardId, screeningId);
        String decision = req.decision() == null ? "" : req.decision().trim().toUpperCase();
        if (!"CLEARED".equals(decision) && !"NOT_CLEARED".equals(decision)) {
            throw new za.co.handyflow.platform.shared.HandyFlowException("Decision must be CLEARED or NOT_CLEARED",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_DECISION");
        }
        if (record.isPending()) {
            throw new za.co.handyflow.platform.shared.HandyFlowException(
                    "Record the result before signing off this screening",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "SCREENING_RESULT_REQUIRED");
        }
        if ("NOT_CLEARED".equals(decision) && (req.note() == null || req.note().isBlank())) {
            throw new za.co.handyflow.platform.shared.HandyFlowException(
                    "Give a reason when the guard is not cleared",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "DECISION_NOTE_REQUIRED");
        }
        record.decide(decision, req.note(), by, byName);
        screeningRepository.save(record);
        updateScreeningStatus(tenantId, guardId);
        log.info("[Security] Screening decision screeningId={} decision={} by={}", screeningId, decision, by);
        return record;
    }

    /** A screening record that belongs to this tenant and this guard, or not found. */
    @Transactional(readOnly = true)
    public GuardScreeningRecord findForGuard(TenantId tenantId, UUID guardId, UUID screeningId) {
        return screeningRepository.findById(screeningId)
                .filter(r -> r.getTenantId().equals(tenantId) && r.getGuardId().equals(guardId))
                .orElseThrow(() -> new ResourceNotFoundException("GuardScreeningRecord", screeningId.toString()));
    }

    @Transactional(readOnly = true)
    public List<GuardScreeningRecord> getScreeningHistory(TenantId tenantId, UUID guardId) {
        return screeningRepository.findByGuard(tenantId, guardId);
    }

    // ── Scheduling Gate ───────────────────────────────────────────────────────

    /**
     * Advisory screening gate — called before assigning a guard to a shift.
     *
     * Returns a warning string if the guard has concerning screening status,
     * or null if clear.  The caller (ShiftService, RotationService) decides
     * whether to treat this as a hard block or a warning.
     *
     * WHY advisory and not a hard block?
     * A hard block here would prevent emergency shift coverage when no other
     * guard is available.  The operator should be informed and decide.
     * Phase 3 adds a site-level "require_screening_clearance" flag that
     * makes this a hard block for high-security sites.
     */
    @Transactional(readOnly = true)
    public String checkScreeningGate(UUID guardId) {
        // Newest record per screening type, the same rule Deployment Readiness uses, so a passed renewal clears an old
        // failure instead of the gate and the readiness percentage disagreeing.
        List<GuardScreeningRecord> current = newestPerType(screeningRepository.findAllForGuard(guardId));
        if (current.stream().anyMatch(GuardScreeningRecord::isFailed)) {
            return "Guard has a FAILED screening record. Review before assigning.";
        }
        if (current.stream().anyMatch(GuardScreeningRecord::isPending)) {
            return "Guard has a PENDING screening. Results not yet received.";
        }
        return null;  // clear
    }

    /** The newest record of each screening type. Input is newest first (as the repository returns it); ties keep the first. */
    static List<GuardScreeningRecord> newestPerType(List<GuardScreeningRecord> newestFirst) {
        java.util.Map<GuardScreeningRecord.ScreeningType, GuardScreeningRecord> byType = new java.util.LinkedHashMap<>();
        for (GuardScreeningRecord r : newestFirst) {
            byType.merge(r.getScreeningType(), r, (a, b) -> b.getCreatedAt().isAfter(a.getCreatedAt()) ? b : a);
        }
        return new java.util.ArrayList<>(byType.values());
    }

    // ── Status Rollup ─────────────────────────────────────────────────────────

    /**
     * Recomputes and persists the guard's screening_status rollup column.
     * Called after every create/update to a screening record.
     *
     * Logic, over the newest record of each screening type (a passed renewal replaces an old failure):
     *   FAIL in any record   → FLAGGED
     *   PENDING in any record → PENDING
     *   All PASS/INCONCLUSIVE → CLEARED
     *   No records           → UNSCREENED
     */
    @Transactional
    public void updateScreeningStatus(TenantId tenantId, UUID guardId) {
        List<GuardScreeningRecord> records = screeningRepository.findByGuard(tenantId, guardId);
        if (records.isEmpty()) {
            setStatus(tenantId, guardId, "UNSCREENED");
            return;
        }
        List<GuardScreeningRecord> current = newestPerType(records);
        boolean hasFail    = current.stream().anyMatch(GuardScreeningRecord::isFailed);
        boolean hasPending = current.stream().anyMatch(GuardScreeningRecord::isPending);

        if (hasFail)    setStatus(tenantId, guardId, "FLAGGED");
        else if (hasPending) setStatus(tenantId, guardId, "PENDING");
        else            setStatus(tenantId, guardId, "CLEARED");
    }

    private void setStatus(TenantId tenantId, UUID guardId, String status) {
        guardRepository.findActiveById(tenantId, guardId).ifPresent(guard -> {
            guard.setScreeningStatus(status);
            guardRepository.save(guard);
        });
    }
}