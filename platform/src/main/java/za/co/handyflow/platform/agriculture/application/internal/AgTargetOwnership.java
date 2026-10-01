package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgEnterpriseRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgCostAllocation;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * The one place that decides whether a crop cycle, group, animal or enterprise may carry a cost or a sale for a farm: it must exist in the
 * tenant AND belong to that farm, so a cost or revenue can never be pinned on another farm's crop or herd. Shared by the cost ledger (W1)
 * and sales allocations (W2) so the two cannot drift apart.
 */
@Component
@RequiredArgsConstructor
public class AgTargetOwnership {

    private final AgCropCycleRepository cropCycleRepository;
    private final AgGroupRepository groupRepository;
    private final AgAnimalRepository animalRepository;
    private final AgEnterpriseRepository enterpriseRepository;

    /** @throws ResourceNotFoundException the target does not exist in this tenant
     *  @throws IllegalArgumentException the target type is unknown, or the target belongs to a different farm */
    public void requireOnFarm(TenantId tenantId, UUID farmId, String targetType, UUID targetId) {
        UUID owner = switch (targetType) {
            case AgCostAllocation.CROP_CYCLE -> cropCycleRepository.findActiveById(tenantId, targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("CropCycle", targetId.toString())).getFarmId();
            case AgCostAllocation.GROUP -> groupRepository.findActiveById(tenantId, targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Group", targetId.toString())).getFarmId();
            case AgCostAllocation.ANIMAL -> animalRepository.findActiveById(tenantId, targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Animal", targetId.toString())).getFarmId();
            case AgCostAllocation.ENTERPRISE -> enterpriseRepository.findActiveById(tenantId, targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Enterprise", targetId.toString())).getFarmId();
            default -> throw new IllegalArgumentException("targetType must be one of " + AgCostAllocation.TARGET_TYPES);
        };
        if (!farmId.equals(owner)) {
            throw new IllegalArgumentException(targetType.toLowerCase().replace('_', ' ') + " " + targetId + " does not belong to this farm");
        }
    }
}
