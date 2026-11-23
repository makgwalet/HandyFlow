package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgBreedingRecord;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.model.AgGroupCountLog;
import za.co.handyflow.platform.agriculture.domain.model.AgSpecies;
import za.co.handyflow.platform.agriculture.domain.repository.AgSpeciesRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgBirthRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgLifecycleRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgBreedingRules;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgBreedingRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.dto.BreedingRecordResponse;
import za.co.handyflow.platform.agriculture.dto.ConfirmPregnantRequest;
import za.co.handyflow.platform.agriculture.dto.CreateBreedingRecordRequest;
import za.co.handyflow.platform.agriculture.dto.RecordBirthRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgBreedingRecordService {

    private final AgBreedingRecordRepository breedingRecordRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgSpeciesRepository speciesRepository;
    private final AgGroupCountLogService countLog;

    @Transactional(readOnly = true)
    public Page<BreedingRecordResponse> getHistoryForAnimal(TenantId tenantId, UUID animalId, Pageable pageable) {
        return breedingRecordRepository.findByAnimal(tenantId, animalId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BreedingRecordResponse> getHistoryForGroup(TenantId tenantId, UUID groupId, Pageable pageable) {
        return breedingRecordRepository.findByGroup(tenantId, groupId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BreedingRecordResponse getBreedingRecord(TenantId tenantId, UUID id) {
        AgBreedingRecord record = findByTenantAndId(tenantId, id);
        return toResponse(record, animalRepository.findByBirthRecord(tenantId, id).stream().map(AgAnimal::getId).toList());
    }

    @Transactional
    public BreedingRecordResponse createBreedingRecord(TenantId tenantId, CreateBreedingRecordRequest req) {
        validateTarget(tenantId, req.animalId(), req.groupId(), req.sireId());
        AgBreedingRecord record = AgBreedingRecord.create(tenantId, req.animalId(), req.groupId(), req.breedingType(),
                req.matingDate(), req.sireId(), req.sireDescription(), req.expectedDueDate(), req.notes());
        breedingRecordRepository.save(record);
        log.info("Breeding record created id={} tenant={}", record.getId(), tenantId.getValue());
        return toResponse(record);
    }

    @Transactional
    public BreedingRecordResponse confirmPregnant(TenantId tenantId, UUID id, ConfirmPregnantRequest req) {
        AgBreedingRecord record = findByTenantAndId(tenantId, id);
        record.confirmPregnant(req.expectedDueDate());
        return toResponse(record);
    }

    @Transactional
    public BreedingRecordResponse markNotPregnant(TenantId tenantId, UUID id) {
        AgBreedingRecord record = findByTenantAndId(tenantId, id);
        record.markNotPregnant();
        return toResponse(record);
    }

    @Transactional
    public BreedingRecordResponse recordBirth(TenantId tenantId, UUID id, RecordBirthRequest req) {
        AgBreedingRecord record = findByTenantAndId(tenantId, id);
        record.recordBirth(req.actualBirthDate(), req.offspringCount(), req.stillbornCount());
        // the record has passed its own checks, so the counts are valid; what is born now changes the herd
        int live = AgBreedingRules.requireBirthCounts(req.offspringCount(), req.stillbornCount())[0];
        List<UUID> created = List.of();
        if (live > 0) {
            if (record.getAnimalId() != null) created = createOffspring(tenantId, record, live, req.offspring());
            else addToGroup(tenantId, record, live);
        }
        return toResponse(record, created);
    }

    /**
     * One animal per live offspring when the dam is an individually tracked animal: born on the farm on the birth date, with the
     * dam's farm, area, enterprise, species and breed, the record's sire if known, and the sex entered for each. A tag the farmer
     * typed is used; otherwise the animal gets a temporary tag to be replaced later. A GROUP-tracked species gets no animals.
     * Any problem rolls the whole birth back, so a record is never left BIRTHED without its animals.
     */
    private List<UUID> createOffspring(TenantId tenantId, AgBreedingRecord record, int live, List<RecordBirthRequest.Offspring> entered) {
        AgAnimal dam = animalRepository.findActiveById(tenantId, record.getAnimalId())
                .orElseThrow(() -> new ResourceNotFoundException("Animal", record.getAnimalId().toString()));
        AgSpecies species = speciesRepository.findById(dam.getSpeciesId())
                .orElseThrow(() -> new ResourceNotFoundException("Species", dam.getSpeciesId().toString()));
        if (!AgBirthRules.createsIndividualAnimals(species.getTrackingMode())) return List.of();

        List<AgBirthRules.Offspring> offspring = new ArrayList<>();
        if (entered != null) for (RecordBirthRequest.Offspring o : entered) offspring.add(o == null ? null : new AgBirthRules.Offspring(o.sex(), o.tagNumber(), o.name()));
        List<String> sexes = AgBirthRules.requireOffspringDetail(live, offspring);

        Set<String> taken = new HashSet<>(animalRepository.findTagNumbersStartingWith(tenantId, dam.getFarmId(), dam.getTagNumber().trim() + "-"));
        List<String> requested = new ArrayList<>();
        for (AgBirthRules.Offspring o : offspring) {
            String tag = o.tagNumber() == null ? "" : o.tagNumber().trim();
            if (!tag.isEmpty() && animalRepository.existsActiveByFarmAndTagNumber(tenantId, dam.getFarmId(), tag)) taken.add(tag);
            requested.add(tag);
        }
        List<AgBirthRules.Assigned> tags = AgBirthRules.assignTags(dam.getTagNumber(), record.getActualBirthDate(), requested, taken);

        List<AgAnimal> born = new ArrayList<>();
        for (int i = 0; i < live; i++) {
            String name = offspring.get(i).name() == null || offspring.get(i).name().isBlank() ? null : offspring.get(i).name().trim();
            AgAnimal calf = AgAnimal.create(tenantId, dam.getFarmId(), dam.getProductionAreaId(), dam.getEnterpriseId(), dam.getSpeciesId(),
                    tags.get(i).tagNumber(), name, dam.getBreed(), sexes.get(i), record.getActualBirthDate(), false, record.getSireId(), dam.getId(),
                    "BORN_ON_FARM", record.getActualBirthDate(), null);
            calf.markNewborn(record.getId(), tags.get(i).temporary());
            born.add(calf);
        }
        animalRepository.saveAll(born);
        log.info("Birth created {} animal(s) breedingRecord={} dam={} tenant={}", born.size(), record.getId(), dam.getId(), tenantId.getValue());
        return born.stream().map(AgAnimal::getId).toList();
    }

    /** A group's head count goes up by the number born alive, with a line on its count log. */
    private void addToGroup(TenantId tenantId, AgBreedingRecord record, int live) {
        AgGroup group = groupRepository.findActiveById(tenantId, record.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group", record.getGroupId().toString()));
        AgLifecycleRules.requireOnFarm(group.getStatus(), "This group");
        int before = group.getCurrentCount();
        group.increaseCount(live);
        countLog.record(tenantId, group.getId(), AgGroupCountLog.INCREASE, before, group.getCurrentCount(),
                AgBirthRules.groupCountReason(live, record.getActualBirthDate()), "BREEDING_RECORD", record.getId());
    }

    @Transactional
    public BreedingRecordResponse markAborted(TenantId tenantId, UUID id) {
        AgBreedingRecord record = findByTenantAndId(tenantId, id);
        record.markAborted();
        return toResponse(record);
    }

    @Transactional
    public BreedingRecordResponse markFailed(TenantId tenantId, UUID id) {
        AgBreedingRecord record = findByTenantAndId(tenantId, id);
        record.markFailed();
        return toResponse(record);
    }

    private AgBreedingRecord findByTenantAndId(TenantId tenantId, UUID id) {
        return breedingRecordRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BreedingRecord", id.toString()));
    }

    /** The animal or group must exist; an animal must be a live female; a named sire must be a live, uncastrated male. */
    private void validateTarget(TenantId tenantId, UUID animalId, UUID groupId, UUID sireId) {
        if (animalId != null) {
            AgAnimal dam = animalRepository.findActiveById(tenantId, animalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Animal", animalId.toString()));
            AgBreedingRules.requireDam(dam.getSex(), dam.getStatus());
        }
        if (groupId != null && groupRepository.findActiveById(tenantId, groupId).isEmpty()) {
            throw new ResourceNotFoundException("Group", groupId.toString());
        }
        if (sireId != null) {
            if (sireId.equals(animalId)) throw new IllegalArgumentException("An animal cannot be its own sire");
            AgAnimal sire = animalRepository.findActiveById(tenantId, sireId)
                    .orElseThrow(() -> new ResourceNotFoundException("Animal", sireId.toString()));
            AgBreedingRules.requireSire(sire.getSex(), sire.getStatus());
        }
    }

    private BreedingRecordResponse toResponse(AgBreedingRecord b) {
        return toResponse(b, List.of());
    }

    private BreedingRecordResponse toResponse(AgBreedingRecord b, List<UUID> offspringAnimalIds) {
        return new BreedingRecordResponse(
                b.getId(), b.getAnimalId(), b.getGroupId(), b.getBreedingType(), b.getMatingDate(), b.getSireId(),
                b.getSireDescription(), b.getExpectedDueDate(), b.getActualBirthDate(), b.getOutcome(),
                b.getOffspringCount(), b.getStillbornCount(), b.getNotes(), b.getCreatedAt(), b.getUpdatedAt(), offspringAnimalIds
        );
    }
}
