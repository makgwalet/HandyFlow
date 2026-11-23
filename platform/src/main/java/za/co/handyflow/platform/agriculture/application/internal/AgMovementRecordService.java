package za.co.handyflow.platform.agriculture.application.internal;

import za.co.handyflow.platform.agriculture.domain.rules.AgDates;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.model.AgGroupCountLog;
import za.co.handyflow.platform.agriculture.domain.model.AgMovementRecord;
import za.co.handyflow.platform.agriculture.domain.model.AgProductionArea;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgProductionAreaRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgLifecycleRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgMovementRules;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgMovementRecordRepository;
import za.co.handyflow.platform.agriculture.dto.CreateMovementRecordRequest;
import za.co.handyflow.platform.agriculture.dto.MovementRecordResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Movement history that also changes the animal or group, in the same transaction, after checking it is still on the farm:
 * AREA_TRANSFER moves it to another area of the same farm (a part-group move is recorded, the group stays where it is);
 * FARM_TRANSFER moves an animal, or a whole group, to another farm; SALE and TRANSFER_OUT mark an animal sold or transferred
 * out, or take head off a group (a reason or buyer is required) and log the count change; an animal or group still in its
 * withdrawal period cannot be sold or transferred out without a written override reason, which is kept on the record. The "from" area and farm are read from
 * the animal, not trusted from the request. See AgMovementRules and AgLifecycleRules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgMovementRecordService {

    private final AgMovementRecordRepository movementRecordRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgProductionAreaRepository areaRepository;
    private final AgFarmRepository farmRepository;
    private final AgGroupCountLogService countLog;
    private final AgWithdrawalService withdrawal;

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
        String type = AgMovementRules.normaliseType(req.movementType());
        AgLifecycleRules.requireNotFuture(req.movementDate(), AgDates.today(), "The movement date");
        Target target = loadTarget(tenantId, req.animalId(), req.groupId());
        AgLifecycleRules.requireOnFarm(target.status(), target.animal() != null ? "This animal" : "This group");

        UUID fromArea = req.fromProductionAreaId();
        UUID toArea = req.toProductionAreaId();
        UUID fromFarm = req.fromFarmId();
        UUID toFarm = req.toFarmId();
        Integer count = req.countMoved();
        String reason = req.reason();
        int groupCount = target.group() != null ? target.group().getCurrentCount() : 0;
        AgGroup group = target.group();
        int countBefore = groupCount;
        String countChange = null;
        String notes = req.notes();
        AgWithdrawalRules.Override override = null;   // set when a sale goes ahead inside a withdrawal period

        switch (type) {
            case "AREA_TRANSFER" -> {
                if (toArea == null) throw new IllegalArgumentException("Choose the production area to move to");
                AgProductionArea area = areaRepository.findActiveById(tenantId, toArea)
                        .orElseThrow(() -> new ResourceNotFoundException("ProductionArea", toArea.toString()));
                AgMovementRules.requireUsableArea(area.getFarmId(), area.getStatus(), target.farmId());
                fromArea = target.currentAreaId();
                AgMovementRules.requireDifferentArea(fromArea, toArea);
                if (target.animal() != null) {
                    target.animal().moveTo(toArea);
                    count = null;
                } else {
                    AgMovementRules.requireCountWithinGroup(count, groupCount);
                    if (AgMovementRules.movesWholeGroup(count, groupCount)) group.moveTo(toArea);
                }
            }
            case "FARM_TRANSFER" -> {
                AgLifecycleRules.requireDifferentFarm(target.farmId(), toFarm);
                farmRepository.findActiveById(tenantId, toFarm)
                        .orElseThrow(() -> new ResourceNotFoundException("Farm", toFarm.toString()));
                if (toArea != null) {
                    AgProductionArea area = areaRepository.findActiveById(tenantId, toArea)
                            .orElseThrow(() -> new ResourceNotFoundException("ProductionArea", toArea.toString()));
                    AgMovementRules.requireUsableArea(area.getFarmId(), area.getStatus(), toFarm);
                }
                fromFarm = target.farmId();
                fromArea = target.currentAreaId();
                if (target.animal() != null) {
                    target.animal().moveToFarm(toFarm, toArea);
                    count = null;
                } else {
                    AgLifecycleRules.requireWholeGroupForFarmTransfer(count, groupCount);
                    group.moveToFarm(toFarm, toArea);
                }
            }
            case "SALE", "TRANSFER_OUT" -> {
                // Meat or milk still inside its withdrawal period must not leave the farm, unless someone says why it is safe
                override = withdrawal.requireAnimalOrGroupClearOrOverride(
                        tenantId, target.animal(), target.group(), req.movementDate(), req.withdrawalOverrideReason()).orElse(null);
                fromFarm = target.farmId();
                fromArea = target.currentAreaId();
                if (target.animal() != null) {
                    target.animal().changeStatus(AgLifecycleRules.animalStatusForMovement(type));
                    count = null;
                } else {
                    int leaving = AgLifecycleRules.headsLeaving(count, groupCount);
                    reason = AgLifecycleRules.requireReason(reason);
                    group.reduceCount(leaving);   // closes the group at zero
                    count = leaving;
                    countChange = "SALE".equals(type) ? AgGroupCountLog.SALE : AgGroupCountLog.TRANSFER_OUT;
                }
            }
            default -> throw new IllegalArgumentException("Unsupported movement type " + type);
        }

        // buyer and vehicle details only belong to a sale or transfer out
        boolean leaves = "SALE".equals(type) || "TRANSFER_OUT".equals(type);
        AgMovementRecord record = AgMovementRecord.create(tenantId, req.animalId(), req.groupId(), req.movementDate(),
                type, fromArea, toArea, fromFarm, toFarm, count, reason, notes,
                leaves ? req.buyerName() : null, leaves ? req.buyerIdNumber() : null, leaves ? req.buyerAddress() : null,
                leaves ? req.destination() : null, leaves ? req.vehicleRegistration() : null);
        record.recordWithdrawalOverride(override);
        movementRecordRepository.save(record);
        if (countChange != null) {
            countLog.record(tenantId, group.getId(), countChange, countBefore, group.getCurrentCount(), reason, "AgMovementRecord", record.getId());
        }
        log.info("Movement record created id={} type={} tenant={}", record.getId(), type, tenantId.getValue());
        return toResponse(record);
    }

    /** The animal or group, which must exist. Exactly one of the two ids is given. */
    private Target loadTarget(TenantId tenantId, UUID animalId, UUID groupId) {
        if ((animalId == null) == (groupId == null))
            throw new IllegalArgumentException("exactly one of animalId or groupId is required");
        if (animalId != null) {
            AgAnimal a = animalRepository.findActiveById(tenantId, animalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Animal", animalId.toString()));
            return new Target(a, null, a.getFarmId(), a.getProductionAreaId(), a.getStatus());
        }
        AgGroup g = groupRepository.findActiveById(tenantId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId.toString()));
        return new Target(null, g, g.getFarmId(), g.getProductionAreaId(), g.getStatus());
    }

    private record Target(AgAnimal animal, AgGroup group, UUID farmId, UUID currentAreaId, String status) {}

    private MovementRecordResponse toResponse(AgMovementRecord m) {
        return new MovementRecordResponse(
                m.getId(), m.getAnimalId(), m.getGroupId(), m.getMovementDate(), m.getMovementType(),
                m.getFromProductionAreaId(), m.getToProductionAreaId(), m.getFromFarmId(), m.getToFarmId(),
                m.getCountMoved(), m.getReason(), m.getNotes(), m.getCreatedAt(),
                m.getBuyerName(), m.getBuyerIdNumber(), m.getBuyerAddress(), m.getDestination(), m.getVehicleRegistration(),
                m.getWithdrawalOverrideReason(), m.getWithdrawalHeldUntil()
        );
    }
}
