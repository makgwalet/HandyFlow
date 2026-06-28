// security/application/internal/GuardService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuardService {

    /**
     * Statuses that require a written reason before they can be applied.
     * WHY? SUSPENDED and TERMINATED are serious HR/legal actions.
     * A note-free status change is operationally worthless for audit purposes.
     */
    private static final Set<String> NOTE_REQUIRED = Set.of("SUSPENDED", "TERMINATED");

    private final GuardRepository guardRepository;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<GuardResponse> getGuards(TenantId tenantId, String search, Pageable pageable) {
        var page = (search == null || search.isBlank())
                ? guardRepository.findAllActive(tenantId, pageable)
                : guardRepository.searchActive(tenantId, search, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public GuardResponse getGuard(TenantId tenantId, UUID id) {
        return guardRepository.findActiveById(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", id.toString()));
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @Transactional
    public GuardResponse createGuard(TenantId tenantId, CreateGuardRequest req) {
        // Check PSiRA uniqueness across the tenant (only if a number is provided)
        if (req.psiraNumber() != null && !req.psiraNumber().isBlank()) {
            if (guardRepository.existsByTenantIdAndPsiraNumberAndDeletedAtIsNull(
                    tenantId, req.psiraNumber())) {
                throw new HandyFlowException(
                        "A guard with PSiRA number " + req.psiraNumber() + " already exists",
                        HttpStatus.CONFLICT, "PSIRA_DUPLICATE");
            }
        }
        Guard guard = Guard.create(tenantId, req.firstName(), req.lastName(),
                req.psiraNumber(), req.idNumber(), req.phone(), req.grade());
        guardRepository.save(guard);
        log.info("[Security] Created guard={} tenant={}", guard.getFullName(), tenantId);
        return toResponse(guard);
    }

    @Transactional
    public GuardResponse updateGuard(TenantId tenantId, UUID id, CreateGuardRequest req) {
        Guard guard = findActive(tenantId, id);

        // Fix bug #3: PSiRA duplicate check on update must EXCLUDE the guard
        // being edited.  Without this exclusion, any update on a guard who
        // already has a PSiRA number would fail the check against themselves.
        if (req.psiraNumber() != null && !req.psiraNumber().isBlank()) {
            if (guardRepository.existsByPsiraExcluding(tenantId, req.psiraNumber(), id)) {
                throw new HandyFlowException(
                        "Another guard with PSiRA number " + req.psiraNumber() + " already exists",
                        HttpStatus.CONFLICT, "PSIRA_DUPLICATE");
            }
        }

        guard.update(req.firstName(), req.lastName(), req.psiraNumber(),
                req.idNumber(), req.phone(), req.grade(), req.notes());
        guardRepository.save(guard);
        log.info("[Security] Updated guard={} tenant={}", id, tenantId);
        return toResponse(guard);
    }

    /**
     * Updates the guard's operational status.
     *
     * WHY is this a separate endpoint from updateGuard?
     * Status changes are HR/legal events that need their own audit trail,
     * their own required-note validation, and their own permission check
     * (a shift manager might be able to update guard details but not
     * unilaterally suspend someone without a note).
     * Mixing it into PUT /guards/{id} would make that single endpoint responsible
     * for two very different operations with different validation rules.
     *
     * This fixes bug #5: the PATCH /guards/{id}/status endpoint was missing,
     * so the frontend's "Change Status" modal silently 404'd in production.
     *
     * @param changedBy  The authenticated user ID — used for audit trail.
     *                   Pass TenantContext.getCurrentUserId() from the controller.
     */
    @Transactional
    public GuardResponse updateStatus(TenantId tenantId, UUID id,
                                      UpdateGuardStatusRequest req,
                                      UUID changedBy) {
        Guard guard = findActive(tenantId, id);

        // Require a written reason for serious status changes
        if (NOTE_REQUIRED.contains(req.status()) &&
                (req.note() == null || req.note().isBlank())) {
            throw new HandyFlowException(
                    "A reason note is required when setting status to " + req.status(),
                    HttpStatus.BAD_REQUEST, "NOTE_REQUIRED");
        }

        guard.updateStatus(req.status(), req.note(), changedBy);
        guardRepository.save(guard);

        log.info("[Security] Guard status changed guard={} status={} by={} tenant={}",
                id, req.status(), changedBy, tenantId);
        return toResponse(guard);
    }

    @Transactional
    public void deleteGuard(TenantId tenantId, UUID id, UUID deletedBy) {
        Guard guard = findActive(tenantId, id);
        // Fix bug #19: deletedBy was hardcoded to null.
        // The controller must pass TenantContext.getCurrentUserId() here.
        guard.softDelete(deletedBy);
        guardRepository.save(guard);
        log.info("[Security] Soft-deleted guard={} by={} tenant={}", id, deletedBy, tenantId);
    }

    /**
     * Updates guard photo.
     *
     * Dev-mode behaviour: base64 data URIs are accepted but stored as
     * "PENDING_UPLOAD" — the domain model logs a warning and stores the
     * placeholder.  The frontend shows a fallback avatar when photoUrl is
     * null or "PENDING_UPLOAD".
     *
     * Production: this method should receive a CDN URL (uploaded to S3 by
     * the API gateway or a separate presigned-URL upload flow).  The current
     * DTO (Map<String, String>) passes photoBase64 directly.
     *
     * WHY not block base64 entirely?
     * We're in dev.  Blocking it here would break the photo capture flow in
     * the admin UI before S3 is set up, with no benefit.  The PENDING_UPLOAD
     * placeholder clearly flags the issue in any DB inspection without
     * silently growing the DB row to 1MB.
     */
    @Transactional
    public GuardResponse updatePhoto(TenantId tenantId, UUID id, String photoBase64) {
        Guard guard = findActive(tenantId, id);
        if (photoBase64 != null && photoBase64.startsWith("data:")) {
            log.warn("[Security] Base64 photo received for guard={} — stored as PENDING_UPLOAD. " +
                    "Wire up S3 presigned URL before production deployment.", id);
        }
        guard.updatePhoto(photoBase64);
        guardRepository.save(guard);
        return toResponse(guard);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Guard findActive(TenantId tenantId, UUID id) {
        return guardRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", id.toString()));
    }

    private GuardResponse toResponse(Guard g) {
        return new GuardResponse(
                g.getId(), g.getFirstName(), g.getLastName(),
                g.getFullName(), g.getPsiraNumber(), g.getIdNumber(),
                g.getPhone(),
                // Don't return the PENDING_UPLOAD placeholder to the frontend.
                // Return null so the UI shows the avatar fallback instead of
                // displaying the string "PENDING_UPLOAD".
                "PENDING_UPLOAD".equals(g.getPhotoUrl()) ? null : g.getPhotoUrl(),
                g.getGrade(), g.isActive(), g.getNotes(), g.getCreatedAt(),
                g.getStatus(), g.getStatusNote(), g.getStatusChangedAt(),
                g.getPsiraExpiryDate()
        );
    }
}
