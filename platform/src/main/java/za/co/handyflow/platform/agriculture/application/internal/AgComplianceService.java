package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgDates;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Hold;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Produce;
import za.co.handyflow.platform.agriculture.dto.ProduceWithdrawalResponse;
import za.co.handyflow.platform.agriculture.dto.WithdrawalStatusResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Read-side of withdrawal periods and pre-harvest intervals: is this animal, group or crop held on a given day? */
@Service
@RequiredArgsConstructor
public class AgComplianceService {

    private final AgWithdrawalService withdrawal;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgCropCycleRepository cropCycleRepository;

    @Transactional(readOnly = true)
    public WithdrawalStatusResponse animalStatus(TenantId tenantId, UUID animalId, LocalDate asOf) {
        animalRepository.findActiveById(tenantId, animalId).orElseThrow(() -> new ResourceNotFoundException("Animal", animalId.toString()));
        LocalDate day = asOf != null ? asOf : AgDates.today();
        return toResponse(withdrawal.animalHold(tenantId, animalId, day), day);
    }

    @Transactional(readOnly = true)
    public WithdrawalStatusResponse groupStatus(TenantId tenantId, UUID groupId, LocalDate asOf) {
        groupRepository.findActiveById(tenantId, groupId).orElseThrow(() -> new ResourceNotFoundException("Group", groupId.toString()));
        LocalDate day = asOf != null ? asOf : AgDates.today();
        return toResponse(withdrawal.groupHold(tenantId, groupId, day), day);
    }

    @Transactional(readOnly = true)
    public WithdrawalStatusResponse cropStatus(TenantId tenantId, UUID cropCycleId, LocalDate asOf) {
        cropCycleRepository.findActiveById(tenantId, cropCycleId).orElseThrow(() -> new ResourceNotFoundException("CropCycle", cropCycleId.toString()));
        LocalDate day = asOf != null ? asOf : AgDates.today();
        return toResponse(withdrawal.cropHold(tenantId, cropCycleId, day), day);
    }

    @Transactional(readOnly = true)
    public ProduceWithdrawalResponse animalProduceStatus(TenantId tenantId, UUID animalId, LocalDate asOf) {
        animalRepository.findActiveById(tenantId, animalId).orElseThrow(() -> new ResourceNotFoundException("Animal", animalId.toString()));
        LocalDate day = asOf != null ? asOf : AgDates.today();
        return new ProduceWithdrawalResponse(
                toResponse(withdrawal.animalProduceHold(tenantId, animalId, day, Produce.MILK), day),
                toResponse(withdrawal.animalProduceHold(tenantId, animalId, day, Produce.EGGS), day));
    }

    @Transactional(readOnly = true)
    public ProduceWithdrawalResponse groupProduceStatus(TenantId tenantId, UUID groupId, LocalDate asOf) {
        groupRepository.findActiveById(tenantId, groupId).orElseThrow(() -> new ResourceNotFoundException("Group", groupId.toString()));
        LocalDate day = asOf != null ? asOf : AgDates.today();
        return new ProduceWithdrawalResponse(
                toResponse(withdrawal.groupProduceHold(tenantId, groupId, day, Produce.MILK), day),
                toResponse(withdrawal.groupProduceHold(tenantId, groupId, day, Produce.EGGS), day));
    }

    private static WithdrawalStatusResponse toResponse(Optional<Hold> hold, LocalDate day) {
        return hold.map(h -> new WithdrawalStatusResponse(true, day, h.treatedOn(), h.clearOn(), h.product()))
                .orElseGet(() -> new WithdrawalStatusResponse(false, day, null, null, null));
    }
}
