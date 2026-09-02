package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgCropType;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.repository.*;
import za.co.handyflow.platform.agriculture.dto.AnimalCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.CropCycleCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.GroupCostSummaryResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Cost-per-animal / cost-per-group / cost-per-hectare — the reporting
 * service flagged as missing in both prior increments' own status reports.
 * A DIRECT PORT of {@code fleet.FleetCostService}'s pattern: join cost data
 * that was already being captured record-by-record into the one number
 * that actually answers "what does this cost to keep/grow" — the same
 * "small lift, high value" framing that service's own Javadoc uses.
 * <p>
 * SCOPE, matching {@code FleetCostService} exactly: all-time totals, not
 * date-ranged. A farm's cost-per-hectare is mostly useful as a comparison
 * (which cycle/animal/group is expensive to run) rather than a period
 * metric — if a season-scoped or annual breakdown turns out to matter
 * later, this is the file to extend, following the same pattern
 * {@code FleetLogbookService}'s date handling already establishes
 * elsewhere in the platform.
 * <p>
 * WHAT THIS DELIBERATELY DOES NOT DO:
 * <ul>
 *   <li><b>No revenue, no profitability, no P&amp;L.</b> This is cost only.
 *       "Farm P&amp;L" was explicitly scoped in the architecture plan as a
 *       reporting view joining THIS module's cost data with
 *       {@code invoicing}'s own revenue data — a view this module doesn't
 *       own and this service doesn't attempt to build. {@code invoicing}
 *       remains outside this module's {@code allowedDependencies},
 *       unchanged.</li>
 *   <li><b>No labor cost.</b> {@code AgInputApplication.laborHours} and
 *       {@code AgHarvestRecord.laborHours} are summed as hours, not
 *       converted to a currency figure — there is no stored labor rate
 *       anywhere in this module (no link to HR pay rates, no manual rate
 *       field on any entity), and inventing one would be exactly the kind
 *       of guessed business rule this engagement's own ground rules
 *       prohibit. If a labor rate becomes available, this is the file to
 *       extend.</li>
 *   <li><b>Group acquisition cost is NOT included in
 *       {@code GroupCostSummaryResponse.totalCost}.</b> Unlike
 *       {@code AgAnimal.acquisitionCost}, {@code AgGroup} has no
 *       acquisition-cost field — Increment 1 never captured what a batch
 *       cost to buy in, only how many head. Flagged here rather than
 *       silently under-reporting a "total" that isn't actually total;
 *       adding that field would be an Increment-1 schema change out of
 *       this task's own scope.</li>
 * </ul>
 * <p>
 * SEED COST: {@code AgCropCycle} has no {@code seedCost} field of its own
 * (only {@code seedQuantity}) — seed cost is recovered from the ISSUE
 * {@code AgStockMovement} {@code AgCropCycleService.issueSeed()} creates
 * against the seed {@code AgInventoryItem}, via
 * {@code AgStockMovementRepository.sumTotalCostByReference()}. That
 * movement previously stored a null unit cost (a real gap, fixed alongside
 * this service — see {@code AgCropCycleService.issueSeed()}'s own updated
 * Javadoc) — without that fix, seed cost would have silently reported as
 * zero for every cycle regardless of what the seed actually cost.
 */
@Service
@RequiredArgsConstructor
public class AgCostReportingService {

    private final AgAnimalRepository animalRepository;
    private final AgHealthEventRepository healthEventRepository;
    private final AgFeedRecordRepository feedRecordRepository;
    private final AgGroupRepository groupRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgCropTypeRepository cropTypeRepository;
    private final AgInputApplicationRepository inputApplicationRepository;
    private final AgStockMovementRepository stockMovementRepository;
    private final AgHarvestRecordRepository harvestRecordRepository;

