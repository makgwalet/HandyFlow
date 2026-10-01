package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgCropType;
import za.co.handyflow.platform.agriculture.domain.model.AgHarvestRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropTypeRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHarvestRecordRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgCropLifecycle;
import za.co.handyflow.platform.agriculture.domain.rules.AgUnits;
import za.co.handyflow.platform.agriculture.dto.CreateHarvestRecordRequest;
import za.co.handyflow.platform.agriculture.dto.HarvestRecordResponse;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Create + list only — append-only yield history. Deliberately does NOT
 * touch {@code AgCropCycle}'s own status; the harvest-transition endpoints
 * on {@code AgCropCycleService} (startHarvest/completeHarvest) are the
 * caller's own responsibility to invoke, since multiple harvest records
 * can be logged against one cycle while it is still in HARVESTING status
 * (a multi-pick crop) — see {@link AgHarvestRecord}'s own Javadoc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgHarvestRecordService {

    private final AgHarvestRecordRepository harvestRecordRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgCropTypeRepository cropTypeRepository;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public Page<HarvestRecordResponse> getHistoryForCropCycle(TenantId tenantId, UUID cropCycleId, Pageable pageable) {
        return harvestRecordRepository.findByCropCycle(tenantId, cropCycleId, pageable).map(this::toResponse);
    }

    @Transactional
    public HarvestRecordResponse createHarvestRecord(TenantId tenantId, UUID cropCycleId, CreateHarvestRecordRequest req) {
        AgCropCycle cycle = cropCycleRepository.findActiveById(tenantId, cropCycleId)
                .orElseThrow(() -> new ResourceNotFoundException("CropCycle", cropCycleId.toString()));
        AgCropLifecycle.requireAcceptsRecords(AgCropLifecycle.RecordKind.HARVEST, cycle.getStatus());
        // Yield is summed in the crop's own unit, so the unit entered must be convertible to it (a crop type that has
        // since been deleted simply skips the check).
        cropTypeRepository.findActiveById(tenantId, cycle.getCropTypeId())
                .map(AgCropType::getDefaultUnitOfMeasure)
                .ifPresent(cropUnit -> AgUnits.requireConvertible(req.unitOfMeasure(), cropUnit));
        String harvestedByName = resolveEmployeeName(tenantId, req.harvestedBy());
        AgHarvestRecord record = AgHarvestRecord.create(tenantId, cropCycleId, req.harvestDate(),
                req.quantityHarvested(), req.unitOfMeasure(), req.qualityGrade(), req.moistureContent(),
                req.storageLocation(), req.harvestedBy(), harvestedByName, req.laborHours(), req.notes());
        harvestRecordRepository.save(record);
        log.info("Harvest recorded id={} cropCycle={} quantity={} tenant={}",
                record.getId(), cropCycleId, req.quantityHarvested(), tenantId.getValue());
        return toResponse(record);
    }

    private String resolveEmployeeName(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        Optional<EmployeeResponse> employee = hrFacade.findEmployeeById(tenantId, employeeId);
        if (employee.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        return employee.get().fullName();
    }

    private HarvestRecordResponse toResponse(AgHarvestRecord h) {
        return new HarvestRecordResponse(
                h.getId(), h.getCropCycleId(), h.getHarvestDate(), h.getQuantityHarvested(), h.getUnitOfMeasure(),
                h.getQualityGrade(), h.getMoistureContent(), h.getStorageLocation(), h.getHarvestedBy(),
                h.getHarvestedByName(), h.getLaborHours(), h.getNotes(), h.getCreatedAt()
        );
    }
}
