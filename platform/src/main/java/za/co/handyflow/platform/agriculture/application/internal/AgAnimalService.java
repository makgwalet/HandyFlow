package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgWeightRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgWeightRecordRepository;
import za.co.handyflow.platform.agriculture.dto.*;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * CRUD for individually-tracked animals, plus the recordWeight/move/
 * changeStatus operations. recordWeight wraps AgWeightRecord.create() and
 * AgAnimal.recordWeight() in one transaction — the same "append history,
 * update denormalized current state" shape used throughout this module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgAnimalService {

    private final AgAnimalRepository animalRepository;
    private final AgWeightRecordRepository weightRecordRepository;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public Page<AnimalResponse> getAnimalsForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable) {
        Page<AgAnimal> page = (status != null && !status.isBlank())
                ? animalRepository.findByStatusForFarm(tenantId, farmId, status, pageable)
                : animalRepository.findAllActiveForFarm(tenantId, farmId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AnimalResponse getAnimal(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public AnimalResponse createAnimal(TenantId tenantId, CreateAnimalRequest req) {
        if (animalRepository.existsActiveByFarmAndTagNumber(tenantId, req.farmId(), req.tagNumber())) {
            throw new IllegalArgumentException(
                    "An animal with tag number '" + req.tagNumber() + "' already exists on this farm");
        }
        AgAnimal animal = AgAnimal.create(tenantId, req.farmId(), req.productionAreaId(), req.enterpriseId(),
                req.speciesId(), req.tagNumber(), req.name(), req.breed(), req.sex(), req.dateOfBirth(),
                req.estimatedAge(), req.sireId(), req.damId(), req.acquisitionType(), req.acquisitionDate(),
                req.acquisitionCost());
        animalRepository.save(animal);
        log.info("Animal created id={} tag={} farm={} tenant={}", animal.getId(), req.tagNumber(), req.farmId(), tenantId.getValue());
        return toResponse(animal);
    }

    @Transactional
    public AnimalResponse updateAnimal(TenantId tenantId, UUID id, UpdateAnimalRequest req) {
        AgAnimal animal = findActive(tenantId, id);
        animal.update(req.productionAreaId(), req.enterpriseId(), req.name(), req.breed(), req.dateOfBirth(),
                req.estimatedAge(), req.sireId(), req.damId(), req.notes());
        return toResponse(animal);
    }

    @Transactional
    public WeightRecordResponse recordWeight(TenantId tenantId, UUID animalId, RecordWeightRequest req) {
        AgAnimal animal = findActive(tenantId, animalId);
        String recordedByName = resolveEmployeeName(tenantId, req.recordedBy());
        AgWeightRecord record = AgWeightRecord.create(tenantId, animalId, null, req.recordedDate(),
                req.weightKg(), req.sampleSize(), req.recordedBy(), recordedByName, req.notes());
        weightRecordRepository.save(record);
        animal.recordWeight(req.weightKg());
        log.info("Weight recorded animal={} weightKg={} tenant={}", animalId, req.weightKg(), tenantId.getValue());
        return toWeightResponse(record);
    }

    @Transactional(readOnly = true)
    public Page<WeightRecordResponse> getWeightHistory(TenantId tenantId, UUID animalId, Pageable pageable) {
        findActive(tenantId, animalId);
        return weightRecordRepository.findByAnimal(tenantId, animalId, pageable).map(this::toWeightResponse);
    }

    @Transactional
    public AnimalResponse moveAnimal(TenantId tenantId, UUID id, MoveAnimalRequest req) {
        AgAnimal animal = findActive(tenantId, id);
        animal.moveTo(req.productionAreaId());
        return toResponse(animal);
    }

    @Transactional
    public AnimalResponse changeStatus(TenantId tenantId, UUID id, ChangeAnimalStatusRequest req) {
        AgAnimal animal = findActive(tenantId, id);
        animal.changeStatus(req.status());
        return toResponse(animal);
    }

    @Transactional
    public void deleteAnimal(TenantId tenantId, UUID id) {
        AgAnimal animal = findActive(tenantId, id);
        animal.softDelete();
        log.info("Animal deleted id={} tenant={}", id, tenantId.getValue());
    }

    AgAnimal findActive(TenantId tenantId, UUID id) {
        return animalRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Animal", id.toString()));
    }

    private String resolveEmployeeName(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        Optional<EmployeeResponse> employee = hrFacade.findEmployeeById(tenantId, employeeId);
        if (employee.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }
        return employee.get().fullName();
    }

    private AnimalResponse toResponse(AgAnimal a) {
        return new AnimalResponse(
                a.getId(), a.getFarmId(), a.getProductionAreaId(), a.getEnterpriseId(), a.getSpeciesId(),
                a.getTagNumber(), a.getName(), a.getBreed(), a.getSex(), a.getDateOfBirth(), a.isEstimatedAge(),
                a.getSireId(), a.getDamId(), a.getAcquisitionType(), a.getAcquisitionDate(), a.getAcquisitionCost(),
                a.getCurrentWeightKg(), a.getStatus(), a.getNotes(), a.getCreatedAt(), a.getUpdatedAt()
        );
    }

    private WeightRecordResponse toWeightResponse(AgWeightRecord w) {
        return new WeightRecordResponse(
                w.getId(), w.getAnimalId(), w.getGroupId(), w.getRecordedDate(), w.getWeightKg(),
                w.getSampleSize(), w.getRecordedBy(), w.getRecordedByName(), w.getNotes(), w.getCreatedAt()
        );
    }
}
