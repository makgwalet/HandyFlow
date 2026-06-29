// security/application/internal/ShiftSwapService.java

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

import java.time.LocalDate;
import java.util.*;

/**
 * ShiftSwapService — manages the two-stage shift swap approval workflow.
 *
 * Stage 1 (guard-initiated):
 *   Requesting guard calls createSwapRequest().
 *   System checks the shift exists and belongs to the requesting guard.
 *   If proposedGuardId is supplied, the proposed guard is notified.
 *
 * Stage 2a (proposed guard):
 *   Proposed guard calls acceptSwap().
 *   Status: PENDING → PROPOSED_ACCEPTED
 *
 * Stage 2b (supervisor):
 *   Supervisor calls approveSwap() or rejectSwap().
 *   approveSwap() re-runs all scheduling validations before committing.
 *   On APPROVED: Shift.guardId is updated to the proposed guard.
 *
 * WHY re-validate at approval time?
 * A guard's status, PSiRA, or schedule can change between the swap being
 * requested and the supervisor approving it.  The validation snapshot
 * (validationPassed + validationNotes) is frozen on the swap request row so
 * the supervisor has full visibility even after the guard record changes again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftSwapService {

    private final ShiftSwapRepository   swapRepository;
    private final ShiftRepository       shiftRepository;
    private final GuardRepository       guardRepository;

    // ── Queries ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ShiftSwapResponse> getPendingSwaps(TenantId tenantId, Pageable pageable) {
        return swapRepository.findPending(tenantId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ShiftSwapResponse> getSwapsByGuard(TenantId tenantId, UUID guardId,
                                                   Pageable pageable) {
        return swapRepository.findByGuard(tenantId, guardId, pageable)
                .map(this::toResponse);
    }

    // ── Stage 1: Request ───────────────────────────────────────────────────────

    @Transactional
    public ShiftSwapResponse createSwapRequest(TenantId tenantId, UUID requestingGuardId,
                                               CreateSwapRequest req) {
        // Resolve the shift and verify it belongs to the requesting guard
        Shift shift = shiftRepository.findByTenantAndId(tenantId, req.originalShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift", "unknown"));

        if (!shift.getGuardId().equals(requestingGuardId)) {
            throw new HandyFlowException(
                    "You can only request a swap for your own shifts", HttpStatus.FORBIDDEN, "VALIDATION_ERROR");
        }
        if (shift.getStatus() != ShiftStatus.SCHEDULED) {
            throw new HandyFlowException(
                    "Only SCHEDULED shifts can be swapped (status: " + shift.getStatus() + ")",
                    HttpStatus.BAD_REQUEST, "INVALID_SHIFT_STATUS");
        }

        // Prevent duplicate open swap requests for the same shift
        List<ShiftSwapRequest> existing = swapRepository.findOpenByShift(req.originalShiftId());
        if (!existing.isEmpty()) {
            throw new HandyFlowException(
                    "An open swap request already exists for this shift", HttpStatus.CONFLICT, "VALIDATION_ERROR");
        }

        // Validate proposed guard belongs to this tenant (if supplied)
        if (req.proposedGuardId() != null) {
            guardRepository.findActiveById(tenantId, req.proposedGuardId())
                    .orElseThrow(() -> new ResourceNotFoundException("Proposed guard not found", "lookup"));
        }

        ShiftSwapRequest swapRequest = ShiftSwapRequest.create(
                tenantId, req.originalShiftId(),
                requestingGuardId, req.proposedGuardId(), req.reason());
        swapRepository.save(swapRequest);

        log.info("[Security] Swap request created id={} shift={} requestingGuard={}",
                swapRequest.getId(), req.originalShiftId(), requestingGuardId);
        return toResponse(swapRequest);
    }

    // ── Stage 2a: Proposed guard accepts ──────────────────────────────────────

    @Transactional
    public ShiftSwapResponse acceptSwap(TenantId tenantId, UUID swapId,
                                        UUID acceptingGuardId) {
        ShiftSwapRequest swap = swapRepository.findByTenantAndId(tenantId, swapId)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftSwapRequest", swapId.toString()));

        if (!acceptingGuardId.equals(swap.getProposedGuardId())) {
            throw new HandyFlowException(
                    "Only the proposed guard can accept this swap request", HttpStatus.FORBIDDEN, "VALIDATION_ERROR");
        }

        swap.proposedGuardAccepts();
        swapRepository.save(swap);

        log.info("[Security] Swap accepted by proposed guard swapId={}", swapId);
        return toResponse(swap);
    }

    // ── Stage 2b: Supervisor approves ─────────────────────────────────────────

    /**
     * Approves the swap and re-assigns the shift to the proposed guard.
     *
     * Validation at approval time:
     *   1. Proposed guard is still ACTIVE and PSiRA not expired
     *   2. Proposed guard has no overlapping shift
     * If validation fails: the swap is NOT rejected automatically — the
     * supervisor sees the validation notes and can decide to reject or
     * investigate, because sometimes the situation is fixable.
     */
    @Transactional
    public ShiftSwapResponse approveSwap(TenantId tenantId, UUID swapId,
                                         UUID supervisorId, SwapActionRequest req) {
        ShiftSwapRequest swap = swapRepository.findByTenantAndId(tenantId, swapId)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftSwapRequest", swapId.toString()));

        Shift shift = shiftRepository.findByTenantAndId(tenantId, swap.getOriginalShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift", "unknown"));

        UUID proposedGuardId = swap.getProposedGuardId();
        if (proposedGuardId == null) {
            throw new HandyFlowException(
                    "Swap has no proposed guard — cannot approve an open request",
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }

        // ── Run validations ────────────────────────────────────────────────────
        StringBuilder notes     = new StringBuilder();
        boolean       allPassed = true;

        Guard proposed = guardRepository.findActiveById(tenantId, proposedGuardId)
                .orElse(null);

        if (proposed == null) {
            notes.append("Proposed guard not found or deleted. ");
            allPassed = false;
        } else {
            // Status check
            if (!proposed.isSchedulable()) {
                notes.append("Proposed guard is ").append(proposed.getStatus())
                        .append(" — not schedulable. ");
                allPassed = false;
            }

            // PSiRA expiry check
            LocalDate shiftDate = shift.getStartAt().atZone(
                    java.time.ZoneId.of("Africa/Johannesburg")).toLocalDate();
            if (proposed.getPsiraExpiryDate() != null &&
                    proposed.getPsiraExpiryDate().isBefore(shiftDate)) {
                notes.append("Proposed guard PSiRA expired on ")
                        .append(proposed.getPsiraExpiryDate()).append(". ");
                allPassed = false;
            }

            // Overlap check — same guard, same time window, excluding this shift
            boolean overlaps = shiftRepository.hasOverlap(
                    tenantId, proposedGuardId,
                    shift.getStartAt(), shift.getEndAt(),
                    shift.getId());
            if (overlaps) {
                notes.append("Proposed guard has an overlapping shift. ");
                allPassed = false;
            }
        }

        if (!allPassed) {
            notes.append("Supervisor approved despite validation failures.");
        } else {
            notes.append("All validations passed.");
        }

        // Record approval (with validation snapshot)
        swap.approve(supervisorId, allPassed, notes.toString().trim());
        swapRepository.save(swap);

        // Re-assign the shift to the proposed guard
        shift.reassignGuard(proposedGuardId);
        shiftRepository.save(shift);

        log.info("[Security] Swap approved swapId={} newGuard={} validationPassed={}",
                swapId, proposedGuardId, allPassed);
        return toResponse(swap);
    }

    // ── Stage 2b: Supervisor rejects ──────────────────────────────────────────

    @Transactional
    public ShiftSwapResponse rejectSwap(TenantId tenantId, UUID swapId,
                                        UUID supervisorId, SwapActionRequest req) {
        ShiftSwapRequest swap = swapRepository.findByTenantAndId(tenantId, swapId)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftSwapRequest", swapId.toString()));
        swap.reject(supervisorId, req.reason());
        swapRepository.save(swap);

        log.info("[Security] Swap rejected swapId={} by supervisor={}", swapId, supervisorId);
        return toResponse(swap);
    }

    // ── Cancel (requesting guard) ──────────────────────────────────────────────

    @Transactional
    public ShiftSwapResponse cancelSwap(TenantId tenantId, UUID swapId,
                                        UUID requestingGuardId) {
        ShiftSwapRequest swap = swapRepository.findByTenantAndId(tenantId, swapId)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftSwapRequest", swapId.toString()));

        if (!swap.getRequestingGuardId().equals(requestingGuardId)) {
            throw new HandyFlowException(
                    "Only the requesting guard can cancel this swap", HttpStatus.FORBIDDEN, "VALIDATION_ERROR");
        }
        swap.cancel();
        swapRepository.save(swap);

        log.info("[Security] Swap cancelled swapId={}", swapId);
        return toResponse(swap);
    }

    // ── Response mapping ───────────────────────────────────────────────────────

    private ShiftSwapResponse toResponse(ShiftSwapRequest r) {
        String shiftSummary = buildShiftSummary(r.getOriginalShiftId());
        String requestingName = guardName(r.getRequestingGuardId());
        String proposedName   = r.getProposedGuardId() != null
                ? guardName(r.getProposedGuardId()) : null;
        String decidedByName  = r.getDecidedBy() != null
                ? guardName(r.getDecidedBy()) : null;

        return new ShiftSwapResponse(
                r.getId(), r.getOriginalShiftId(), shiftSummary,
                r.getRequestingGuardId(), requestingName,
                r.getProposedGuardId(), proposedName,
                r.getStatus().name(),
                r.getProposedAcceptedAt(),
                r.getRequestedAt(),
                r.getDecidedBy(), decidedByName, r.getDecidedAt(),
                r.getReason(), r.getRejectionReason(),
                r.getValidationPassed(), r.getValidationNotes(),
                r.getCreatedAt());
    }

    private String guardName(UUID guardId) {
        return guardRepository.findById(guardId)
                .map(Guard::getFullName).orElse("Unknown");
    }

    private String buildShiftSummary(UUID shiftId) {
        return shiftRepository.findById(shiftId)
                .map(s -> s.getStartAt().toString())
                .orElse("Unknown shift");
    }
}
