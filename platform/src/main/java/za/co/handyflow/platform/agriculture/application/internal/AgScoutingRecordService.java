package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgScoutingRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgScoutingRecordRepository;
import za.co.handyflow.platform.agriculture.dto.CreateScoutingRecordRequest;
import za.co.handyflow.platform.agriculture.dto.ScoutingRecordResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateScoutingRecordRequest;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/** Mirrors AgHealthEventService's own shape — see AgScoutingRecord's own Javadoc. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgScoutingRecordService {

    private final AgScoutingRecordRepository scoutingRecordRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public Page<ScoutingRecordResponse> getHistoryForCropCycle(TenantId tenantId, UUID cropCycleId, Pageable pageable) {
        return scoutingRecordRepository.findByCropCycle(tenantId, cropCycleId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ScoutingRecordResponse getScoutingRecord(TenantId tenantId, UUID id) {
        return toResponse(findByTenantAndId(tenantId, id));
    }

    @Transactional
    public ScoutingRecordResponse createScoutingRecord(TenantId tenantId, UUID cropCycleId, CreateScoutingRecordRequest req) {
        if (cropCycleRepository.findActiveById(tenantId, cropCycleId).isEmpty()) {
            throw new ResourceNotFoundException("CropCycle", cropCycleId.toString());
        }
        String scoutedByName = resolveEmployeeName(tenantId, req.scoutedBy());
        AgScoutingRecord record = AgScoutingRecord.create(tenantId, cropCycleId, req.scoutingDate(),
                req.observationType(), req.severity(), req.description(), req.recommendedAction(),
                req.scoutedBy(), scoutedByName, req.followUpDate(), req.notes());
        scoutingRecordRepository.save(record);
        log.info("Scouting record created id={} cropCycle={} tenant={}", record.getId(), cropCycleId, tenantId.getValue());
        return toResponse(record);
    }

    @Transactional
    public ScoutingRecordResponse updateScoutingRecord(TenantId tenantId, UUID id, UpdateScoutingRecordRequest req) {
        AgScoutingRecord record = findByTenantAndId(tenantId, id);
        record.update(req.severity(), req.description(), req.recommendedAction(), req.followUpDate(), req.notes());
        return toResponse(record);
    }

    @Transactional
    public ScoutingRecordResponse resolveScoutingRecord(TenantId tenantId, UUID id) {
        AgScoutingRecord record = findByTenantAndId(tenantId, id);
        record.resolve();
        return toResponse(record);
    }

    @Transactional
    public ScoutingRecordResponse reopenScoutingRecord(TenantId tenantId, UUID id) {
        AgScoutingRecord record = findByTenantAndId(tenantId, id);
        record.reopen();
        return toResponse(record);
    }

    @Transactional
    public ScoutingRecordResponse acknowledgeFollowUp(TenantId tenantId, UUID id) {
        AgScoutingRecord record = findByTenantAndId(tenantId, id);
        record.acknowledgeFollowUp();
        return toResponse(record);
    }

    private AgScoutingRecord findByTenantAndId(TenantId tenantId, UUID id) {
        return scoutingRecordRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingRecord", id.toString()));
    }

    private String resolveEmployeeName(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        Optional<EmployeeResponse> employee = hrFacade.findEmployeeById(tenantId, employeeId);
        if (employee.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        return employee.get().fullName();
    }

    private ScoutingRecordResponse toResponse(AgScoutingRecord r) {
        return new ScoutingRecordResponse(
                r.getId(), r.getCropCycleId(), r.getScoutingDate(), r.getObservationType(), r.getSeverity(),
                r.getDescription(), r.getRecommendedAction(), r.getScoutedBy(), r.getScoutedByName(),
                r.getFollowUpDate(), r.isFollowUpAcknowledged(), r.getStatus(), r.getNotes(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
