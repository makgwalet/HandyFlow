package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgMovementRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgMovementRecordRepository;
import za.co.handyflow.platform.agriculture.dto.CreateMovementRecordRequest;
import za.co.handyflow.platform.agriculture.dto.MovementRecordResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Append-only movement history. Deliberately does NOT touch AgAnimal's or
 * AgGroup's own current productionAreaId/farmId here for anything beyond
 * validating the target exists — an INTERNAL_TRANSFER caller is expected to
 * also call AgAnimalService.moveAnimal()/AgGroupService.moveGroup() (or the
 * controller wires both in sequence); keeping this service focused purely
 * on recording the movement event mirrors AgMovementRecord's own Javadoc
 * ("this entity only records the fact... it does not mutate the animal/
 * group itself") for the same reason AgMortalityRecordService is the one
 * exception that DOES own that follow-through.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgMovementRecordService {

    private final AgMovementRecordRepository movementRecordRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;

    @Transactional(readOnly = true)
    public Page<MovementRecordResponse> getHistoryForAnimal(TenantId tenantId, UUID animalId, Pageable pageable) {
        return movementRecordRepository.findByAnimal(tenantId, animalId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<MovementRecordResponse> getHistoryForGroup(TenantId tenantId, UUID groupId, Pageable pageable) {
        return movementRecordRepository.findByGroup(tenantId, groupId, pageable).map(this::toResponse);
    }

    @Transactional
    public MovementRecordResponse createMovementRecord(TenantId tenantId, CreateMovementRecordRequest req) {
        validateTarget(tenantId, req.animalId(), req.groupId());
        AgMovementRecord record = AgMovementRecord.create(tenantId, req.animalId(), req.groupId(), req.movementDate(),
                req.movementType(), req.fromProductionAreaId(), req.toProductionAreaId(), req.fromFarmId(),
                req.toFarmId(), req.countMoved(), req.reason(), req.notes());
        movementRecordRepository.save(record);
        log.info("Movement record created id={} type={} tenant={}", record.getId(), req.movementType(), tenantId.getValue());
        return toResponse(record);
    }

    private void validateTarget(TenantId tenantId, UUID animalId, UUID groupId) {
        if (animalId != null && animalRepository.findActiveById(tenantId, animalId).isEmpty()) {
            throw new ResourceNotFoundException("Animal", animalId.toString());
        }
        if (groupId != null && groupRepository.findActiveById(tenantId, groupId).isEmpty()) {
            throw new ResourceNotFoundException("Group", groupId.toString());
        }
    }

    private MovementRecordResponse toResponse(AgMovementRecord m) {
        return new MovementRecordResponse(
                m.getId(), m.getAnimalId(), m.getGroupId(), m.getMovementDate(), m.getMovementType(),
                m.getFromProductionAreaId(), m.getToProductionAreaId(), m.getFromFarmId(), m.getToFarmId(),
                m.getCountMoved(), m.getReason(), m.getNotes(), m.getCreatedAt()
        );
    }
}
