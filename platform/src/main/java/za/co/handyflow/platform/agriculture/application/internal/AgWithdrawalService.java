package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.model.AgHealthEvent;
import za.co.handyflow.platform.agriculture.domain.repository.AgHealthEventRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInputApplicationRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgReminderRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Hold;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Produce;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Treatment;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Finds what is holding an animal, group or crop and refuses (or, with a written reason, lets through and notes) a sale,
 * slaughter or harvest while something is. The rules themselves are in AgWithdrawalRules.
 */
@Service
@RequiredArgsConstructor
public class AgWithdrawalService {

    private final AgHealthEventRepository healthEventRepository;
    private final AgInputApplicationRepository inputApplicationRepository;

    @Transactional(readOnly = true)
    public Optional<Hold> animalHold(TenantId tenantId, UUID animalId, LocalDate asOf) {
        List<Treatment> treatments = healthEventRepository
                .findWithdrawalTreatmentsForAnimal(tenantId, animalId, since(asOf), asOf).stream()
                .map(e -> new Treatment(e.getEventDate(), e.getWithdrawalPeriodDays(), e.getProductUsed(), "SCHEDULED".equals(e.getStatus())))
                .toList();
        return AgWithdrawalRules.activeHold(treatments, asOf);
    }

    @Transactional(readOnly = true)
    public Optional<Hold> groupHold(TenantId tenantId, UUID groupId, LocalDate asOf) {
        List<Treatment> treatments = healthEventRepository
                .findWithdrawalTreatmentsForGroup(tenantId, groupId, since(asOf), asOf).stream()
                .map(e -> new Treatment(e.getEventDate(), e.getWithdrawalPeriodDays(), e.getProductUsed(), "SCHEDULED".equals(e.getStatus())))
                .toList();
        return AgWithdrawalRules.activeHold(treatments, asOf);
    }

    /** Is milk or egg from this animal held on {@code asOf}? Informational: nothing in the module records milk or egg sales yet. */
    @Transactional(readOnly = true)
    public Optional<Hold> animalProduceHold(TenantId tenantId, UUID animalId, LocalDate asOf, Produce produce) {
        return AgWithdrawalRules.activeHold(produceTreatments(
                healthEventRepository.findProduceTreatmentsForAnimal(tenantId, animalId, since(asOf), asOf), produce), asOf);
    }

    @Transactional(readOnly = true)
    public Optional<Hold> groupProduceHold(TenantId tenantId, UUID groupId, LocalDate asOf, Produce produce) {
        return AgWithdrawalRules.activeHold(produceTreatments(
                healthEventRepository.findProduceTreatmentsForGroup(tenantId, groupId, since(asOf), asOf), produce), asOf);
    }

    private static List<Treatment> produceTreatments(List<AgHealthEvent> events, Produce produce) {
        return events.stream()
                .map(e -> new Treatment(e.getEventDate(), AgWithdrawalRules.produceDays(produce, e.getMilkWithdrawalDays(), e.getEggWithdrawalDays()),
                        e.getProductUsed(), "SCHEDULED".equals(e.getStatus())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Hold> cropHold(TenantId tenantId, UUID cropCycleId, LocalDate harvestDate) {
        List<Treatment> sprays = inputApplicationRepository
                .findWithPreHarvestInterval(tenantId, cropCycleId, since(harvestDate), harvestDate).stream()
                .map(a -> new Treatment(a.getApplicationDate(), a.getPreHarvestIntervalDays(), a.getProductUsed(), false))
                .toList();
        return AgWithdrawalRules.activeHold(sprays, harvestDate);
    }

    /** Before selling, slaughtering or transferring out an animal or group. Returns the line to keep when a hold was overridden, else null. */
    @Transactional(readOnly = true)
    public String requireAnimalOrGroupClear(TenantId tenantId, AgAnimal animal, AgGroup group, LocalDate asOf, String overrideReason) {
        if (animal != null) {
            return AgWithdrawalRules.requireClear(animalHold(tenantId, animal.getId(), asOf),
                    AgReminderRules.animalLabel(animal.getTagNumber(), animal.getName()),
                    "withdrawal period", "sold or slaughtered", overrideReason);
        }
        return AgWithdrawalRules.requireClear(groupHold(tenantId, group.getId(), asOf),
                AgReminderRules.groupLabel(group.getBatchNumber()),
                "withdrawal period", "sold or slaughtered", overrideReason);
    }

    /** Like {@link #requireAnimalOrGroupClear} but returns the override as data, to store in its own fields. Empty when nothing was held. */
    @Transactional(readOnly = true)
    public Optional<AgWithdrawalRules.Override> requireAnimalOrGroupClearOrOverride(TenantId tenantId, AgAnimal animal, AgGroup group, LocalDate asOf, String overrideReason) {
        if (animal != null) {
            return AgWithdrawalRules.requireClearOrOverride(animalHold(tenantId, animal.getId(), asOf),
                    AgReminderRules.animalLabel(animal.getTagNumber(), animal.getName()),
                    "withdrawal period", "sold or slaughtered", overrideReason);
        }
        return AgWithdrawalRules.requireClearOrOverride(groupHold(tenantId, group.getId(), asOf),
                AgReminderRules.groupLabel(group.getBatchNumber()),
                "withdrawal period", "sold or slaughtered", overrideReason);
    }

    /** Like {@link #requireCropClear} but returns the override as data. Empty when nothing was held. */
    @Transactional(readOnly = true)
    public Optional<AgWithdrawalRules.Override> requireCropClearOrOverride(TenantId tenantId, UUID cropCycleId, LocalDate harvestDate, String overrideReason) {
        return AgWithdrawalRules.requireClearOrOverride(cropHold(tenantId, cropCycleId, harvestDate),
                "This crop", "pre-harvest interval", "harvested", overrideReason);
    }

    /** Before a harvest is recorded. Returns the line to keep when a hold was overridden, else null. */
    @Transactional(readOnly = true)
    public String requireCropClear(TenantId tenantId, UUID cropCycleId, LocalDate harvestDate, String overrideReason) {
        return AgWithdrawalRules.requireClear(cropHold(tenantId, cropCycleId, harvestDate),
                "This crop", "pre-harvest interval", "harvested", overrideReason);
    }

    private static LocalDate since(LocalDate asOf) { return asOf.minusDays(AgWithdrawalRules.MAX_DAYS); }
}
