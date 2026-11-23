package za.co.handyflow.platform.agriculture.application.internal;

import za.co.handyflow.platform.agriculture.domain.rules.AgDates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.model.AgGroupCountLog;
import za.co.handyflow.platform.agriculture.domain.model.AgMortalityRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgMortalityRecordRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgLifecycleRules;
import za.co.handyflow.platform.agriculture.dto.CreateMortalityRecordRequest;
import za.co.handyflow.platform.agriculture.dto.MortalityRecordResponse;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Recording a mortality is the one place in this module where the history
 * entity's own Javadoc explicitly hands the follow-through mutation to the
 * SERVICE layer: {@code AgMortalityRecord} only records the fact of a loss,
 * so this method also applies {@code AgAnimal.changeStatus("DECEASED")} (for
 * an individually-tracked animal, where countLost is always 1) or
 * {@code AgGroup.reduceCount(countLost)} (for a group), inside the same
 * transaction as the record itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgMortalityRecordService {

    private final AgMortalityRecordRepository mortalityRecordRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final HrFacade hrFacade;
    private final AgGroupCountLogService countLog;

    @Transactional(readOnly = true)
    public Page<MortalityRecordResponse> getHistoryForAnimal(TenantId tenantId, UUID animalId, Pageable pageable) {
        return mortalityRecordRepository.findByAnimal(tenantId, animalId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<MortalityRecordResponse> getHistoryForGroup(TenantId tenantId, UUID groupId, Pageable pageable) {
        return mortalityRecordRepository.findByGroup(tenantId, groupId, pageable).map(this::toResponse);
    }

    @Transactional
    public MortalityRecordResponse createMortalityRecord(TenantId tenantId, CreateMortalityRecordRequest req) {
        AgLifecycleRules.requireNotFuture(req.mortalityDate(), AgDates.today(), "The date of death");
        String reportedByName = resolveEmployeeName(tenantId, req.reportedBy());
        if ((req.animalId() == null) == (req.groupId() == null))
            throw new IllegalArgumentException("exactly one of animalId or groupId is required");

        AgGroup group = null;
        int countBefore = 0;
        if (req.animalId() != null) {
            AgAnimal animal = animalRepository.findActiveById(tenantId, req.animalId())
                    .orElseThrow(() -> new ResourceNotFoundException("Animal", req.animalId().toString()));
            AgLifecycleRules.requireMortalityCount(true, req.countLost() == null ? 0 : req.countLost(), 0);
            animal.changeStatus("DECEASED");   // refused unless the animal is still ACTIVE
        } else {
            group = groupRepository.findActiveById(tenantId, req.groupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group", req.groupId().toString()));
            AgLifecycleRules.requireOnFarm(group.getStatus(), "This group");
            AgLifecycleRules.requireMortalityCount(false, req.countLost() == null ? 0 : req.countLost(), group.getCurrentCount());
            countBefore = group.getCurrentCount();
            group.reduceCount(req.countLost());
        }

        AgMortalityRecord record = AgMortalityRecord.create(tenantId, req.animalId(), req.groupId(),
                req.mortalityDate(), req.countLost(), req.causeCategory(), req.causeDetail(),
                req.estimatedValueLoss(), req.reportedBy(), reportedByName, req.notes());
        mortalityRecordRepository.save(record);
        if (group != null) {
            countLog.record(tenantId, group.getId(), AgGroupCountLog.MORTALITY, countBefore, group.getCurrentCount(),
                    "Mortality: " + req.causeCategory().trim(), "AgMortalityRecord", record.getId());
        }
        log.info("Mortality recorded id={} countLost={} tenant={}", record.getId(), req.countLost(), tenantId.getValue());
        return toResponse(record);
    }

    private String resolveEmployeeName(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        Optional<EmployeeSummary> employee = hrFacade.findEmployeeById(tenantId, employeeId);
        if (employee.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        return employee.get().fullName();
    }

    private MortalityRecordResponse toResponse(AgMortalityRecord m) {
        return new MortalityRecordResponse(
                m.getId(), m.getAnimalId(), m.getGroupId(), m.getMortalityDate(), m.getCountLost(),
                m.getCauseCategory(), m.getCauseDetail(), m.getEstimatedValueLoss(), m.getReportedBy(),
                m.getReportedByName(), m.getNotes(), m.getCreatedAt()
        );
    }
}
