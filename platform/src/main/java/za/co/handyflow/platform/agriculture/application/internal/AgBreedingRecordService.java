package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgBreedingRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgBreedingRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.dto.BreedingRecordResponse;
import za.co.handyflow.platform.agriculture.dto.ConfirmPregnantRequest;
import za.co.handyflow.platform.agriculture.dto.CreateBreedingRecordRequest;
import za.co.handyflow.platform.agriculture.dto.RecordBirthRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgBreedingRecordService {

    private final AgBreedingRecordRepository breedingRecordRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;

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
        return toResponse(findByTenantAndId(tenantId, id));
    }

    @Transactional
    public BreedingRecordResponse createBreedingRecord(TenantId tenantId, CreateBreedingRecordRequest req) {
        validateTarget(tenantId, req.animalId(), req.groupId());
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
        record.recordBirth(req.actualBirthDate(), req.offspringCount());
        return toResponse(record);
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

    private void validateTarget(TenantId tenantId, UUID animalId, UUID groupId) {
        if (animalId != null && animalRepository.findActiveById(tenantId, animalId).isEmpty()) {
            throw new ResourceNotFoundException("Animal", animalId.toString());
        }
        if (groupId != null && groupRepository.findActiveById(tenantId, groupId).isEmpty()) {
            throw new ResourceNotFoundException("Group", groupId.toString());
        }
    }

    private BreedingRecordResponse toResponse(AgBreedingRecord b) {
        return new BreedingRecordResponse(
                b.getId(), b.getAnimalId(), b.getGroupId(), b.getBreedingType(), b.getMatingDate(), b.getSireId(),
                b.getSireDescription(), b.getExpectedDueDate(), b.getActualBirthDate(), b.getOutcome(),
                b.getOffspringCount(), b.getNotes(), b.getCreatedAt(), b.getUpdatedAt()
        );
    }
}
