package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgProductionArea;
import za.co.handyflow.platform.agriculture.domain.repository.AgProductionAreaRepository;
import za.co.handyflow.platform.agriculture.dto.ChangeAreaStatusRequest;
import za.co.handyflow.platform.agriculture.dto.CreateProductionAreaRequest;
import za.co.handyflow.platform.agriculture.dto.ProductionAreaResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateProductionAreaRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgProductionAreaService {

    private final AgProductionAreaRepository areaRepository;

    @Transactional(readOnly = true)
    public Page<ProductionAreaResponse> getAreasForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable) {
        Page<AgProductionArea> page = (status != null && !status.isBlank())
                ? areaRepository.findByStatusForFarm(tenantId, farmId, status, pageable)
                : areaRepository.findAllActiveForFarm(tenantId, farmId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductionAreaResponse getArea(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public ProductionAreaResponse createArea(TenantId tenantId, CreateProductionAreaRequest req) {
        AgProductionArea area = AgProductionArea.create(tenantId, req.farmId(), req.name(), req.areaType(),
                req.sizeHectares(), req.capacity(), req.soilType());
        areaRepository.save(area);
        log.info("Production area created id={} farm={} tenant={}", area.getId(), req.farmId(), tenantId.getValue());
        return toResponse(area);
    }

    @Transactional
    public ProductionAreaResponse updateArea(TenantId tenantId, UUID id, UpdateProductionAreaRequest req) {
        AgProductionArea area = findActive(tenantId, id);
        area.update(req.name(), req.areaType(), req.sizeHectares(), req.capacity(), req.soilType(), req.notes());
        return toResponse(area);
    }

    @Transactional
    public ProductionAreaResponse changeStatus(TenantId tenantId, UUID id, ChangeAreaStatusRequest req) {
        AgProductionArea area = findActive(tenantId, id);
        area.changeStatus(req.status());
        return toResponse(area);
    }

    @Transactional
    public void deleteArea(TenantId tenantId, UUID id) {
        AgProductionArea area = findActive(tenantId, id);
        area.softDelete();
        log.info("Production area deleted id={} tenant={}", id, tenantId.getValue());
    }

    private AgProductionArea findActive(TenantId tenantId, UUID id) {
        return areaRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionArea", id.toString()));
    }

    private ProductionAreaResponse toResponse(AgProductionArea a) {
        return new ProductionAreaResponse(
                a.getId(), a.getFarmId(), a.getName(), a.getAreaType(), a.getSizeHectares(),
                a.getCapacity(), a.getSoilType(), a.getStatus(), a.getNotes(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
