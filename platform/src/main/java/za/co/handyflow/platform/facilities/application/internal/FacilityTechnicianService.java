package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityTechnician;
import za.co.handyflow.platform.facilities.domain.repository.FacilityTechnicianRepository;
import za.co.handyflow.platform.facilities.dto.TechnicianResponse;
import za.co.handyflow.platform.facilities.dto.UpsertTechnicianRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Chunk 3: the user link now works on update (it was silently ignored), a
 * user can be linked to only one active technician, and any change to the
 * link is audited because it determines which jobs a user can access.
 */
@Service
@RequiredArgsConstructor
public class FacilityTechnicianService {

    private static final UUID NO_EXCLUSION = new UUID(0L, 0L);

    private final FacilityTechnicianRepository technicianRepository;
    private final FacilityAuditService auditService;

    @Transactional(readOnly = true)
    public Page<TechnicianResponse> getTechnicians(TenantId tenantId, Pageable pageable) {
        return technicianRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional
    public TechnicianResponse createTechnician(TenantId tenantId, UpsertTechnicianRequest req) {
        requireLinkAvailable(tenantId, req.linkedUserId(), NO_EXCLUSION);
        FacilityTechnician t = FacilityTechnician.create(tenantId, req.name(), req.contactPhone(),
                req.contactEmail(), req.specialization(), req.linkedUserId());
        technicianRepository.save(t);
        if (req.linkedUserId() != null) {
            auditService.record(tenantId, "TECHNICIAN_LINK_CHANGED", "TECHNICIAN", t.getId(),
                    "linkedUser: none -> " + req.linkedUserId());
        }
        return toResponse(t);
    }

    @Transactional
    public TechnicianResponse updateTechnician(TenantId tenantId, UUID id, UpsertTechnicianRequest req) {
        FacilityTechnician t = findActive(tenantId, id);
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
    public TechnicianResponse deactivate(TenantId tenantId, UUID id) {
        FacilityTechnician t = findActive(tenantId, id);
        t.deactivate();
        technicianRepository.save(t);
        return toResponse(t);
    }

    @Transactional
    public TechnicianResponse reactivate(TenantId tenantId, UUID id) {
        FacilityTechnician t = findActive(tenantId, id);
        t.reactivate();
        technicianRepository.save(t);
        return toResponse(t);
    }

    @Transactional
    public void deleteTechnician(TenantId tenantId, UUID id) {
        FacilityTechnician t = findActive(tenantId, id);
        t.softDelete();
        technicianRepository.save(t);
    }

    private void requireLinkAvailable(TenantId tenantId, UUID userId, UUID excludeTechnicianId) {
        if (userId != null && technicianRepository.existsOtherLinkedToUser(tenantId, userId, excludeTechnicianId)) {
            throw new IllegalStateException("That user is already linked to another technician");
        }
    }

    private FacilityTechnician findActive(TenantId tenantId, UUID id) {
        return technicianRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityTechnician", id.toString()));
    }

    private TechnicianResponse toResponse(FacilityTechnician t) {
        return new TechnicianResponse(t.getId(), t.getName(), t.getContactPhone(), t.getContactEmail(),
                t.getSpecialization(), t.getLinkedUserId(), t.isActive(), t.getCreatedAt());
    }
}