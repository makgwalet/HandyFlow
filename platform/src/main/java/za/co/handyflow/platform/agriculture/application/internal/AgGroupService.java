package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.model.AgWeightRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgWeightRecordRepository;
import za.co.handyflow.platform.agriculture.domain.model.AgProductionArea;
import za.co.handyflow.platform.agriculture.domain.repository.AgProductionAreaRepository;
import za.co.handyflow.platform.agriculture.domain.model.AgGroupCountLog;
import za.co.handyflow.platform.agriculture.domain.rules.AgLifecycleRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgListRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgMovementRules;
import za.co.handyflow.platform.agriculture.dto.*;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.EmployeeSummary;
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
    private final AgProductionAreaRepository areaRepository;
    private final AgGroupCountLogService countLog;
    private final AgReferenceChecker references;

    @Transactional(readOnly = true)
    public Page<GroupResponse> getGroupsForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable) {
        return getGroupsForFarm(tenantId, farmId, status, null, pageable);
    }

    /** Filtered by status and a search on batch number and breed. The page is clamped to 1-200 rows and ordered by batch number. */
    @Transactional(readOnly = true)
    public Page<GroupResponse> getGroupsForFarm(TenantId tenantId, UUID farmId, String status, String q, Pageable pageable) {
        Pageable page = PageRequest.of(AgListRules.pageIndex(pageable.getPageNumber()), AgListRules.pageSize(pageable.getPageSize()));
        return groupRepository.searchForFarm(tenantId, farmId, AgListRules.status(status), AgListRules.likePattern(q), page)
                .map(this::toResponse);
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
        references.requireActiveFarm(tenantId, req.farmId());
        references.requireSpecies(tenantId, req.speciesId());
        references.checkPlacement(tenantId, req.farmId(), req.productionAreaId(), req.enterpriseId());
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
        java.util.Set<String> clear = AgPatchRules.clearSet(req.clear(), AgPatchRules.GROUP_CLEARABLE);
        UUID newArea = AgPatchRules.apply("productionAreaId", group.getProductionAreaId(), req.productionAreaId(), clear);
        UUID newEnterprise = AgPatchRules.apply("enterpriseId", group.getEnterpriseId(), req.enterpriseId(), clear);
        references.checkPlacement(tenantId, group.getFarmId(),
                java.util.Objects.equals(newArea, group.getProductionAreaId()) ? null : newArea,
                java.util.Objects.equals(newEnterprise, group.getEnterpriseId()) ? null : newEnterprise);
        group.update(newArea, newEnterprise,
                AgPatchRules.apply("breed", group.getBreed(), req.breed(), clear),
                AgPatchRules.apply("notes", group.getNotes(), req.notes(), clear));
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
        if (req.productionAreaId() != null) {
            AgProductionArea area = areaRepository.findActiveById(tenantId, req.productionAreaId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductionArea", req.productionAreaId().toString()));
            AgMovementRules.requireUsableArea(area.getFarmId(), area.getStatus(), group.getFarmId());
        }
        group.moveTo(req.productionAreaId());
        return toResponse(group);
    }

    @Transactional
    public GroupResponse reduceCount(TenantId tenantId, UUID id, AdjustGroupCountRequest req) {
        AgGroup group = findActive(tenantId, id);
        AgLifecycleRules.requireOnFarm(group.getStatus(), "This group");
        String reason = AgLifecycleRules.requireReason(req.reason());
        int before = group.getCurrentCount();
        group.reduceCount(req.count());
        countLog.record(tenantId, id, AgGroupCountLog.DECREASE, before, group.getCurrentCount(), reason, null, null);
        return toResponse(group);
    }

    @Transactional
    public GroupResponse increaseCount(TenantId tenantId, UUID id, AdjustGroupCountRequest req) {
        AgGroup group = findActive(tenantId, id);
        AgLifecycleRules.requireOnFarm(group.getStatus(), "This group");
        String reason = AgLifecycleRules.requireReason(req.reason());
        int before = group.getCurrentCount();
        group.increaseCount(req.count());
        countLog.record(tenantId, id, AgGroupCountLog.INCREASE, before, group.getCurrentCount(), reason, null, null);
        return toResponse(group);
    }

    @Transactional(readOnly = true)
    public Page<GroupCountLogResponse> getCountLog(TenantId tenantId, UUID id, Pageable pageable) {
        findActive(tenantId, id);
        return countLog.history(tenantId, id, pageable);
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

    /** Throws "not found" unless this group exists, is not deleted, and belongs to the tenant. */
    public void requireExists(TenantId tenantId, UUID id) { findActive(tenantId, id); }

    AgGroup findActive(TenantId tenantId, UUID id) {
        return groupRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id.toString()));
    }

    private String resolveEmployeeName(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        Optional<EmployeeSummary> employee = hrFacade.findEmployeeById(tenantId, employeeId);
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
