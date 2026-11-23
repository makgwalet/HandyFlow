package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgWeightRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgWeightRecordRepository;
import za.co.handyflow.platform.agriculture.domain.model.AgProductionArea;
import za.co.handyflow.platform.agriculture.domain.repository.AgProductionAreaRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgListRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgMovementRules;
import za.co.handyflow.platform.agriculture.dto.*;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.agriculture.domain.rules.AgDates;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules;
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
    private final AgProductionAreaRepository areaRepository;
    private final AgReferenceChecker references;
    private final AgWithdrawalService withdrawal;

    @Transactional(readOnly = true)
    public Page<AnimalResponse> getAnimalsForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable) {
        return getAnimalsForFarm(tenantId, farmId, status, null, pageable);
    }

    /** Filtered by status and a search on tag number, name and breed. The page is clamped to 1-200 rows and ordered by tag number. */
    @Transactional(readOnly = true)
    public Page<AnimalResponse> getAnimalsForFarm(TenantId tenantId, UUID farmId, String status, String q, Pageable pageable) {
        Pageable page = PageRequest.of(AgListRules.pageIndex(pageable.getPageNumber()), AgListRules.pageSize(pageable.getPageSize()));
        return animalRepository.searchForFarm(tenantId, farmId, AgListRules.status(status), AgListRules.likePattern(q), page)
                .map(this::toResponse);
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
        references.requireActiveFarm(tenantId, req.farmId());
        references.requireSpecies(tenantId, req.speciesId());
        references.checkPlacement(tenantId, req.farmId(), req.productionAreaId(), req.enterpriseId());
        UUID newId = null;   // a new animal cannot be its own parent
        references.checkParents(tenantId, newId, req.sireId(), req.damId(), req.speciesId(), req.dateOfBirth());
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
        java.util.Set<String> clear = AgPatchRules.clearSet(req.clear(), AgPatchRules.ANIMAL_CLEARABLE);
        UUID newArea = AgPatchRules.apply("productionAreaId", animal.getProductionAreaId(), req.productionAreaId(), clear);
        UUID newEnterprise = AgPatchRules.apply("enterpriseId", animal.getEnterpriseId(), req.enterpriseId(), clear);
        java.time.LocalDate newDob = AgPatchRules.apply("dateOfBirth", animal.getDateOfBirth(), req.dateOfBirth(), clear);
        UUID newSire = AgPatchRules.apply("sireId", animal.getSireId(), req.sireId(), clear);
        UUID newDam = AgPatchRules.apply("damId", animal.getDamId(), req.damId(), clear);
        // only what changed is checked, so an old record can still be edited after its area or enterprise has since been retired
        references.checkPlacement(tenantId, animal.getFarmId(),
                java.util.Objects.equals(newArea, animal.getProductionAreaId()) ? null : newArea,
                java.util.Objects.equals(newEnterprise, animal.getEnterpriseId()) ? null : newEnterprise);
        if (!java.util.Objects.equals(newSire, animal.getSireId()) || !java.util.Objects.equals(newDam, animal.getDamId())
                || !java.util.Objects.equals(newDob, animal.getDateOfBirth())) {
            references.checkParents(tenantId, animal.getId(), newSire, newDam, animal.getSpeciesId(), newDob);
        }
        animal.update(newArea, newEnterprise,
                AgPatchRules.apply("name", animal.getName(), req.name(), clear),
                AgPatchRules.apply("breed", animal.getBreed(), req.breed(), clear),
                newDob,
                req.estimatedAge() != null ? req.estimatedAge() : animal.isEstimatedAge(),
                newSire, newDam,
                AgPatchRules.apply("notes", animal.getNotes(), req.notes(), clear));
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
        if (req.productionAreaId() != null) {
            AgProductionArea area = areaRepository.findActiveById(tenantId, req.productionAreaId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductionArea", req.productionAreaId().toString()));
            AgMovementRules.requireUsableArea(area.getFarmId(), area.getStatus(), animal.getFarmId());
        }
        animal.moveTo(req.productionAreaId());
        return toResponse(animal);
    }

    @Transactional
    public AnimalResponse changeStatus(TenantId tenantId, UUID id, ChangeAnimalStatusRequest req) {
        AgAnimal animal = findActive(tenantId, id);
        // An animal in its withdrawal period cannot go for sale or slaughter without a written reason, which stays on the animal
        if (AgWithdrawalRules.LEAVES_FOR_MARKET.contains(req.status())) {
            animal.recordWithdrawalOverride(withdrawal.requireAnimalOrGroupClearOrOverride(tenantId, animal, null, AgDates.today(), req.withdrawalOverrideReason()).orElse(null));
        }
        animal.changeStatus(req.status());
        return toResponse(animal);
    }

    /** Flags or unflags an animal as breeding stock. Only changes how margins are reported; nothing else about the animal changes. */
    @Transactional
    public AnimalResponse setBreedingStock(TenantId tenantId, UUID id, boolean breedingStock) {
        AgAnimal animal = findActive(tenantId, id);
        animal.markBreedingStock(breedingStock);
        log.info("Animal breeding stock set id={} breedingStock={} tenant={}", id, breedingStock, tenantId.getValue());
        return toResponse(animal);
    }

    @Transactional
    public void deleteAnimal(TenantId tenantId, UUID id) {
        AgAnimal animal = findActive(tenantId, id);
        animal.softDelete();
        log.info("Animal deleted id={} tenant={}", id, tenantId.getValue());
    }

    /** Throws "not found" unless this animal exists, is not deleted, and belongs to the tenant. */
    public void requireExists(TenantId tenantId, UUID id) { findActive(tenantId, id); }

    AgAnimal findActive(TenantId tenantId, UUID id) {
        return animalRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Animal", id.toString()));
    }

    private String resolveEmployeeName(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        Optional<EmployeeSummary> employee = hrFacade.findEmployeeById(tenantId, employeeId);
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
                a.getCurrentWeightKg(), a.getStatus(), a.getNotes(), a.getCreatedAt(), a.getUpdatedAt(), a.isBreedingStock(),
                a.getWithdrawalOverrideReason(), a.getWithdrawalHeldUntil()
        );
    }

    private WeightRecordResponse toWeightResponse(AgWeightRecord w) {
        return new WeightRecordResponse(
                w.getId(), w.getAnimalId(), w.getGroupId(), w.getRecordedDate(), w.getWeightKg(),
                w.getSampleSize(), w.getRecordedBy(), w.getRecordedByName(), w.getNotes(), w.getCreatedAt()
        );
    }
}
