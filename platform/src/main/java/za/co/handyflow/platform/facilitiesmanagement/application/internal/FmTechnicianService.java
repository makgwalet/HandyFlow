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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FmTechnicianService {

    private final FmTechnicianRepository technicianRepository;

    @Transactional(readOnly = true)
    public Page<FmTechnicianResponse> getTechnicians(TenantId tenantId, Pageable pageable) {
        return technicianRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional
    public FmTechnicianResponse createTechnician(TenantId tenantId, UpsertFmTechnicianRequest req) {
        FmTechnician t = FmTechnician.create(tenantId, req.name(), req.contactPhone(), req.contactEmail(), req.specialization());
        technicianRepository.save(t);
        return toResponse(t);
    }

    @Transactional
    public FmTechnicianResponse updateTechnician(TenantId tenantId, UUID id, UpsertFmTechnicianRequest req) {
        FmTechnician t = findActive(tenantId, id);
        t.update(req.name(), req.contactPhone(), req.contactEmail(), req.specialization());
        technicianRepository.save(t);
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

    FmTechnician findActive(TenantId tenantId, UUID id) {
        return technicianRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmTechnician", id.toString()));
    }

    private FmTechnicianResponse toResponse(FmTechnician t) {
        return new FmTechnicianResponse(t.getId(), t.getName(), t.getContactPhone(), t.getContactEmail(),
                t.getSpecialization(), t.isActive(), t.getCreatedAt());
    }
}
