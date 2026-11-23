package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgReminderRules;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the morning reminders need to read well and reach the right person: a tag number or batch number instead of a database id,
 * the farm's name, and the farm manager (falling back to the tenant admins when the farm has no manager who is a platform user).
 */
@Component
@RequiredArgsConstructor
class AgReminderSupport {

    /** Where a reminder is about: the farm it is on and a readable name for the thing. */
    record Target(UUID farmId, String farmName, String label) {}

    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgFarmRepository farmRepository;
    private final HrFacade hrFacade;
    private final TenantAdminRecipients tenantAdminRecipients;

    /** Empty when the animal or group, or its farm, is gone: there is nobody to remind about it. */
    Optional<Target> forAnimalOrGroup(TenantId tenantId, UUID animalId, UUID groupId) {
        if (animalId != null) {
            return animalRepository.findActiveById(tenantId, animalId).flatMap(a -> withFarm(tenantId, a.getFarmId(), AgReminderRules.animalLabel(a.getTagNumber(), a.getName())));
        }
        if (groupId != null) {
            return groupRepository.findActiveById(tenantId, groupId).flatMap(g -> withFarm(tenantId, g.getFarmId(), AgReminderRules.groupLabel(g.getBatchNumber())));
        }
        return Optional.empty();
    }

    Optional<Target> forCropCycle(TenantId tenantId, UUID cropCycleId) {
        return cropCycleRepository.findActiveById(tenantId, cropCycleId).flatMap(c -> withFarm(tenantId, c.getFarmId(), cycleLabel(c)));
    }

    Optional<Target> forFarm(TenantId tenantId, UUID farmId, String label) {
        return withFarm(tenantId, farmId, label);
    }

    /** The farm manager if they are a platform user who can be reached, otherwise the tenant admins. Never both. */
    List<Recipient> recipientsFor(TenantId tenantId, UUID farmId) {
        List<Recipient> manager = farmRepository.findActiveById(tenantId, farmId)
                .map(AgFarm::getManagerId)
                .flatMap(id -> hrFacade.findEmployeeById(tenantId, id))
                .map(EmployeeSummary::userId)
                .map(userId -> tenantAdminRecipients.resolveUsers(tenantId, List.of(userId)))
                .orElse(List.of());
        return AgReminderRules.managerOrFallback(manager, tenantAdminRecipients.resolveTenantAdmins(tenantId));
    }

    private Optional<Target> withFarm(TenantId tenantId, UUID farmId, String label) {
        return farmRepository.findActiveById(tenantId, farmId)
                .filter(f -> !"INACTIVE".equals(f.getStatus()))
                .map(f -> new Target(f.getId(), f.getName(), label));
    }

    private static String cycleLabel(AgCropCycle c) {
        return "crop cycle " + (c.getCycleName() == null || c.getCycleName().isBlank() ? "(unnamed)" : c.getCycleName().trim());
    }
}
