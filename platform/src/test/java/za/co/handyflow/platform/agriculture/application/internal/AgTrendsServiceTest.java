package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.agriculture.domain.model.AgCropType;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.repository.*;
import za.co.handyflow.platform.agriculture.dto.AgTrendsResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Attribution is the service's job: each dated row belongs to a farm through its animal, group or crop cycle. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgTrendsServiceTest {

    @Mock AgFarmRepository farmRepository;
    @Mock AgAnimalRepository animalRepository;
    @Mock AgGroupRepository groupRepository;
    @Mock AgCropCycleRepository cropCycleRepository;
    @Mock AgCropTypeRepository cropTypeRepository;
    @Mock AgFeedRecordRepository feedRecordRepository;
    @Mock AgHealthEventRepository healthEventRepository;
    @Mock AgInputApplicationRepository inputApplicationRepository;
    @Mock AgStockMovementRepository stockMovementRepository;
    @Mock AgHarvestRecordRepository harvestRecordRepository;
    @Mock AgMortalityRecordRepository mortalityRecordRepository;
    @Mock AgBreedingRecordRepository breedingRecordRepository;
    @Mock AgScoutingRecordRepository scoutingRecordRepository;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
    final UUID farmA = UUID.randomUUID();
    final UUID farmB = UUID.randomUUID();

    private AgTrendsService service() {
        return new AgTrendsService(farmRepository, animalRepository, groupRepository, cropCycleRepository, cropTypeRepository,
                feedRecordRepository, healthEventRepository, inputApplicationRepository, stockMovementRepository,
                harvestRecordRepository, mortalityRecordRepository, breedingRecordRepository, scoutingRecordRepository);
    }

    private static List<Object[]> rows(Object[]... r) { return List.of(r); }

    /** Every query returns nothing until a test stubs it. */
    private void emptyTenant() {
        when(animalRepository.findAnimalFarms(eq(TENANT))).thenReturn(List.of());
        when(groupRepository.findGroupFarms(eq(TENANT))).thenReturn(List.of());
        when(cropCycleRepository.findCycleFarmsAndCrops(eq(TENANT))).thenReturn(List.of());
        when(feedRecordRepository.findCostsBetween(eq(TENANT), any(), any())).thenReturn(List.of());
        when(healthEventRepository.findCostsBetween(eq(TENANT), any(), any())).thenReturn(List.of());
        when(inputApplicationRepository.findCostsBetween(eq(TENANT), any(), any())).thenReturn(List.of());
        when(stockMovementRepository.findCropCycleCostsBetween(eq(TENANT), any(), any())).thenReturn(List.of());
        when(animalRepository.findPurchasesBetween(eq(TENANT), any(), any())).thenReturn(List.of());
        when(harvestRecordRepository.findBetween(eq(TENANT), any(), any())).thenReturn(List.of());
        when(mortalityRecordRepository.findBetween(eq(TENANT), any(), any())).thenReturn(List.of());
        when(breedingRecordRepository.findBirthsBetween(eq(TENANT), any(), any())).thenReturn(List.of());
        when(scoutingRecordRepository.findBetween(eq(TENANT), any(), any())).thenReturn(List.of());
        when(cropTypeRepository.findAllActive(eq(TENANT), any())).thenReturn(new PageImpl<AgCropType>(List.of()));
    }

    private static void assertNumber(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("feed cost reaches its farm through the animal or the group; a record of a deleted animal is left out")
    void attributesCostsThroughAnimalAndGroup() {
        emptyTenant();
        UUID animal = UUID.randomUUID(), group = UUID.randomUUID(), deleted = UUID.randomUUID();
        when(animalRepository.findAnimalFarms(eq(TENANT))).thenReturn(rows(new Object[] {animal, farmA}));
        when(groupRepository.findGroupFarms(eq(TENANT))).thenReturn(rows(new Object[] {group, farmB}));
        when(feedRecordRepository.findCostsBetween(eq(TENANT), any(), any())).thenReturn(rows(
                new Object[] {TODAY, new BigDecimal("100"), animal, null},
                new Object[] {TODAY, new BigDecimal("50"), null, group},
                new Object[] {TODAY, new BigDecimal("999"), deleted, null}));

        AgTrendsResponse all = service().getTrends(TENANT, null, 1);
        AgTrendsResponse onlyA = service().getTrends(TENANT, null, 1);

        assertNumber("150", all.costs().get(0).feed());
        assertNumber("150", onlyA.costs().get(0).total());
    }

    @Test
    @DisplayName("a farm filter keeps only that farm's rows")
    void farmFilter() {
        emptyTenant();
        UUID animal = UUID.randomUUID(), group = UUID.randomUUID();
        when(farmRepository.findActiveById(eq(TENANT), eq(farmA))).thenReturn(Optional.of(mock(AgFarm.class)));
        when(animalRepository.findAnimalFarms(eq(TENANT))).thenReturn(rows(new Object[] {animal, farmA}));
        when(groupRepository.findGroupFarms(eq(TENANT))).thenReturn(rows(new Object[] {group, farmB}));
        when(healthEventRepository.findCostsBetween(eq(TENANT), any(), any())).thenReturn(rows(
                new Object[] {TODAY, new BigDecimal("30"), animal, null}, new Object[] {TODAY, new BigDecimal("70"), null, group}));

        AgTrendsResponse r = service().getTrends(TENANT, farmA, 1);

        assertEquals(farmA, r.farmId());
        assertNumber("30", r.costs().get(0).health());
    }

    @Test
    @DisplayName("an unknown farm is a 404, and months outside 1 to 24 are refused before any query runs")
    void validation() {
        emptyTenant();
        assertThrows(ResourceNotFoundException.class, () -> service().getTrends(TENANT, UUID.randomUUID(), 12));
        assertThrows(IllegalArgumentException.class, () -> service().getTrends(TENANT, null, 0));
        assertThrows(IllegalArgumentException.class, () -> service().getTrends(TENANT, null, 25));
        verifyNoInteractions(feedRecordRepository);
    }

    @Test
    @DisplayName("harvests are attributed through the crop cycle and converted to tonnes and to the crop's unit")
    void harvestsThroughCropCycle() {
        emptyTenant();
        UUID cycle = UUID.randomUUID(), crop = UUID.randomUUID(), orphan = UUID.randomUUID();
        AgCropType maize = mock(AgCropType.class);
        when(maize.getId()).thenReturn(crop);
        when(maize.getName()).thenReturn("Maize");
        when(maize.getDefaultUnitOfMeasure()).thenReturn("t");
        when(cropTypeRepository.findAllActive(eq(TENANT), any())).thenReturn(new PageImpl<AgCropType>(List.of(maize)));
        when(cropCycleRepository.findCycleFarmsAndCrops(eq(TENANT))).thenReturn(rows(new Object[] {cycle, farmA, crop}));
        when(harvestRecordRepository.findBetween(eq(TENANT), any(), any())).thenReturn(rows(
                new Object[] {TODAY, new BigDecimal("800"), "kg", cycle}, new Object[] {TODAY, new BigDecimal("5"), "t", cycle},
                new Object[] {TODAY, new BigDecimal("77"), "t", orphan}));            // its cycle was deleted

        AgTrendsResponse r = service().getTrends(TENANT, null, 1);

        assertNumber("5.8", r.production().tonnes().get(0).tonnes());
        assertEquals("Maize", r.production().byCrop().get(0).cropName());
        assertEquals("t", r.production().byCrop().get(0).unit());
        assertNumber("5.8", r.production().byCrop().get(0).total());
    }

    @Test
    @DisplayName("inputs and seed cost reach their farm through the crop cycle")
    void cropCostsThroughCycle() {
        emptyTenant();
        UUID cycle = UUID.randomUUID();
        when(cropCycleRepository.findCycleFarmsAndCrops(eq(TENANT))).thenReturn(rows(new Object[] {cycle, farmA, UUID.randomUUID()}));
        when(inputApplicationRepository.findCostsBetween(eq(TENANT), any(), any())).thenReturn(rows(new Object[] {TODAY, new BigDecimal("250"), cycle}));
        when(stockMovementRepository.findCropCycleCostsBetween(eq(TENANT), any(), any())).thenReturn(rows(new Object[] {TODAY, new BigDecimal("40"), cycle}));
        when(animalRepository.findPurchasesBetween(eq(TENANT), any(), any())).thenReturn(rows(new Object[] {TODAY, new BigDecimal("1000"), farmB}));

        AgTrendsResponse r = service().getTrends(TENANT, null, 1);

        assertNumber("250", r.costs().get(0).inputs());
        assertNumber("40", r.costs().get(0).seed());
        assertNumber("1000", r.costs().get(0).animalPurchases());
        assertNumber("1290", r.costs().get(0).total());
    }

    @Test
    @DisplayName("a birth with no head count still counts as one; deaths carry their count and recorded loss")
    void livestockEvents() {
        emptyTenant();
        UUID animal = UUID.randomUUID(), group = UUID.randomUUID();
        when(animalRepository.findAnimalFarms(eq(TENANT))).thenReturn(rows(new Object[] {animal, farmA}));
        when(groupRepository.findGroupFarms(eq(TENANT))).thenReturn(rows(new Object[] {group, farmA}));
        when(breedingRecordRepository.findBirthsBetween(eq(TENANT), any(), any())).thenReturn(rows(
                new Object[] {TODAY, null, animal, null}, new Object[] {TODAY, 4, animal, null}));
        when(mortalityRecordRepository.findBetween(eq(TENANT), any(), any())).thenReturn(rows(
                new Object[] {TODAY, 6, new BigDecimal("1800.50"), null, group}, new Object[] {TODAY, 1, null, animal, null}));

        AgTrendsResponse r = service().getTrends(TENANT, null, 1);

        assertEquals(5, r.livestock().get(0).births());
        assertEquals(7, r.livestock().get(0).deaths());
        assertNumber("1800.5", r.livestock().get(0).estimatedLoss());
    }

    @Test
    @DisplayName("every query is asked for the range the aggregator needs, ending today")
    void queriesTheNeededRange() {
        emptyTenant();

        service().getTrends(TENANT, null, 1);

        LocalDate from = AgTrendsAggregator.earliestNeeded(TODAY, 1);
        verify(feedRecordRepository).findCostsBetween(eq(TENANT), eq(from), eq(TODAY));
        verify(harvestRecordRepository).findBetween(eq(TENANT), eq(from), eq(TODAY));
        verify(breedingRecordRepository).findBirthsBetween(eq(TENANT), eq(from), eq(TODAY));
    }
}
