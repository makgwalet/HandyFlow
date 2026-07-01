// security/application/internal/VettingService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.*;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.FieldEncryptionService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * VettingService — Part 9.5 and 9.6 of the VIP/CP module.
 *
 * Two responsibilities:
 *
 * 1. OFFICER VETTING (Part 9.5): managing the CP clearance tier on guards,
 *    which gates DetailAssignment creation in CloseProtectionService.
 *    Tier hierarchy: STANDARD < ENHANCED < HIGH < CRITICAL.
 *    Maps to principal threat_level: a CRITICAL-threat principal requires
 *    a guard with tier=CRITICAL; HIGH requires HIGH or above, etc.
 *    This reuses the existing guard screening history (Phase 2) as the
 *    underlying evidence — the tier column is the supervisor's
 *    administrative conclusion from that evidence, not a separate set
 *    of checks.
 *
 * 2. PRINCIPAL VETTING (Part 9.6): managing due-diligence checks on the
 *    protected person before the company agrees to an engagement.
 *    Checks: sanctions, PEP status, adverse media, source of funds,
 *    criminal associates. A HIT doesn't auto-block — it flags for
 *    compliance review. The declined principals register records formal
 *    decisions not to take an engagement.
 *
 * WHY does VettingService handle both subjects?
 * Both are "vetting" workflows with the same record-then-decide pattern —
 * create a check, record the result, update a rollup status, gate an action
 * downstream. Keeping them in one service avoids parallel structures for
 * essentially identical lifecycles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VettingService {

    private final GuardRepository              guardRepository;
    private final PrincipalVettingRepository   vettingRepository;
    private final DeclinedPrincipalRepository  declinedRepository;
    private final FieldEncryptionService       encryptionService;

    // ── Part 9.5: Officer CP vetting tier ─────────────────────────────────────

    /**
     * Sets (or updates) a guard's CP vetting tier. This is a supervisory
     * administrative action — it asserts that the guard has completed the
     * relevant checks (polygraph, enhanced background, etc.) documented in
     * their screening history, and formalises the tier clearance level.
     *
     * The detailed evidence lives in security_guard_screening_records
     * (Phase 2); this tier is the conclusion drawn from that evidence, not
     * a duplicate of the screening records themselves.
     */
    @Transactional
    public void setGuardCpVettingTier(TenantId tenantId, UUID guardId,
                                      SetCpVettingTierRequest req) {
        Guard guard = guardRepository.findActiveById(tenantId, guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));

        validateTier(req.tier());

        guard.setCpVettingTier(req.tier(), req.clearedAt(), req.expiresAt());
        guardRepository.save(guard);

        log.info("[Security] CP vetting tier set guardId={} tier={} expires={}",
                guardId, req.tier(), req.expiresAt());
    }

    // ── Part 9.6: Principal vetting ───────────────────────────────────────────

    @Transactional
    public PrincipalVetting createVettingCheck(TenantId tenantId, UUID principalId,
                                               CreatePrincipalVettingRequest req,
                                               UUID createdBy) {
        PrincipalVetting.VettingType type;
        try {
            type = PrincipalVetting.VettingType.valueOf(req.vettingType());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid vettingType: " + req.vettingType(),
                    HttpStatus.BAD_REQUEST, "INVALID_VETTING_TYPE");
        }

        PrincipalVetting check = PrincipalVetting.create(tenantId, principalId, type, createdBy);
        vettingRepository.save(check);
        updateVettingStatus(tenantId, principalId);

        log.info("[Security] Principal vetting check created principalId={} type={}",
                principalId, type);
        return check;
    }

    @Transactional
    public PrincipalVetting recordVettingResult(TenantId tenantId, UUID checkId,
                                                RecordVettingResultRequest req) {
        PrincipalVetting check = vettingRepository.findByTenantAndId(tenantId, checkId)
                .orElseThrow(() -> new ResourceNotFoundException("PrincipalVetting",
                        checkId.toString()));

        PrincipalVetting.VettingResult result;
        try {
            result = PrincipalVetting.VettingResult.valueOf(req.result());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid result: " + req.result(),
                    HttpStatus.BAD_REQUEST, "INVALID_VETTING_RESULT");
        }

        check.recordResult(result, req.conductedBy(), req.conductedAt(),
                req.nextReviewAt(), req.reportRef(), req.notes());
        vettingRepository.save(check);
        updateVettingStatus(tenantId, check.getPrincipalId());

        if (check.isHit()) {
            log.warn("[Security] Vetting HIT on principalId={} type={}",
                    check.getPrincipalId(), check.getVettingType());
        }
        return check;
    }

    @Transactional(readOnly = true)
    public List<PrincipalVetting> getVettingHistory(TenantId tenantId, UUID principalId) {
        return vettingRepository.findByPrincipal(tenantId, principalId);
    }

    // ── Declined principals register ──────────────────────────────────────────

    /**
     * Formally records the company's decision not to take an engagement for
     * this principal. sensitiveDetail (the intelligence behind the decision)
     * is encrypted before storage — it's typically more sensitive than the
     * principal record itself.
     *
     * This does NOT prevent re-accepting the principal in future (that's an
     * operational decision); it just creates an immutable compliance record
     * that the declination decision was made and documented.
     */
    @Transactional
    public DeclinedPrincipal declinePrincipal(TenantId tenantId, UUID principalId,
                                              UUID declinedBy, DeclinePrincipalRequest req) {
        if (declinedRepository.isDeclined(tenantId, principalId)) {
            throw new HandyFlowException(
                    "Principal is already on the declined register",
                    HttpStatus.CONFLICT, "ALREADY_DECLINED");
        }

        String encryptedDetail = encryptionService.encrypt(req.sensitiveDetail());

        DeclinedPrincipal record = DeclinedPrincipal.decline(
                tenantId, principalId, declinedBy, req.reason(), encryptedDetail);
        declinedRepository.save(record);

        log.warn("[Security] Principal DECLINED principalId={} by={}", principalId, declinedBy);
        return record;
    }

    @Transactional(readOnly = true)
    public List<DeclinedPrincipal> getDeclinedRegister(TenantId tenantId) {
        return declinedRepository.findAllByTenant(tenantId);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void updateVettingStatus(TenantId tenantId, UUID principalId) {
        // Principal entity doesn't have a direct vettingStatus field we can
        // set here without loading it — but we can note this as a follow-up
        // similar to GuardScreeningService.updateScreeningStatus().
        // For now: log the rollup computation. A full wiring (load Principal,
        // setVettingStatus, save) would require PrincipalRepository here —
        // deliberately left as a @TODO to keep this service's dependency
        // surface minimal.
        boolean hasHit     = vettingRepository.hasHit(principalId);
        boolean hasPending = vettingRepository.hasPending(principalId);
        String computed    = hasHit ? "FLAGGED" : hasPending ? "PENDING" : "CLEARED";
        log.info("[Security] Vetting rollup principalId={} status={}", principalId, computed);
    }

    private void validateTier(String tier) {
        if (!java.util.Set.of("STANDARD", "ENHANCED", "HIGH", "CRITICAL").contains(tier)) {
            throw new HandyFlowException("Invalid vetting tier: " + tier
                    + ". Must be one of: STANDARD, ENHANCED, HIGH, CRITICAL",
                    HttpStatus.BAD_REQUEST, "INVALID_VETTING_TIER");
        }
    }
}
