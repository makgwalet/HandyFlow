package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.application.internal.AgDashboardAggregator.AnimalRow;
import za.co.handyflow.platform.agriculture.application.internal.AgDashboardAggregator.CropAreaRow;
import za.co.handyflow.platform.agriculture.application.internal.AgDashboardAggregator.CycleStatusRow;
import za.co.handyflow.platform.agriculture.application.internal.AgDashboardAggregator.FarmRow;
import za.co.handyflow.platform.agriculture.application.internal.AgDashboardAggregator.GroupRow;
import za.co.handyflow.platform.agriculture.application.internal.AgDashboardAggregator.SpeciesInfo;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgCropType;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.model.AgHealthEvent;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.agriculture.domain.model.AgScoutingRecord;
import za.co.handyflow.platform.agriculture.domain.model.AgSpecies;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropTypeRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHealthEventRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgScoutingRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgSpeciesRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgAttentionRules;
import za.co.handyflow.platform.agriculture.dto.AgDashboardResponse;
import za.co.handyflow.platform.agriculture.dto.AttentionItemResponse;
import za.co.handyflow.platform.agriculture.dto.FarmTodaySummaryResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * FIX (Agriculture GAP 4 & GAP 5 — mobile gap report): a dedicated service rather than adding more repository
 * dependencies to AgFarmService. The per-farm "Home/Today" summary embeds the same attention list the dedicated
 * "attention" endpoint returns on its own.
 * <p>
 * It also serves the tenant-wide dashboard ({@link #getTenantDashboard}): every ACTIVE farm in one call, built from a
 * handful of grouped queries plus one attention pass per farm, instead of the client calling a per-farm endpoint once
 * per farm. The arithmetic itself lives in {@link AgDashboardAggregator} (pure, unit tested); this class only fetches rows.
 */
@Service
@RequiredArgsConstructor
public class AgDashboardService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    /** Farms per tenant are few; this is a safety cap, not a page size. */
    private static final int MAX_FARMS = 500;
    private static final int DASHBOARD_ATTENTION_LIMIT = 20;

    private final AgFarmRepository farmRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgHealthEventRepository healthEventRepository;
    private final AgScoutingRecordRepository scoutingRecordRepository;
    private final AgInventoryItemRepository inventoryItemRepository;
    private final AgSpeciesRepository speciesRepository;
    private final AgCropTypeRepository cropTypeRepository;

    @Transactional(readOnly = true)
    public FarmTodaySummaryResponse getTodaySummary(TenantId tenantId, UUID farmId) {
        AgFarm farm = requireFarm(tenantId, farmId);
        long animalCount = animalRepository.countActiveForFarm(tenantId, farmId);
        long groupCount = groupRepository.countActiveForFarm(tenantId, farmId);
        long cropCycleCount = cropCycleRepository.countActiveForFarm(tenantId, farmId);
        List<AttentionItemResponse> attention = buildAttentionList(tenantId, farm, today());
        return new FarmTodaySummaryResponse(farmId, animalCount, groupCount, cropCycleCount, attention);
    }

    @Transactional(readOnly = true)
    public List<AttentionItemResponse> getAttention(TenantId tenantId, UUID farmId) {
        return buildAttentionList(tenantId, requireFarm(tenantId, farmId), today());
    }

    /** Every ACTIVE farm: totals, farm types and locations, crops in production, livestock by species and the ranked attention list. */
    @Transactional(readOnly = true)
    public AgDashboardResponse getTenantDashboard(TenantId tenantId) {
        LocalDate today = today();
        List<AgFarm> farms = farmRepository.findByStatus(tenantId, "ACTIVE", Pageable.ofSize(MAX_FARMS)).getContent();

        List<FarmRow> farmRows = new ArrayList<>();
        List<AttentionItemResponse> attention = new ArrayList<>();
        for (AgFarm f : farms) {
            farmRows.add(new FarmRow(f.getId(), f.getName(), f.getFarmType(), f.getProvince(), f.getRegion(),
                    f.getGpsLatitude(), f.getGpsLongitude(), f.getTotalHectares()));
            attention.addAll(buildAttentionList(tenantId, f, today));
        }

        List<CycleStatusRow> cycleStatus = new ArrayList<>();
        for (Object[] r : cropCycleRepository.summarizeByFarmAndStatus(tenantId)) {
            cycleStatus.add(new CycleStatusRow((UUID) r[0], (String) r[1], asLong(r[2]), asBigDecimal(r[3])));
        }
        List<CropAreaRow> cropAreas = new ArrayList<>();
        for (Object[] r : cropCycleRepository.summarizeInProductionByFarmAndCrop(tenantId)) {
            cropAreas.add(new CropAreaRow((UUID) r[0], (UUID) r[1], asLong(r[2]), asBigDecimal(r[3])));
        }
        List<AnimalRow> animals = new ArrayList<>();
        for (Object[] r : animalRepository.countActiveByFarmAndSpecies(tenantId)) {
            animals.add(new AnimalRow((UUID) r[0], (UUID) r[1], asLong(r[2])));
        }
        List<GroupRow> groups = new ArrayList<>();
        for (Object[] r : groupRepository.summarizeActiveByFarmAndSpecies(tenantId)) {
            groups.add(new GroupRow((UUID) r[0], (UUID) r[1], asLong(r[2]), asLong(r[3])));
        }

        Map<UUID, String> cropNames = new HashMap<>();
        for (AgCropType t : cropTypeRepository.findAllActive(tenantId, Pageable.ofSize(MAX_FARMS)).getContent()) {
            cropNames.put(t.getId(), t.getName());
        }
        Map<UUID, SpeciesInfo> species = new HashMap<>();
        for (AgSpecies s : speciesRepository.findAllActive(tenantId, Pageable.ofSize(MAX_FARMS)).getContent()) {
            species.put(s.getId(), new SpeciesInfo(s.getName(), s.getCategory()));
        }

        return AgDashboardAggregator.build(today, farmRows, cycleStatus, cropAreas, cropNames, animals, groups, species,
                attention, DASHBOARD_ATTENTION_LIMIT);
    }

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    private AgFarm requireFarm(TenantId tenantId, UUID farmId) {
        return farmRepository.findActiveById(tenantId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
    }

    /**
     * Everything that needs a person's attention on one farm, most urgent first. Date-driven sources are queried up to the
     * look-ahead horizon and then classified, so a vaccination due next week appears as UPCOMING instead of not at all.
     * See {@link AgAttentionRules} for the severity ladder.
     */
    private List<AttentionItemResponse> buildAttentionList(TenantId tenantId, AgFarm farm, LocalDate today) {
        UUID farmId = farm.getId();
        String farmName = farm.getName();
        LocalDate horizon = AgAttentionRules.horizon(today);
        List<AttentionItemResponse> items = new ArrayList<>();

        for (AgHealthEvent e : healthEventRepository.findDueForFarm(tenantId, farmId, horizon)) {
            items.add(new AttentionItemResponse(
                    "HEALTH_EVENT_DUE",
                    AgAttentionRules.dueSeverity(e.getNextDueDate(), today),
                    e.getEventType() + " due",
                    e.getDescription(),
                    e.getNextDueDate(),
                    e.getId(), farmId, farmName));
        }

        // An open HIGH-severity finding is critical whether or not a follow-up was scheduled. When it also has a follow-up
        // that is due, it is reported once (here, with the follow-up date) rather than twice.
        Set<UUID> reportedScouting = new HashSet<>();
        for (AgScoutingRecord r : scoutingRecordRepository.findOpenHighSeverityForFarm(tenantId, farmId)) {
            reportedScouting.add(r.getId());
            items.add(new AttentionItemResponse(
                    "SCOUTING_HIGH_SEVERITY",
                    AgAttentionRules.CRITICAL,
                    "High-severity " + r.getObservationType().toLowerCase().replace('_', ' ') + " finding",
                    r.getRecommendedAction() != null ? r.getRecommendedAction() : r.getDescription(),
                    r.getFollowUpDate(),
                    r.getId(), farmId, farmName));
        }
        for (AgScoutingRecord r : scoutingRecordRepository.findFollowUpDueForFarm(tenantId, farmId, horizon)) {
            if (reportedScouting.contains(r.getId())) continue;
            items.add(new AttentionItemResponse(
                    "SCOUTING_FOLLOWUP_DUE",
                    AgAttentionRules.dueSeverity(r.getFollowUpDate(), today),
                    "Scouting follow-up due",
                    r.getRecommendedAction() != null ? r.getRecommendedAction() : r.getDescription(),
                    r.getFollowUpDate(),
                    r.getId(), farmId, farmName));
        }

        for (AgCropCycle c : cropCycleRepository.findHarvestDueForFarm(tenantId, farmId, horizon)) {
            items.add(new AttentionItemResponse(
                    "HARVEST_DUE",
                    AgAttentionRules.dueSeverity(c.getExpectedHarvestDate(), today),
                    "Harvest due",
                    c.getCycleName() != null && !c.getCycleName().isBlank() ? c.getCycleName()
                            : c.getVariety() != null && !c.getVariety().isBlank() ? c.getVariety() : "Crop cycle",
                    c.getExpectedHarvestDate(),
                    c.getId(), farmId, farmName));
        }

        for (AgInventoryItem i : inventoryItemRepository.findBelowReorderLevelForFarm(tenantId, farmId)) {
            boolean outOfStock = i.getCurrentQuantity() == null || i.getCurrentQuantity().signum() <= 0;
            items.add(new AttentionItemResponse(
                    "LOW_STOCK",
                    AgAttentionRules.stockSeverity(i.getCurrentQuantity()),
                    i.getItemName() + (outOfStock ? " is out of stock" : " is low"),
                    String.format("%s %s remaining (reorder level: %s %s)",
                            i.getCurrentQuantity(), i.getUnitOfMeasure(), i.getReorderLevel(), i.getUnitOfMeasure()),
                    null,
                    i.getId(), farmId, farmName));
        }

        return AgAttentionRules.ranked(items);
    }

    // Aggregate columns come back as Long / BigDecimal depending on the function and column type; Number covers both.
    private static long asLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static BigDecimal asBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return o instanceof BigDecimal bd ? bd : new BigDecimal(o.toString());
    }
}
