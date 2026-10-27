package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmTechnician;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmTechnicianRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmTechnicianResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpsertFmTechnicianRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Chunk 5: technicians can be linked to a system user (which drives job-level access). A user can be
 * linked to only one active technician, and any change to the link is audited.
 */
@Service
@RequiredArgsConstructor
public class FmTechnicianService {

    private static final UUID NO_EXCLUSION = new UUID(0L, 0L);

    private final FmTechnicianRepository technicianRepository;
    private final FmAuditService auditService;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public Page<FmTechnicianResponse> getTechnicians(TenantId tenantId, Pageable pageable) {
        boolean full = accessPolicy.scope(tenantId).unrestricted();
        return technicianRepository.findAllActive(tenantId, pageable).map(t -> toResponse(t, full));
    }

    /** The signed-in user's own technician record (404 when none is linked to them). */
    @Transactional(readOnly = true)
    public FmTechnicianResponse me(TenantId tenantId) {
        UUID userId = TenantContext.getCurrentUserId();
        if (userId == null) throw new HandyFlowException("Not authenticated", HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        return technicianRepository.findActiveLinkedToUser(tenantId, userId).stream().findFirst().map(t -> toResponse(t, true))
                .orElseThrow(() -> new ResourceNotFoundException("FmTechnician", "linked to you"));
    }

    @Transactional
    public FmTechnicianResponse createTechnician(TenantId tenantId, UpsertFmTechnicianRequest req) {
        requireCompanyWide(tenantId);
        requireLinkAvailable(tenantId, req.linkedUserId(), NO_EXCLUSION);
        FmTechnician t = FmTechnician.create(tenantId, req.name(), req.contactPhone(), req.contactEmail(),
                req.specialization(), req.linkedUserId());
        technicianRepository.save(t);
        auditService.record(tenantId, "TECHNICIAN_CREATED", "TECHNICIAN", t.getId(), t.getName());
        if (req.linkedUserId() != null) {
            auditService.record(tenantId, "TECHNICIAN_LINK_CHANGED", "TECHNICIAN", t.getId(),
                    "linkedUser: none -> " + req.linkedUserId());
        }
        return toResponse(t, true);
    }

    @Transactional
    public FmTechnicianResponse updateTechnician(TenantId tenantId, UUID id, UpsertFmTechnicianRequest req) {
        requireCompanyWide(tenantId);
        FmTechnician t = findActive(tenantId, id);
        UUID previousLink = t.getLinkedUserId();
        requireLinkAvailable(tenantId, req.linkedUserId(), id);
        t.update(req.name(), req.contactPhone(), req.contactEmail(), req.specialization(), req.linkedUserId());
        technicianRepository.save(t);
        auditService.record(tenantId, "TECHNICIAN_UPDATED", "TECHNICIAN", id, t.getName() + " / " + t.getSpecialization());
        if (!Objects.equals(previousLink, req.linkedUserId())) {
            auditService.record(tenantId, "TECHNICIAN_LINK_CHANGED", "TECHNICIAN", id,
                    "linkedUser: " + previousLink + " -> " + req.linkedUserId());
        }
        return toResponse(t, true);
    }

    @Transactional
    public FmTechnicianResponse deactivate(TenantId tenantId, UUID id) {
        requireCompanyWide(tenantId);
        FmTechnician t = findActive(tenantId, id);
        requireNoOpenWork(tenantId, t);
        t.deactivate();
        technicianRepository.save(t);
        auditService.record(tenantId, "TECHNICIAN_DEACTIVATED", "TECHNICIAN", id, t.getName());
        return toResponse(t, true);
    }

    @Transactional
    public FmTechnicianResponse reactivate(TenantId tenantId, UUID id) {
        requireCompanyWide(tenantId);
        FmTechnician t = findActive(tenantId, id);
        t.reactivate();
        technicianRepository.save(t);
        auditService.record(tenantId, "TECHNICIAN_REACTIVATED", "TECHNICIAN", id, t.getName());
        return toResponse(t, true);
    }

    @Transactional
    public void deleteTechnician(TenantId tenantId, UUID id) {
        requireCompanyWide(tenantId);
        FmTechnician t = findActive(tenantId, id);
        requireNoOpenWork(tenantId, t);
        t.softDelete();
        technicianRepository.save(t);
        auditService.record(tenantId, "TECHNICIAN_DELETED", "TECHNICIAN", id, t.getName());
    }

    /** Technician records are company-wide; a caller limited to certain clients may only look at the directory. */
    private void requireCompanyWide(TenantId tenantId) {
        if (!accessPolicy.scope(tenantId).unrestricted())
            throw new HandyFlowException("Your access is limited to certain clients, so you cannot change technician records",
                    HttpStatus.FORBIDDEN, "SCOPE_RESTRICTED");
    }

    private void requireNoOpenWork(TenantId tenantId, FmTechnician t) {
        long open = workOrderRepository.countOpenForTechnician(tenantId, t.getId());
        if (open > 0)
            throw new HandyFlowException(t.getName() + " still has " + open + " unfinished work order" + (open == 1 ? "" : "s")
                    + "; reassign or finish them first", HttpStatus.CONFLICT, "TECHNICIAN_HAS_OPEN_WORK");
    }

    private void requireLinkAvailable(TenantId tenantId, UUID userId, UUID excludeTechnicianId) {
        if (userId != null && technicianRepository.existsOtherLinkedToUser(tenantId, userId, excludeTechnicianId)) {
            throw new IllegalStateException("That user is already linked to another technician");
        }
    }

    FmTechnician findActive(TenantId tenantId, UUID id) {
        return technicianRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmTechnician", id.toString()));
    }

    private FmTechnicianResponse toResponse(FmTechnician t, boolean full) {
        return new FmTechnicianResponse(t.getId(), t.getName(), full ? t.getContactPhone() : null, full ? t.getContactEmail() : null,
                t.getSpecialization(), full ? t.getLinkedUserId() : null, t.isActive(), t.getCreatedAt());
    }
}