package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgHealthEvent;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHealthEventRepository;
import za.co.handyflow.platform.agriculture.dto.CreateHealthEventRequest;
import za.co.handyflow.platform.agriculture.dto.HealthEventResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateHealthEventRequest;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgHealthEventService {

    private final AgHealthEventRepository healthEventRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public Page<HealthEventResponse> getHistoryForAnimal(TenantId tenantId, UUID animalId, Pageable pageable) {
        return healthEventRepository.findByAnimal(tenantId, animalId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<HealthEventResponse> getHistoryForGroup(TenantId tenantId, UUID groupId, Pageable pageable) {
        return healthEventRepository.findByGroup(tenantId, groupId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public HealthEventResponse getHealthEvent(TenantId tenantId, UUID id) {
        return toResponse(findByTenantAndId(tenantId, id));
    }

    @Transactional
    public HealthEventResponse createHealthEvent(TenantId tenantId, CreateHealthEventRequest req) {
        validateTarget(tenantId, req.animalId(), req.groupId());
        String administeredByName = resolveEmployeeName(tenantId, req.administeredBy());
        AgHealthEvent event = AgHealthEvent.create(tenantId, req.animalId(), req.groupId(), req.eventType(),
                req.eventDate(), req.description(), req.productUsed(), req.dosage(), req.administeredBy(),
                administeredByName, req.veterinarian(), req.cost(), req.withdrawalPeriodDays(), req.nextDueDate(),
                req.status(), req.notes());
        healthEventRepository.save(event);
        log.info("Health event created id={} type={} tenant={}", event.getId(), req.eventType(), tenantId.getValue());
        return toResponse(event);
    }

    @Transactional
    public HealthEventResponse updateHealthEvent(TenantId tenantId, UUID id, UpdateHealthEventRequest req) {
        AgHealthEvent event = findByTenantAndId(tenantId, id);
        event.update(req.description(), req.productUsed(), req.dosage(), req.veterinarian(), req.cost(),
                req.withdrawalPeriodDays(), req.nextDueDate(), req.notes());
        return toResponse(event);
    }

    @Transactional
    public HealthEventResponse markCompleted(TenantId tenantId, UUID id) {
        AgHealthEvent event = findByTenantAndId(tenantId, id);
        event.markCompleted();
        return toResponse(event);
    }

    @Transactional
    public HealthEventResponse acknowledgeReminder(TenantId tenantId, UUID id) {
        AgHealthEvent event = findByTenantAndId(tenantId, id);
        event.acknowledgeReminder();
        return toResponse(event);
    }

    private AgHealthEvent findByTenantAndId(TenantId tenantId, UUID id) {
        return healthEventRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("HealthEvent", id.toString()));
    }

    private void validateTarget(TenantId tenantId, UUID animalId, UUID groupId) {
        if (animalId != null && animalRepository.findActiveById(tenantId, animalId).isEmpty()) {
            throw new ResourceNotFoundException("Animal", animalId.toString());
        }
        if (groupId != null && groupRepository.findActiveById(tenantId, groupId).isEmpty()) {
            throw new ResourceNotFoundException("Group", groupId.toString());
        }
    }

    private String resolveEmployeeName(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        Optional<EmployeeResponse> employee = hrFacade.findEmployeeById(tenantId, employeeId);
        if (employee.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        return employee.get().fullName();
    }

    private HealthEventResponse toResponse(AgHealthEvent e) {
        return new HealthEventResponse(
                e.getId(), e.getAnimalId(), e.getGroupId(), e.getEventType(), e.getEventDate(), e.getDescription(),
                e.getProductUsed(), e.getDosage(), e.getAdministeredBy(), e.getAdministeredByName(),
                e.getVeterinarian(), e.getCost(), e.getWithdrawalPeriodDays(), e.getNextDueDate(),
                e.isReminderAcknowledged(), e.getStatus(), e.getNotes(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
