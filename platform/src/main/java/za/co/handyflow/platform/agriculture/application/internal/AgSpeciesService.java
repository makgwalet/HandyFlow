package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgSpecies;
import za.co.handyflow.platform.agriculture.domain.repository.AgSpeciesRepository;
import za.co.handyflow.platform.agriculture.dto.CreateSpeciesRequest;
import za.co.handyflow.platform.agriculture.dto.SpeciesResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateSpeciesRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgSpeciesService {

    private final AgSpeciesRepository speciesRepository;

    @Transactional(readOnly = true)
    public Page<SpeciesResponse> getSpecies(TenantId tenantId, String category, Pageable pageable) {
        Page<AgSpecies> page = (category != null && !category.isBlank())
                ? speciesRepository.findAllActiveByCategory(tenantId, category, pageable)
                : speciesRepository.findAllActive(tenantId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SpeciesResponse getSpeciesById(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public SpeciesResponse createSpecies(TenantId tenantId, CreateSpeciesRequest req) {
        AgSpecies species = AgSpecies.create(tenantId, req.name(), req.category(), req.defaultUnitOfMeasure(),
                req.trackingMode(), req.gestationDays(), req.maturityWeightKg());
        speciesRepository.save(species);
        log.info("Species created id={} tenant={}", species.getId(), tenantId.getValue());
        return toResponse(species);
    }

    @Transactional
    public SpeciesResponse updateSpecies(TenantId tenantId, UUID id, UpdateSpeciesRequest req) {
        AgSpecies species = findActive(tenantId, id);
        species.update(req.name(), req.defaultUnitOfMeasure(), req.trackingMode(), req.gestationDays(), req.maturityWeightKg());
        return toResponse(species);
    }

    @Transactional
    public SpeciesResponse deactivateSpecies(TenantId tenantId, UUID id) {
        AgSpecies species = findActive(tenantId, id);
        species.deactivate();
        return toResponse(species);
    }

    @Transactional
    public SpeciesResponse reactivateSpecies(TenantId tenantId, UUID id) {
        AgSpecies species = findActive(tenantId, id);
        species.reactivate();
        return toResponse(species);
    }

    @Transactional
    public void deleteSpecies(TenantId tenantId, UUID id) {
        AgSpecies species = findActive(tenantId, id);
        species.softDelete();
        log.info("Species deleted id={} tenant={}", id, tenantId.getValue());
    }

    private AgSpecies findActive(TenantId tenantId, UUID id) {
        return speciesRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Species", id.toString()));
    }

    private SpeciesResponse toResponse(AgSpecies s) {
        return new SpeciesResponse(
                s.getId(), s.getName(), s.getCategory(), s.getDefaultUnitOfMeasure(), s.getTrackingMode(),
                s.getGestationDays(), s.getMaturityWeightKg(), s.getStatus(), s.getCreatedAt()
        );
    }
}
