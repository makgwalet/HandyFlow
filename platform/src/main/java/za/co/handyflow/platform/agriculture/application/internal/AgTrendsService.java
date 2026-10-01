package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.application.internal.AgTrendsAggregator.CropInfo;
import za.co.handyflow.platform.agriculture.application.internal.AgTrendsAggregator.DatedAmount;
import za.co.handyflow.platform.agriculture.application.internal.AgTrendsAggregator.EventRow;
import za.co.handyflow.platform.agriculture.application.internal.AgTrendsAggregator.HarvestRow;
import za.co.handyflow.platform.agriculture.application.internal.AgTrendsAggregator.Rows;
import za.co.handyflow.platform.agriculture.application.internal.AgTrendsAggregator.ScoutingRow;
import za.co.handyflow.platform.agriculture.domain.model.AgCropType;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgBreedingRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropTypeRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFeedRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHarvestRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHealthEventRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInputApplicationRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgMortalityRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgScoutingRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgStockMovementRepository;
import za.co.handyflow.platform.agriculture.dto.AgTrendsResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Month-by-month trends and 30-day comparisons for one farm or all of them. Fetches dated rows in a single range, works out
 * which farm each belongs to, and hands them to {@link AgTrendsAggregator} (pure, unit tested).
 * <p>
 * Records reach their farm indirectly: feed, health, mortality and births through an animal or group; inputs, harvests,
 * scouting and seed cost through a crop cycle. A record whose animal, group or cycle has been deleted is not attributed and is
 * left out, the same as the cost reports, which only walk live animals and cycles, so the totals agree.
 */
