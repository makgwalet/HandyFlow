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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FacilityTechnicianService {

    private final FacilityTechnicianRepository technicianRepository;

    @Transactional(readOnly = true)
    public Page<TechnicianResponse> getTechnicians(TenantId tenantId, Pageable pageable) {
        return technicianRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional
    public TechnicianResponse createTechnician(TenantId tenantId, UpsertTechnicianRequest req) {
        FacilityTechnician t = FacilityTechnician.create(tenantId, req.name(), req.contactPhone(),
                req.contactEmail(), req.specialization(), req.linkedUserId());
        technicianRepository.save(t);
        return toResponse(t);
    }

    @Transactional
    public TechnicianResponse updateTechnician(TenantId tenantId, UUID id, UpsertTechnicianRequest req) {
        FacilityTechnician t = findActive(tenantId, id);
        t.update(req.name(), req.contactPhone(), req.contactEmail(), req.specialization());
        technicianRepository.save(t);
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

    private FacilityTechnician findActive(TenantId tenantId, UUID id) {
        return technicianRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityTechnician", id.toString()));
    }

    private TechnicianResponse toResponse(FacilityTechnician t) {
        return new TechnicianResponse(t.getId(), t.getName(), t.getContactPhone(), t.getContactEmail(),
                t.getSpecialization(), t.getLinkedUserId(), t.isActive(), t.getCreatedAt());
    }
}