    // ── Animals ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AnimalCostSummaryResponse getAnimalCostSummary(TenantId tenantId, UUID animalId) {
        AgAnimal animal = animalRepository.findActiveById(tenantId, animalId)
                .orElseThrow(() -> new ResourceNotFoundException("Animal", animalId.toString()));
        return summarizeAnimal(tenantId, animal);
    }

    @Transactional(readOnly = true)
    public List<AnimalCostSummaryResponse> getFarmAnimalCostSummaries(TenantId tenantId, UUID farmId) {
        // size=1000 is a pragmatic dashboard ceiling, not a real pagination
        // story — mirrors FleetCostService.getFleetCostSummary() exactly.
        List<AgAnimal> animals = animalRepository
                .findAllActiveForFarm(tenantId, farmId, Pageable.ofSize(1000))
                .getContent();
        return animals.stream()
                .map(a -> summarizeAnimal(tenantId, a))
                .sorted(Comparator.comparing(
                        AnimalCostSummaryResponse::totalCost,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private AnimalCostSummaryResponse summarizeAnimal(TenantId tenantId, AgAnimal animal) {
        BigDecimal healthCost = healthEventRepository.sumCostByAnimal(tenantId, animal.getId());
        BigDecimal feedCost = feedRecordRepository.sumTotalCostByAnimal(tenantId, animal.getId());
        BigDecimal acquisitionCost = animal.getAcquisitionCost() != null ? animal.getAcquisitionCost() : BigDecimal.ZERO;
        BigDecimal totalCost = acquisitionCost.add(healthCost).add(feedCost);

        BigDecimal weight = animal.getCurrentWeightKg();
        BigDecimal costPerKg = (weight != null && weight.signum() > 0)
                ? totalCost.divide(weight, 2, RoundingMode.HALF_UP)
                : null; // null, not zero — "no weight recorded yet" is a different fact from "free to keep"

        return new AnimalCostSummaryResponse(
                animal.getId(), animal.getTagNumber(), animal.getFarmId(),
                acquisitionCost, healthCost, feedCost, totalCost, weight, costPerKg
        );
    }

    // ── Groups ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GroupCostSummaryResponse getGroupCostSummary(TenantId tenantId, UUID groupId) {
        AgGroup group = groupRepository.findActiveById(tenantId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId.toString()));
        return summarizeGroup(tenantId, group);
    }

    @Transactional(readOnly = true)
    public List<GroupCostSummaryResponse> getFarmGroupCostSummaries(TenantId tenantId, UUID farmId) {
        List<AgGroup> groups = groupRepository
                .findAllActiveForFarm(tenantId, farmId, Pageable.ofSize(1000))
                .getContent();
        return groups.stream()
                .map(g -> summarizeGroup(tenantId, g))
                .sorted(Comparator.comparing(
                        GroupCostSummaryResponse::totalCost,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private GroupCostSummaryResponse summarizeGroup(TenantId tenantId, AgGroup group) {
        BigDecimal healthCost = healthEventRepository.sumCostByGroup(tenantId, group.getId());
        BigDecimal feedCost = feedRecordRepository.sumTotalCostByGroup(tenantId, group.getId());
        BigDecimal totalCost = healthCost.add(feedCost); // ongoing costs only — see class Javadoc

        int currentCount = group.getCurrentCount();
        BigDecimal costPerHead = currentCount > 0
                ? totalCost.divide(BigDecimal.valueOf(currentCount), 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal avgWeight = group.getAverageWeightKg();
        BigDecimal costPerKg = (avgWeight != null && avgWeight.signum() > 0 && currentCount > 0)
                ? totalCost.divide(avgWeight.multiply(BigDecimal.valueOf(currentCount)), 2, RoundingMode.HALF_UP)
                : null;

        return new GroupCostSummaryResponse(
                group.getId(), group.getBatchNumber(), group.getFarmId(),
                healthCost, feedCost, totalCost, currentCount, costPerHead, avgWeight, costPerKg
        );
    }

    // ── Crop cycles ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CropCycleCostSummaryResponse getCropCycleCostSummary(TenantId tenantId, UUID cropCycleId) {
        AgCropCycle cycle = cropCycleRepository.findActiveById(tenantId, cropCycleId)
                .orElseThrow(() -> new ResourceNotFoundException("CropCycle", cropCycleId.toString()));
        return summarizeCropCycle(tenantId, cycle);
    }

    @Transactional(readOnly = true)
    public List<CropCycleCostSummaryResponse> getFarmCropCycleCostSummaries(TenantId tenantId, UUID farmId) {
        List<AgCropCycle> cycles = cropCycleRepository
                .findAllActiveForFarm(tenantId, farmId, Pageable.ofSize(1000))
                .getContent();
        return cycles.stream()
                .map(c -> summarizeCropCycle(tenantId, c))
                .sorted(Comparator.comparing(
                        CropCycleCostSummaryResponse::costPerHectare,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private CropCycleCostSummaryResponse summarizeCropCycle(TenantId tenantId, AgCropCycle cycle) {
        BigDecimal seedCost = stockMovementRepository.sumTotalCostByReference(tenantId, "AgCropCycle", cycle.getId());
        BigDecimal inputCost = inputApplicationRepository.sumCostByCropCycle(tenantId, cycle.getId());
        BigDecimal totalCost = seedCost.add(inputCost); // excludes unconverted labor hours — see class Javadoc

        BigDecimal hectares = cycle.getAreaPlantedHectares();
        BigDecimal costPerHectare = (hectares != null && hectares.signum() > 0)
                ? totalCost.divide(hectares, 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal laborHours = inputApplicationRepository.sumLaborHoursByCropCycle(tenantId, cycle.getId());
        BigDecimal yield = harvestRecordRepository.sumQuantityByCropCycle(tenantId, cycle.getId());
        BigDecimal yieldPerHectare = (hectares != null && hectares.signum() > 0)
                ? yield.divide(hectares, 3, RoundingMode.HALF_UP)
                : null;

        String yieldUnit = cropTypeRepository.findActiveById(tenantId, cycle.getCropTypeId())
                .map(AgCropType::getDefaultUnitOfMeasure)
                .orElse(null);

        return new CropCycleCostSummaryResponse(
                cycle.getId(), cycle.getCycleName(), cycle.getFarmId(), cycle.getCropTypeId(), hectares,
                seedCost, inputCost, totalCost, costPerHectare, laborHours, yield, yieldUnit, yieldPerHectare
        );
    }
}
