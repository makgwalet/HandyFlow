package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgCropType;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropTypeRepository;
import za.co.handyflow.platform.agriculture.dto.CreateCropTypeRequest;
import za.co.handyflow.platform.agriculture.dto.CropTypeResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateCropTypeRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** Mirrors AgSpeciesService exactly — see AgCropType's own Javadoc. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgCropTypeService {

    private final AgCropTypeRepository cropTypeRepository;

    @Transactional(readOnly = true)
    public Page<CropTypeResponse> getCropTypes(TenantId tenantId, String category, Pageable pageable) {
        Page<AgCropType> page = (category != null && !category.isBlank())
                ? cropTypeRepository.findAllActiveByCategory(tenantId, category, pageable)
                : cropTypeRepository.findAllActive(tenantId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CropTypeResponse getCropType(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public CropTypeResponse createCropType(TenantId tenantId, CreateCropTypeRequest req) {
        AgCropType cropType = AgCropType.create(tenantId, req.name(), req.category(),
                req.typicalGrowingDays(), req.defaultUnitOfMeasure());
        cropTypeRepository.save(cropType);
        log.info("Crop type created id={} tenant={}", cropType.getId(), tenantId.getValue());
        return toResponse(cropType);
    }

    @Transactional
    public CropTypeResponse updateCropType(TenantId tenantId, UUID id, UpdateCropTypeRequest req) {
        AgCropType cropType = findActive(tenantId, id);
        cropType.update(req.name(), req.typicalGrowingDays(), req.defaultUnitOfMeasure());
        return toResponse(cropType);
    }

    @Transactional
    public CropTypeResponse deactivateCropType(TenantId tenantId, UUID id) {
        AgCropType cropType = findActive(tenantId, id);
        cropType.deactivate();
        return toResponse(cropType);
    }

    @Transactional
    public CropTypeResponse reactivateCropType(TenantId tenantId, UUID id) {
        AgCropType cropType = findActive(tenantId, id);
        cropType.reactivate();
        return toResponse(cropType);
    }

    @Transactional
    public void deleteCropType(TenantId tenantId, UUID id) {
        AgCropType cropType = findActive(tenantId, id);
        cropType.softDelete();
        log.info("Crop type deleted id={} tenant={}", id, tenantId.getValue());
    }

    private AgCropType findActive(TenantId tenantId, UUID id) {
        return cropTypeRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("CropType", id.toString()));
    }

    private CropTypeResponse toResponse(AgCropType c) {
        return new CropTypeResponse(
                c.getId(), c.getName(), c.getCategory(), c.getTypicalGrowingDays(),
                c.getDefaultUnitOfMeasure(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
