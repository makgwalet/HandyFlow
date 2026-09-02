package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.dto.AssignManagerRequest;
import za.co.handyflow.platform.agriculture.dto.CreateFarmRequest;
import za.co.handyflow.platform.agriculture.dto.FarmResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateFarmRequest;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * CRUD for the top-level farm record. Manager assignment validates the
 * employee exists via {@link HrFacade} and snapshots the display name at
 * write time — see package-info.java's "HR LINKAGE" section for why this
 * module follows {@code training}'s precedent rather than earthmoving/
 * fleet's free-text one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgFarmService {

    private final AgFarmRepository farmRepository;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public Page<FarmResponse> getFarms(TenantId tenantId, String status, Pageable pageable) {
        Page<AgFarm> page = (status != null && !status.isBlank())
                ? farmRepository.findByStatus(tenantId, status, pageable)
                : farmRepository.findAllActive(tenantId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FarmResponse getFarm(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public FarmResponse createFarm(TenantId tenantId, CreateFarmRequest req) {
        AgFarm farm = AgFarm.create(tenantId, req.name(), req.farmType(), req.registrationNumber(),
                req.province(), req.region(), req.totalHectares());
        farmRepository.save(farm);
        log.info("Farm created id={} tenant={}", farm.getId(), tenantId.getValue());
        return toResponse(farm);
    }

    @Transactional
    public FarmResponse updateFarm(TenantId tenantId, UUID id, UpdateFarmRequest req) {
        AgFarm farm = findActive(tenantId, id);
        farm.update(req.name(), req.farmType(), req.registrationNumber(), req.province(), req.region(),
                req.gpsLatitude(), req.gpsLongitude(), req.totalHectares(), req.notes());
        return toResponse(farm);
    }

    @Transactional
    public FarmResponse assignManager(TenantId tenantId, UUID id, AssignManagerRequest req) {
        AgFarm farm = findActive(tenantId, id);
        if (req.managerId() == null) {
            farm.clearManager();
            return toResponse(farm);
        }
        EmployeeResponse employee = hrFacade.findEmployeeById(tenantId, req.managerId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + req.managerId()));
        farm.assignManager(employee.id(), employee.fullName());
        return toResponse(farm);
    }

    @Transactional
    public FarmResponse deactivateFarm(TenantId tenantId, UUID id) {
        AgFarm farm = findActive(tenantId, id);
        farm.deactivate();
        return toResponse(farm);
    }

    @Transactional
    public FarmResponse reactivateFarm(TenantId tenantId, UUID id) {
        AgFarm farm = findActive(tenantId, id);
        farm.reactivate();
        return toResponse(farm);
    }

    @Transactional
    public void deleteFarm(TenantId tenantId, UUID id) {
        AgFarm farm = findActive(tenantId, id);
        farm.softDelete();
        log.info("Farm deleted id={} tenant={}", id, tenantId.getValue());
    }

    private AgFarm findActive(TenantId tenantId, UUID id) {
        return farmRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", id.toString()));
    }

    private FarmResponse toResponse(AgFarm f) {
        return new FarmResponse(
                f.getId(), f.getName(), f.getFarmType(), f.getRegistrationNumber(),
                f.getProvince(), f.getRegion(), f.getGpsLatitude(), f.getGpsLongitude(),
                f.getTotalHectares(), f.getManagerId(), f.getManagerName(),
                f.getStatus(), f.getNotes(), f.getCreatedAt(), f.getUpdatedAt()
        );
    }
}
