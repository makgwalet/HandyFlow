package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.model.AgWeightRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgWeightRecordRepository;
import za.co.handyflow.platform.agriculture.dto.*;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Mirrors AgAnimalService for batch/flock/herd-tracked groups — see
 * {@link za.co.handyflow.platform.agriculture.domain.model.AgGroup}'s own
 * Javadoc for the group-tracking half of the individual-vs-group design
 * decision.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgGroupService {

    private final AgGroupRepository groupRepository;
    private final AgWeightRecordRepository weightRecordRepository;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public Page<GroupResponse> getGroupsForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable) {
        Page<AgGroup> page = (status != null && !status.isBlank())
                ? groupRepository.findByStatusForFarm(tenantId, farmId, status, pageable)
                : groupRepository.findAllActiveForFarm(tenantId, farmId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public GroupResponse createGroup(TenantId tenantId, CreateGroupRequest req) {
        if (groupRepository.existsActiveByFarmAndBatchNumber(tenantId, req.farmId(), req.batchNumber())) {
            throw new IllegalArgumentException(
                    "A group with batch number '" + req.batchNumber() + "' already exists on this farm");
        }
        AgGroup group = AgGroup.create(tenantId, req.farmId(), req.productionAreaId(), req.enterpriseId(),
                req.speciesId(), req.batchNumber(), req.breed(), req.initialCount(), req.originDate(),
                req.acquisitionType());
        groupRepository.save(group);
        log.info("Group created id={} batch={} farm={} tenant={}", group.getId(), req.batchNumber(), req.farmId(), tenantId.getValue());
        return toResponse(group);
    }

    @Transactional
    public GroupResponse updateGroup(TenantId tenantId, UUID id, UpdateGroupRequest req) {
        AgGroup group = findActive(tenantId, id);
        group.update(req.productionAreaId(), req.enterpriseId(), req.breed(), req.notes());
        return toResponse(group);
    }

    @Transactional
    public WeightRecordResponse recordAverageWeight(TenantId tenantId, UUID groupId, RecordWeightRequest req) {
        AgGroup group = findActive(tenantId, groupId);
        String recordedByName = resolveEmployeeName(tenantId, req.recordedBy());
        AgWeightRecord record = AgWeightRecord.create(tenantId, null, groupId, req.recordedDate(),
                req.weightKg(), req.sampleSize(), req.recordedBy(), recordedByName, req.notes());
        weightRecordRepository.save(record);
        group.recordAverageWeight(req.weightKg());
        log.info("Average weight recorded group={} weightKg={} tenant={}", groupId, req.weightKg(), tenantId.getValue());
        return toWeightResponse(record);
    }

    @Transactional(readOnly = true)
    public Page<WeightRecordResponse> getWeightHistory(TenantId tenantId, UUID groupId, Pageable pageable) {
        findActive(tenantId, groupId);
        return weightRecordRepository.findByGroup(tenantId, groupId, pageable).map(this::toWeightResponse);
    }

    @Transactional
    public GroupResponse moveGroup(TenantId tenantId, UUID id, MoveGroupRequest req) {
        AgGroup group = findActive(tenantId, id);
        group.moveTo(req.productionAreaId());
        return toResponse(group);
    }

    @Transactional
    public GroupResponse reduceCount(TenantId tenantId, UUID id, AdjustGroupCountRequest req) {
        AgGroup group = findActive(tenantId, id);
        group.reduceCount(req.count());
        return toResponse(group);
    }

    @Transactional
    public GroupResponse increaseCount(TenantId tenantId, UUID id, AdjustGroupCountRequest req) {
        AgGroup group = findActive(tenantId, id);
        group.increaseCount(req.count());
        return toResponse(group);
    }

    @Transactional
    public GroupResponse closeGroup(TenantId tenantId, UUID id) {
        AgGroup group = findActive(tenantId, id);
        group.close();
        return toResponse(group);
    }

    @Transactional
    public GroupResponse reopenGroup(TenantId tenantId, UUID id) {
        AgGroup group = findActive(tenantId, id);
        group.reopen();
        return toResponse(group);
    }

    @Transactional
    public void deleteGroup(TenantId tenantId, UUID id) {
        AgGroup group = findActive(tenantId, id);
        group.softDelete();
        log.info("Group deleted id={} tenant={}", id, tenantId.getValue());
    }

    AgGroup findActive(TenantId tenantId, UUID id) {
        return groupRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id.toString()));
    }

    private String resolveEmployeeName(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        Optional<EmployeeResponse> employee = hrFacade.findEmployeeById(tenantId, employeeId);
        if (employee.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        return employee.get().fullName();
    }

    private GroupResponse toResponse(AgGroup g) {
        return new GroupResponse(
                g.getId(), g.getFarmId(), g.getProductionAreaId(), g.getEnterpriseId(), g.getSpeciesId(),
                g.getBatchNumber(), g.getBreed(), g.getInitialCount(), g.getCurrentCount(), g.getAverageWeightKg(),
                g.getOriginDate(), g.getAcquisitionType(), g.getStatus(), g.getNotes(), g.getCreatedAt(), g.getUpdatedAt()
        );
    }

    private WeightRecordResponse toWeightResponse(AgWeightRecord w) {
        return new WeightRecordResponse(
                w.getId(), w.getAnimalId(), w.getGroupId(), w.getRecordedDate(), w.getWeightKg(),
                w.getSampleSize(), w.getRecordedBy(), w.getRecordedByName(), w.getNotes(), w.getCreatedAt()
        );
    }
}
