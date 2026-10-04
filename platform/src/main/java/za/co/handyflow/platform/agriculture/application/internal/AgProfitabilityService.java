package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityAggregator.LedgerRow;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityAggregator.RevenueRow;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityAggregator.UnitInput;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgEnterprise;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCostEntryRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgEnterpriseRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHarvestRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInputApplicationRepository;
import za.co.handyflow.platform.agriculture.dto.AnimalCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.CropCycleCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.GroupCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SalesTotalsResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.TargetRevenue;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The farm's gross-margin report (ADR-001, W5): loads each unit's recorded costs, the cost-ledger totals and the live revenue, and hands them to
 * {@link AgProfitabilityAggregator}, which does the arithmetic. Combining everything in this one place is what keeps it from being counted twice:
 * the existing reports count feed, health, seed, inputs and an animal's purchase price; the ledger holds only the newer categories (labour,
 * equipment, fuel, other direct); and each cost and each sale belongs to exactly one target.
 * <p>
 * Revenue is read live through {@link AgSalesAllocationService}, so a cancelled invoice or a later credit note is reflected, and it is never stored.
 * Callers need AGRICULTURE_FINANCE and INVOICE_READ (it shows revenue).
 */
@Service
@RequiredArgsConstructor
public class AgProfitabilityService {

    /** The units loaded per type, and the sales and labour lists, are capped; if a cap is reached the report says so. */
    private static final int UNIT_LIMIT = 1000;
    private static final int SALES_LIMIT = 1000;
    private static final int UNCOSTED_PEEK = 301;

    private final AgFarmRepository farmRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgGroupRepository groupRepository;
    private final AgAnimalRepository animalRepository;
    private final AgEnterpriseRepository enterpriseRepository;
    private final AgCostEntryRepository costEntryRepository;
    private final AgInputApplicationRepository inputApplicationRepository;
    private final AgHarvestRecordRepository harvestRecordRepository;
    private final AgCostReportingService costReportingService;
    private final AgSalesAllocationService salesService;

    @Transactional(readOnly = true)
    public ProfitabilityResponse farm(TenantId tenantId, UUID farmId) {
        farmRepository.findActiveById(tenantId, farmId).orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
        List<String> truncated = new ArrayList<>();

        List<AgCropCycle> cycles = cropCycleRepository.findAllActiveForFarm(tenantId, farmId, Pageable.ofSize(UNIT_LIMIT)).getContent();
        List<AgGroup> groups = groupRepository.findAllActiveForFarm(tenantId, farmId, Pageable.ofSize(UNIT_LIMIT)).getContent();
        List<AgAnimal> animals = animalRepository.findAllActiveForFarm(tenantId, farmId, Pageable.ofSize(UNIT_LIMIT)).getContent();
        List<AgEnterprise> enterprises = enterpriseRepository.findAllActiveForFarm(tenantId, farmId, Pageable.ofSize(UNIT_LIMIT)).getContent();
        if (cycles.size() >= UNIT_LIMIT) truncated.add("Only the first " + UNIT_LIMIT + " crop cycles are included.");
        if (groups.size() >= UNIT_LIMIT) truncated.add("Only the first " + UNIT_LIMIT + " groups are included.");
        if (animals.size() >= UNIT_LIMIT) truncated.add("Only the first " + UNIT_LIMIT + " animals are included.");

        // What the existing reports already count (feed, health, seed, inputs, an animal's purchase price). Labour hours are not money there.
        Map<UUID, BigDecimal> cycleCost = new HashMap<>(), groupCost = new HashMap<>();
        Map<UUID, AnimalCostSummaryResponse> animalCost = new HashMap<>();
        for (CropCycleCostSummaryResponse c : costReportingService.getFarmCropCycleCostSummaries(tenantId, farmId)) cycleCost.put(c.cropCycleId(), c.totalCost());
        for (GroupCostSummaryResponse g : costReportingService.getFarmGroupCostSummaries(tenantId, farmId)) groupCost.put(g.groupId(), g.totalCost());
        for (AnimalCostSummaryResponse a : costReportingService.getFarmAnimalCostSummaries(tenantId, farmId)) animalCost.put(a.animalId(), a);

        List<UnitInput> units = new ArrayList<>();
        for (AgCropCycle c : cycles) units.add(new UnitInput("CROP_CYCLE", c.getId(), c.getCycleName(), c.getStatus(), null, cycleCost.get(c.getId())));
        for (AgGroup g : groups) units.add(new UnitInput("GROUP", g.getId(), g.getBatchNumber(), g.getStatus(), g.getAcquisitionType(), groupCost.get(g.getId())));
        for (AgAnimal a : animals) {
            AnimalCostSummaryResponse s = animalCost.get(a.getId());
            BigDecimal recorded = s == null ? null : s.totalCost();
            // Breeding stock: the purchase price is capital, not a direct cost, so it comes out of the recorded cost (feed and health stay).
            if (a.isBreedingStock() && recorded != null && s.acquisitionCost() != null) recorded = recorded.subtract(s.acquisitionCost());
            units.add(new UnitInput("ANIMAL", a.getId(), a.getName() != null && !a.getName().isBlank() ? a.getTagNumber() + " (" + a.getName() + ")" : a.getTagNumber(),
                    a.getStatus(), a.getAcquisitionType(), recorded, a.isBreedingStock()));
        }
        for (AgEnterprise e : enterprises) units.add(new UnitInput("ENTERPRISE", e.getId(), e.getName(), e.getStatus(), null, BigDecimal.ZERO));

        List<LedgerRow> ledger = new ArrayList<>();
        for (Object[] row : costEntryRepository.sumByTargetAndCategoryForFarm(tenantId, farmId)) {
            ledger.add(new LedgerRow((String) row[0], (UUID) row[1], (String) row[2], (BigDecimal) row[3]));
        }

        SalesTotalsResponse sales = salesService.totals(tenantId, farmId, null, null);
        List<RevenueRow> revenue = new ArrayList<>();
        for (TargetRevenue t : sales.byTarget()) revenue.add(new RevenueRow(t.targetType(), t.targetId(), t.revenue()));
        if (sales.allocationCount() >= SALES_LIMIT) truncated.add("Revenue covers only the " + SALES_LIMIT + " most recent sale allocations on this farm; older ones aren't included.");

        int uncostedLabour = inputApplicationRepository.findUncostedLabourForFarm(tenantId, farmId, Pageable.ofSize(UNCOSTED_PEEK)).size()
                + harvestRecordRepository.findUncostedLabourForFarm(tenantId, farmId, Pageable.ofSize(UNCOSTED_PEEK)).size();

        return AgProfitabilityAggregator.build(farmId, units, ledger, revenue, uncostedLabour, sales.notCountedCount(), truncated);
    }
}
