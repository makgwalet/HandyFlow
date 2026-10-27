package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmTechnician;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmTechnicianRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmTechnicianResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpsertFmTechnicianRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
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

    @Transactional(readOnly = true)
    public Page<FmTechnicianResponse> getTechnicians(TenantId tenantId, Pageable pageable) {
        return technicianRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional
    public FmTechnicianResponse createTechnician(TenantId tenantId, UpsertFmTechnicianRequest req) {
        requireLinkAvailable(tenantId, req.linkedUserId(), NO_EXCLUSION);
        FmTechnician t = FmTechnician.create(tenantId, req.name(), req.contactPhone(), req.contactEmail(),
                req.specialization(), req.linkedUserId());
        technicianRepository.save(t);
        if (req.linkedUserId() != null) {
            auditService.record(tenantId, "TECHNICIAN_LINK_CHANGED", "TECHNICIAN", t.getId(),
                    "linkedUser: none -> " + req.linkedUserId());
        }
        return toResponse(t);
    }

    @Transactional
    public FmTechnicianResponse updateTechnician(TenantId tenantId, UUID id, UpsertFmTechnicianRequest req) {
        FmTechnician t = findActive(tenantId, id);
        UUID previousLink = t.getLinkedUserId();
        requireLinkAvailable(tenantId, req.linkedUserId(), id);
        t.update(req.name(), req.contactPhone(), req.contactEmail(), req.specialization(), req.linkedUserId());
        technicianRepository.save(t);
        if (!Objects.equals(previousLink, req.linkedUserId())) {
            auditService.record(tenantId, "TECHNICIAN_LINK_CHANGED", "TECHNICIAN", id,
                    "linkedUser: " + previousLink + " -> " + req.linkedUserId());
        }
        return toResponse(t);
    }

    @Transactional
    public FmTechnicianResponse deactivate(TenantId tenantId, UUID id) {
        FmTechnician t = findActive(tenantId, id);
        t.deactivate();
        technicianRepository.save(t);
        return toResponse(t);
    }

    @Transactional
    public FmTechnicianResponse reactivate(TenantId tenantId, UUID id) {
        FmTechnician t = findActive(tenantId, id);
        t.reactivate();
        technicianRepository.save(t);
        return toResponse(t);
    }

    @Transactional
    public void deleteTechnician(TenantId tenantId, UUID id) {
        FmTechnician t = findActive(tenantId, id);
        t.softDelete();
        technicianRepository.save(t);
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

    private FmTechnicianResponse toResponse(FmTechnician t) {
        return new FmTechnicianResponse(t.getId(), t.getName(), t.getContactPhone(), t.getContactEmail(),
                t.getSpecialization(), t.getLinkedUserId(), t.isActive(), t.getCreatedAt());
    }
}