@Service
@RequiredArgsConstructor
public class AgTrendsService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final int LOOKUP_LIMIT = 1000;

    private final AgFarmRepository farmRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgCropTypeRepository cropTypeRepository;
    private final AgFeedRecordRepository feedRecordRepository;
    private final AgHealthEventRepository healthEventRepository;
    private final AgInputApplicationRepository inputApplicationRepository;
    private final AgStockMovementRepository stockMovementRepository;
    private final AgHarvestRecordRepository harvestRecordRepository;
    private final AgMortalityRecordRepository mortalityRecordRepository;
    private final AgBreedingRecordRepository breedingRecordRepository;
    private final AgScoutingRecordRepository scoutingRecordRepository;

    /** @param farmId a single farm, or null for every farm */
    @Transactional(readOnly = true)
    public AgTrendsResponse getTrends(TenantId tenantId, UUID farmId, int months) {
        if (months < AgTrendsAggregator.MIN_MONTHS || months > AgTrendsAggregator.MAX_MONTHS) {
            throw new IllegalArgumentException("months must be between " + AgTrendsAggregator.MIN_MONTHS + " and " + AgTrendsAggregator.MAX_MONTHS);
        }
        if (farmId != null) {
            farmRepository.findActiveById(tenantId, farmId).orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
        }
        LocalDate today = LocalDate.now(SAST);
        LocalDate from = AgTrendsAggregator.earliestNeeded(today, months);

        Map<UUID, UUID> animalFarm = lookup(animalRepository.findAnimalFarms(tenantId));
        Map<UUID, UUID> groupFarm = lookup(groupRepository.findGroupFarms(tenantId));
        Map<UUID, UUID> cycleFarm = new HashMap<>();
        Map<UUID, UUID> cycleCrop = new HashMap<>();
        for (Object[] r : cropCycleRepository.findCycleFarmsAndCrops(tenantId)) {
            cycleFarm.put((UUID) r[0], (UUID) r[1]);
            cycleCrop.put((UUID) r[0], (UUID) r[2]);
        }

        List<DatedAmount> feed = new ArrayList<>();
        for (Object[] r : feedRecordRepository.findCostsBetween(tenantId, from, today)) {
            addAmount(feed, r, targetFarm(animalFarm, groupFarm, (UUID) r[2], (UUID) r[3]));
        }
        List<DatedAmount> health = new ArrayList<>();
        for (Object[] r : healthEventRepository.findCostsBetween(tenantId, from, today)) {
            addAmount(health, r, targetFarm(animalFarm, groupFarm, (UUID) r[2], (UUID) r[3]));
        }
        List<DatedAmount> inputs = new ArrayList<>();
        for (Object[] r : inputApplicationRepository.findCostsBetween(tenantId, from, today)) {
            addAmount(inputs, r, cycleFarm.get((UUID) r[2]));
        }
        List<DatedAmount> seed = new ArrayList<>();
        for (Object[] r : stockMovementRepository.findCropCycleCostsBetween(tenantId, from, today)) {
            addAmount(seed, r, cycleFarm.get((UUID) r[2]));
        }
        List<DatedAmount> purchases = new ArrayList<>();
        for (Object[] r : animalRepository.findPurchasesBetween(tenantId, from, today)) {
            addAmount(purchases, r, (UUID) r[2]);
        }

        List<HarvestRow> harvests = new ArrayList<>();
        for (Object[] r : harvestRecordRepository.findBetween(tenantId, from, today)) {
            UUID cycle = (UUID) r[3];
            UUID farm = cycleFarm.get(cycle);
            if (farm == null) continue;
            harvests.add(new HarvestRow((LocalDate) r[0], farm, cycleCrop.get(cycle), asBigDecimal(r[1]), (String) r[2]));
        }
        List<EventRow> births = new ArrayList<>();
        for (Object[] r : breedingRecordRepository.findBirthsBetween(tenantId, from, today)) {
            UUID farm = targetFarm(animalFarm, groupFarm, (UUID) r[2], (UUID) r[3]);
            // a birth recorded without a head count is still a birth
            if (farm != null) births.add(new EventRow((LocalDate) r[0], farm, r[1] == null ? 1L : ((Number) r[1]).longValue(), null));
        }
        List<EventRow> deaths = new ArrayList<>();
        for (Object[] r : mortalityRecordRepository.findBetween(tenantId, from, today)) {
            UUID farm = targetFarm(animalFarm, groupFarm, (UUID) r[3], (UUID) r[4]);
            if (farm != null) deaths.add(new EventRow((LocalDate) r[0], farm, ((Number) r[1]).longValue(), r[2] == null ? null : asBigDecimal(r[2])));
        }
        List<ScoutingRow> scouting = new ArrayList<>();
        for (Object[] r : scoutingRecordRepository.findBetween(tenantId, from, today)) {
            UUID farm = cycleFarm.get((UUID) r[2]);
            if (farm != null) scouting.add(new ScoutingRow((LocalDate) r[0], farm, (String) r[1]));
        }

        Map<UUID, CropInfo> crops = new HashMap<>();
        for (AgCropType t : cropTypeRepository.findAllActive(tenantId, Pageable.ofSize(LOOKUP_LIMIT)).getContent()) {
            crops.put(t.getId(), new CropInfo(t.getName(), t.getDefaultUnitOfMeasure()));
        }

        return AgTrendsAggregator.build(today, months, farmId,
                new Rows(seed, inputs, feed, health, purchases, harvests, births, deaths, scouting), crops);
    }

    private static Map<UUID, UUID> lookup(List<Object[]> rows) {
        Map<UUID, UUID> m = new HashMap<>();
        for (Object[] r : rows) m.put((UUID) r[0], (UUID) r[1]);
        return m;
    }

    /** The farm of an animal or group record (exactly one of the two is set), or null when that animal or group no longer exists. */
    private static UUID targetFarm(Map<UUID, UUID> animalFarm, Map<UUID, UUID> groupFarm, UUID animalId, UUID groupId) {
        return animalId != null ? animalFarm.get(animalId) : groupId != null ? groupFarm.get(groupId) : null;
    }

    /** Rows are [date, amount, ...]; skipped when the farm is unknown. */
    private static void addAmount(List<DatedAmount> out, Object[] row, UUID farm) {
        if (farm != null) out.add(new DatedAmount((LocalDate) row[0], farm, asBigDecimal(row[1])));
    }

    private static BigDecimal asBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return o instanceof BigDecimal bd ? bd : new BigDecimal(o.toString());
    }
}
