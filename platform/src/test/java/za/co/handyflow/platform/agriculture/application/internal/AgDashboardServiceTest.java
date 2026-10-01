package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.agriculture.domain.model.*;
import za.co.handyflow.platform.agriculture.domain.repository.*;
import za.co.handyflow.platform.agriculture.dto.AgDashboardResponse;
import za.co.handyflow.platform.agriculture.dto.AttentionItemResponse;
import za.co.handyflow.platform.agriculture.dto.FarmTodaySummaryResponse;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgDashboardServiceTest {

    @Mock AgFarmRepository farmRepository;
    @Mock AgAnimalRepository animalRepository;
    @Mock AgGroupRepository groupRepository;
    @Mock AgCropCycleRepository cropCycleRepository;
    @Mock AgHealthEventRepository healthEventRepository;
    @Mock AgScoutingRecordRepository scoutingRecordRepository;
    @Mock AgInventoryItemRepository inventoryItemRepository;
    @Mock AgSpeciesRepository speciesRepository;
    @Mock AgCropTypeRepository cropTypeRepository;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
    final UUID farmId = UUID.randomUUID();

    private AgDashboardService service() {
        return new AgDashboardService(farmRepository, animalRepository, groupRepository, cropCycleRepository,
                healthEventRepository, scoutingRecordRepository, inventoryItemRepository, speciesRepository, cropTypeRepository);
    }

    private AgFarm farm() {
        AgFarm farm = mock(AgFarm.class);
        when(farm.getId()).thenReturn(farmId);
        when(farm.getName()).thenReturn("Green Valley");
        when(farm.getFarmType()).thenReturn("MIXED");
        when(farm.getTotalHectares()).thenReturn(new BigDecimal("400"));
        when(farmRepository.findActiveById(eq(TENANT), eq(farmId))).thenReturn(Optional.of(farm));
        return farm;
    }

    private AgCropCycle cycleHarvestDue(LocalDate expected) {
        AgCropCycle c = mock(AgCropCycle.class);
        when(c.getId()).thenReturn(UUID.randomUUID());
        when(c.getCycleName()).thenReturn("Field 3 maize");
        when(c.getExpectedHarvestDate()).thenReturn(expected);
        return c;
    }

    private AgScoutingRecord highSeverityFinding(LocalDate followUp) {
        AgScoutingRecord r = mock(AgScoutingRecord.class);
        when(r.getId()).thenReturn(UUID.randomUUID());
        when(r.getObservationType()).thenReturn("PEST");
        when(r.getDescription()).thenReturn("Armyworm in the north block");
        when(r.getFollowUpDate()).thenReturn(followUp);
        return r;
    }

    private AgInventoryItem stock(BigDecimal quantity) {
        AgInventoryItem i = mock(AgInventoryItem.class);
        when(i.getId()).thenReturn(UUID.randomUUID());
        when(i.getItemName()).thenReturn("Maize seed");
        when(i.getCurrentQuantity()).thenReturn(quantity);
        when(i.getReorderLevel()).thenReturn(new BigDecimal("50"));
        when(i.getUnitOfMeasure()).thenReturn("kg");
        return i;
    }

    private void nothingDue() {
        when(healthEventRepository.findDueForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of());
        when(scoutingRecordRepository.findOpenHighSeverityForFarm(eq(TENANT), eq(farmId))).thenReturn(List.of());
        when(scoutingRecordRepository.findFollowUpDueForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of());
        when(cropCycleRepository.findHarvestDueForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of());
        when(inventoryItemRepository.findBelowReorderLevelForFarm(eq(TENANT), eq(farmId))).thenReturn(List.of());
    }

    @Test
    @DisplayName("attention: critical first, a high-severity finding with a due follow-up is reported once, and every item names its farm")
    void attentionIsRankedAndDeduplicated() {
        farm();
        nothingDue();
        AgScoutingRecord finding = highSeverityFinding(TODAY.minusDays(1));
        when(scoutingRecordRepository.findOpenHighSeverityForFarm(eq(TENANT), eq(farmId))).thenReturn(List.of(finding));
        when(scoutingRecordRepository.findFollowUpDueForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of(finding));   // same record
        AgCropCycle overdueHarvest = cycleHarvestDue(TODAY.minusDays(2));      // mocks are built BEFORE stubbing a repository with them:
        AgInventoryItem outOfStock = stock(BigDecimal.ZERO);                   // creating one inside when(..).thenReturn(..) is nested stubbing
        when(cropCycleRepository.findHarvestDueForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of(overdueHarvest));
        when(inventoryItemRepository.findBelowReorderLevelForFarm(eq(TENANT), eq(farmId))).thenReturn(List.of(outOfStock));

        List<AttentionItemResponse> items = service().getAttention(TENANT, farmId);

        assertEquals(List.of("SCOUTING_HIGH_SEVERITY", "LOW_STOCK", "HARVEST_DUE"), items.stream().map(AttentionItemResponse::type).toList());
        assertEquals(List.of("CRITICAL", "CRITICAL", "OVERDUE"), items.stream().map(AttentionItemResponse::severity).toList());
        assertTrue(items.get(1).title().contains("out of stock"));
        for (AttentionItemResponse item : items) {
            assertEquals(farmId, item.farmId());
            assertEquals("Green Valley", item.farmName());
        }
    }

    @Test
    @DisplayName("attention: a harvest due in three days is UPCOMING, and stock below reorder level but not empty is MEDIUM")
    void upcomingAndMedium() {
        farm();
        nothingDue();
        AgCropCycle upcomingHarvest = cycleHarvestDue(TODAY.plusDays(3));
        AgInventoryItem lowStock = stock(new BigDecimal("20"));
        when(cropCycleRepository.findHarvestDueForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of(upcomingHarvest));
        when(inventoryItemRepository.findBelowReorderLevelForFarm(eq(TENANT), eq(farmId))).thenReturn(List.of(lowStock));

        List<AttentionItemResponse> items = service().getAttention(TENANT, farmId);

        assertEquals(List.of("UPCOMING", "MEDIUM"), items.stream().map(AttentionItemResponse::severity).toList());
        assertTrue(items.get(1).title().endsWith("is low"));
    }

    @Test
    @DisplayName("the today summary reports the counts the repositories return, with attention attached")
    void todaySummary() {
        farm();
        nothingDue();
        when(animalRepository.countActiveForFarm(eq(TENANT), eq(farmId))).thenReturn(5L);
        when(groupRepository.countActiveForFarm(eq(TENANT), eq(farmId))).thenReturn(2L);
        when(cropCycleRepository.countActiveForFarm(eq(TENANT), eq(farmId))).thenReturn(3L);

        FarmTodaySummaryResponse summary = service().getTodaySummary(TENANT, farmId);

        assertEquals(5, summary.activeAnimalCount());
        assertEquals(2, summary.activeGroupCount());
        assertEquals(3, summary.activeCropCycleCount());
        assertTrue(summary.attentionItems().isEmpty());
    }

    @Test
    @DisplayName("the tenant dashboard turns grouped rows into totals")
    void tenantDashboard() {
        AgFarm farm = farm();
        nothingDue();
        UUID species = UUID.randomUUID();
        UUID crop = UUID.randomUUID();
        when(farmRepository.findByStatus(eq(TENANT), eq("ACTIVE"), any())).thenReturn(new PageImpl<AgFarm>(List.of(farm)));
        when(cropCycleRepository.summarizeByFarmAndStatus(eq(TENANT))).thenReturn(List.<Object[]>of(
                new Object[] {farmId, "GROWING", 2L, new BigDecimal("50.5")}, new Object[] {farmId, "PLANNED", 1L, new BigDecimal("10")}));
        when(cropCycleRepository.summarizeInProductionByFarmAndCrop(eq(TENANT))).thenReturn(List.<Object[]>of(new Object[] {farmId, crop, 2L, new BigDecimal("50.5")}));
        when(animalRepository.countActiveByFarmAndSpecies(eq(TENANT))).thenReturn(List.<Object[]>of(new Object[] {farmId, species, 40L}));
        when(groupRepository.summarizeActiveByFarmAndSpecies(eq(TENANT))).thenReturn(List.<Object[]>of(new Object[] {farmId, species, 1L, 60L}));
        when(speciesRepository.findAllActive(eq(TENANT), any())).thenReturn(new PageImpl<AgSpecies>(List.of()));
        when(cropTypeRepository.findAllActive(eq(TENANT), any())).thenReturn(new PageImpl<AgCropType>(List.of()));

        AgDashboardResponse r = service().getTenantDashboard(TENANT);

        assertEquals(1, r.totals().farmCount());
        assertEquals(0, new BigDecimal("400").compareTo(r.totals().totalHectares()));
        assertEquals(0, new BigDecimal("50.5").compareTo(r.totals().hectaresInProduction()));
        assertEquals(1, r.totals().plannedCropCycles());
        assertEquals(100, r.totals().totalHead());           // 40 animals + 60 head in a group
        assertEquals(1, r.livestock().size());
        assertEquals("Unknown species", r.livestock().get(0).name());   // no species rows were returned
    }
}
