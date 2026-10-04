package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgEnterprise;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.model.AgHarvestRecord;
import za.co.handyflow.platform.agriculture.domain.model.AgInputApplication;
import za.co.handyflow.platform.agriculture.domain.model.AgSeason;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCostEntryRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgEnterpriseRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHarvestRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInputApplicationRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgSeasonRepository;
import za.co.handyflow.platform.agriculture.dto.AnimalCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.CropCycleCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.GroupCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.UnitProfit;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SalesTotalsResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.TargetRevenue;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgProfitabilityServiceTest {

    @Mock AgFarmRepository farmRepository;
    @Mock AgSeasonRepository seasonRepository;
    @Mock AgCropCycleRepository cropCycleRepository;
    @Mock AgGroupRepository groupRepository;
    @Mock AgAnimalRepository animalRepository;
    @Mock AgEnterpriseRepository enterpriseRepository;
    @Mock AgCostEntryRepository costEntryRepository;
    @Mock AgInputApplicationRepository inputApplicationRepository;
    @Mock AgHarvestRecordRepository harvestRecordRepository;
    @Mock AgCostReportingService costReportingService;
    @Mock AgSalesAllocationService salesService;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    final UUID farmId = UUID.randomUUID(), cycleId = UUID.randomUUID(), groupId = UUID.randomUUID(), animalId = UUID.randomUUID(), entId = UUID.randomUUID();

    private AgProfitabilityService service() {
        return new AgProfitabilityService(farmRepository, seasonRepository, cropCycleRepository, groupRepository, animalRepository, enterpriseRepository, costEntryRepository,
                inputApplicationRepository, harvestRecordRepository, costReportingService, salesService);
    }
    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static void num(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }

    @BeforeEach
    void emptyFarm() {
        when(farmRepository.findActiveById(eq(TENANT), eq(farmId))).thenReturn(Optional.of(mock(AgFarm.class)));
        when(cropCycleRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of()));
        when(groupRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of()));
        when(animalRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of()));
        when(enterpriseRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of()));
        when(costReportingService.getFarmCropCycleCostSummaries(eq(TENANT), eq(farmId))).thenReturn(List.of());
        when(costReportingService.getFarmGroupCostSummaries(eq(TENANT), eq(farmId))).thenReturn(List.of());
        when(costReportingService.getFarmAnimalCostSummaries(eq(TENANT), eq(farmId))).thenReturn(List.of());
        when(costEntryRepository.sumByTargetAndCategoryForFarm(eq(TENANT), eq(farmId))).thenReturn(List.of());
        when(inputApplicationRepository.findUncostedLabourForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of());
        when(harvestRecordRepository.findUncostedLabourForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of());
        when(salesService.totals(eq(TENANT), eq(farmId), any(), any())).thenReturn(new SalesTotalsResponse(BigDecimal.ZERO, 0, 0, List.of()));
    }

    private AgCropCycle cycle(String status) {
        AgCropCycle c = mock(AgCropCycle.class);
        when(c.getId()).thenReturn(cycleId); when(c.getCycleName()).thenReturn("Maize - Field 3"); when(c.getStatus()).thenReturn(status);
        when(cropCycleRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of(c)));
        return c;
    }
    private void cycleCost(String total) {
        CropCycleCostSummaryResponse s = mock(CropCycleCostSummaryResponse.class);
        when(s.cropCycleId()).thenReturn(cycleId); when(s.totalCost()).thenReturn(bd(total));
        when(costReportingService.getFarmCropCycleCostSummaries(eq(TENANT), eq(farmId))).thenReturn(List.of(s));
    }
    private static Object[] ledgerRow(String type, UUID id, String category, String amount) { return new Object[] {type, id, category, bd(amount)}; }
    private void ledger(Object[]... rows) { when(costEntryRepository.sumByTargetAndCategoryForFarm(eq(TENANT), eq(farmId))).thenReturn(List.of(rows)); }
    private void sales(int allocations, int notCounted, TargetRevenue... rows) { when(salesService.totals(eq(TENANT), eq(farmId), any(), any())).thenReturn(new SalesTotalsResponse(BigDecimal.ZERO, allocations, notCounted, List.of(rows))); }
    private static UnitProfit unit(ProfitabilityResponse p, String type) { return p.units().stream().filter(u -> type.equals(u.targetType())).findFirst().orElseThrow(); }

    @Test
    @DisplayName("a crop cycle combines its recorded costs, its ledger costs and its revenue, from the three places they live")
    void combinesTheThreeSources() {
        cycle("HARVESTED"); cycleCost("1000");
        ledger(ledgerRow("CROP_CYCLE", cycleId, "LABOUR", "200"), ledgerRow("CROP_CYCLE", cycleId, "FUEL", "300"));
        sales(1, 0, new TargetRevenue("CROP_CYCLE", cycleId, bd("10"), bd("5000")));

        ProfitabilityResponse p = service().farm(TENANT, farmId);

        UnitProfit u = unit(p, "CROP_CYCLE");
        assertEquals("Maize - Field 3", u.label());
        num("5000.00", u.revenue()); num("1000.00", u.recordedCost()); num("200.00", u.labour()); num("300.00", u.fuel());
        num("1500.00", u.directCost()); num("3500.00", u.grossMargin()); assertEquals("COMPLETE", u.state());
        num("3500.00", p.totals().grossMargin());
    }

    @Test
    @DisplayName("an animal is labelled with its tag and name, and a group carries its acquisition type so the purchase-price caveat can apply")
    void animalAndGroup() {
        AgAnimal named = mock(AgAnimal.class), plain = mock(AgAnimal.class);
        when(named.getId()).thenReturn(animalId); when(named.getTagNumber()).thenReturn("TAG 1"); when(named.getName()).thenReturn("Bessie"); when(named.getStatus()).thenReturn("SOLD");
        UUID plainId = UUID.randomUUID();
        when(plain.getId()).thenReturn(plainId); when(plain.getTagNumber()).thenReturn("TAG 2"); when(plain.getName()).thenReturn(" "); when(plain.getStatus()).thenReturn("ACTIVE");
        when(animalRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of(named, plain)));
        AnimalCostSummaryResponse a1 = mock(AnimalCostSummaryResponse.class), a2 = mock(AnimalCostSummaryResponse.class);
        when(a1.animalId()).thenReturn(animalId); when(a1.totalCost()).thenReturn(bd("3000"));
        when(a2.animalId()).thenReturn(plainId); when(a2.totalCost()).thenReturn(bd("10"));
        when(costReportingService.getFarmAnimalCostSummaries(eq(TENANT), eq(farmId))).thenReturn(List.of(a1, a2));
        AgGroup g = mock(AgGroup.class);
        when(g.getId()).thenReturn(groupId); when(g.getBatchNumber()).thenReturn("Batch 7"); when(g.getStatus()).thenReturn("ACTIVE"); when(g.getAcquisitionType()).thenReturn("PURCHASED");
        when(groupRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of(g)));
        GroupCostSummaryResponse gs = mock(GroupCostSummaryResponse.class);
        when(gs.groupId()).thenReturn(groupId); when(gs.totalCost()).thenReturn(bd("500"));
        when(costReportingService.getFarmGroupCostSummaries(eq(TENANT), eq(farmId))).thenReturn(List.of(gs));

        ProfitabilityResponse p = service().farm(TENANT, farmId);

        List<String> labels = p.units().stream().map(UnitProfit::label).toList();
        assertTrue(labels.contains("TAG 1 (Bessie)"), labels.toString());
        assertTrue(labels.contains("TAG 2"), labels.toString());
        UnitProfit batch = p.units().stream().filter(u -> "Batch 7".equals(u.label())).findFirst().orElseThrow();
        assertTrue(batch.caveats().get(0).contains("purchase price"), batch.caveats().toString());
    }

    private AgAnimal animal(UUID id, String tag, boolean breeding, String cost, String acquisition) {
        AgAnimal a = mock(AgAnimal.class);
        when(a.getId()).thenReturn(id); when(a.getTagNumber()).thenReturn(tag); when(a.getName()).thenReturn(null); when(a.getStatus()).thenReturn("ACTIVE"); when(a.isBreedingStock()).thenReturn(breeding);
        AnimalCostSummaryResponse s = mock(AnimalCostSummaryResponse.class);
        when(s.animalId()).thenReturn(id); when(s.totalCost()).thenReturn(bd(cost)); when(s.acquisitionCost()).thenReturn(acquisition == null ? null : bd(acquisition));
        when(costReportingService.getFarmAnimalCostSummaries(eq(TENANT), eq(farmId))).thenReturn(List.of(s));
        when(animalRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of(a)));
        return a;
    }

    @Test
    @DisplayName("breeding stock's purchase price (capital) is taken out of its recorded cost, and its feed and health stay in")
    void breedingStockPriceRemoved() {
        animal(animalId, "TAG 1", true, "45800", "45000");

        UnitProfit u = unit(service().farm(TENANT, farmId), "ANIMAL");

        num("800.00", u.recordedCost());
        assertEquals("BREEDING_STOCK", u.state());
    }

    @Test
    @DisplayName("an ordinary animal keeps its purchase price as a cost")
    void ordinaryAnimalKeepsPrice() {
        animal(animalId, "TAG 2", false, "45800", "45000");

        UnitProfit u = unit(service().farm(TENANT, farmId), "ANIMAL");

        num("45800.00", u.recordedCost());
        assertEquals("IN_PROGRESS", u.state());
    }

    @Test
    @DisplayName("breeding stock with no purchase price recorded loses nothing")
    void breedingStockWithoutPrice() {
        animal(animalId, "TAG 3", true, "800", null);

        num("800.00", unit(service().farm(TENANT, farmId), "ANIMAL").recordedCost());
    }

    @Test
    @DisplayName("an enterprise shows the costs allocated straight to it")
    void enterprise() {
        AgEnterprise e = mock(AgEnterprise.class);
        when(e.getId()).thenReturn(entId); when(e.getName()).thenReturn("Maize enterprise"); when(e.getStatus()).thenReturn("ACTIVE");
        when(enterpriseRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of(e)));
        ledger(ledgerRow("ENTERPRISE", entId, "OTHER_DIRECT", "75"));

        UnitProfit u = unit(service().farm(TENANT, farmId), "ENTERPRISE");

        assertEquals("Maize enterprise", u.label());
        num("75.00", u.otherDirect()); assertEquals("IN_PROGRESS", u.state());
    }

    @Test
    @DisplayName("a cost or sale on a target no longer listed is kept in the catch-all row, so the totals stay complete")
    void unlisted() {
        UUID gone = UUID.randomUUID();
        ledger(ledgerRow("ANIMAL", gone, "EQUIPMENT", "40"));
        sales(1, 0, new TargetRevenue("ANIMAL", gone, bd("1"), bd("300")));

        ProfitabilityResponse p = service().farm(TENANT, farmId);

        UnitProfit u = unit(p, "UNLISTED");
        num("300.00", u.revenue()); num("40.00", u.equipment());
        num("260.00", p.totals().grossMargin());
    }

    @Test
    @DisplayName("uncosted labour (inputs plus harvests) and uncounted sales are reported as notes")
    void notesFromCounts() {
        AgInputApplication i1 = mock(AgInputApplication.class), i2 = mock(AgInputApplication.class);
        when(inputApplicationRepository.findUncostedLabourForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of(i1, i2));
        when(harvestRecordRepository.findUncostedLabourForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of(mock(AgHarvestRecord.class)));
        sales(3, 2);

        List<String> notes = service().farm(TENANT, farmId).notes();

        assertTrue(notes.stream().anyMatch(n -> n.contains("3 recorded labour entries aren't costed yet")), notes.toString());
        assertTrue(notes.stream().anyMatch(n -> n.contains("2 sale allocations aren't counted")), notes.toString());
    }

    @Test
    @DisplayName("when a cap is reached the report says it was cut off")
    void truncationIsSaid() {
        List<AgCropCycle> many = Collections.nCopies(1000, mock(AgCropCycle.class));
        when(cropCycleRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(new ArrayList<>(many)));
        sales(1000, 0);

        List<String> notes = service().farm(TENANT, farmId).notes();

        assertTrue(notes.stream().anyMatch(n -> n.contains("Only the first 1000 crop cycles")), notes.toString());
        assertTrue(notes.stream().anyMatch(n -> n.contains("1000 most recent sale allocations")), notes.toString());
    }

    @Test
    @DisplayName("below the caps there is no cut-off note")
    void noTruncationNote() {
        cycle("GROWING");
        sales(999, 0);
        assertTrue(service().farm(TENANT, farmId).notes().stream().noneMatch(n -> n.contains("Only the first") || n.contains("most recent sale")));
    }

    @Test
    @DisplayName("revenue is read for the whole farm in one call, never per target")
    void readsRevenueOnce() {
        cycle("GROWING");
        service().farm(TENANT, farmId);
        verify(salesService, times(1)).totals(eq(TENANT), eq(farmId), any(), any());
    }

    @Test
    @DisplayName("an unknown farm is a 404 and nothing else is read")
    void unknownFarm() {
        UUID other = UUID.randomUUID();
        when(farmRepository.findActiveById(eq(TENANT), eq(other))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service().farm(TENANT, other));

        verifyNoInteractions(salesService, costEntryRepository, costReportingService);
    }

    // ---- season filter -------------------------------------------------------------------------------------------------

    final UUID seasonId = UUID.randomUUID(), otherCycleId = UUID.randomUUID();

    private void seasonOfThisFarm(String name) {
        AgSeason season = mock(AgSeason.class);
        when(season.getFarmId()).thenReturn(farmId); when(season.getName()).thenReturn(name);
        when(seasonRepository.findActiveById(eq(TENANT), eq(seasonId))).thenReturn(Optional.of(season));
    }
    private AgCropCycle seasonCycle(UUID id, String name, String status) {
        AgCropCycle c = mock(AgCropCycle.class);
        when(c.getId()).thenReturn(id); when(c.getCycleName()).thenReturn(name); when(c.getStatus()).thenReturn(status); when(c.getFarmId()).thenReturn(farmId);
        return c;
    }

    @Test
    @DisplayName("a season report covers that season's crop cycles only: the whole farm's cycles, groups, animals and enterprises are not loaded")
    void seasonCoversItsCyclesOnly() {
        seasonOfThisFarm("2026/27");
        AgCropCycle mine = seasonCycle(cycleId, "Maize - Field 3", "HARVESTED");
        when(cropCycleRepository.findAllActiveForSeason(eq(TENANT), eq(seasonId), any())).thenReturn(new PageImpl<>(List.of(mine)));
        cycleCost("1000");

        ProfitabilityResponse p = service().farm(TENANT, farmId, seasonId);

        assertEquals(List.of("Maize - Field 3"), p.units().stream().map(UnitProfit::label).toList());
        verify(cropCycleRepository, never()).findAllActiveForFarm(any(), any(), any());
        verify(groupRepository, never()).findAllActiveForFarm(any(), any(), any());
        verify(animalRepository, never()).findAllActiveForFarm(any(), any(), any());
        verify(enterpriseRepository, never()).findAllActiveForFarm(any(), any(), any());
    }

    @Test
    @DisplayName("only the ledger costs and sales of the season's cycles are counted; other targets do not leak into a catch-all row")
    void seasonCountsOnlyItsOwnMoney() {
        seasonOfThisFarm("2026/27");
        when(cropCycleRepository.findAllActiveForSeason(eq(TENANT), eq(seasonId), any())).thenReturn(new PageImpl<>(List.of(seasonCycle(cycleId, "Maize", "HARVESTED"))));
        cycleCost("1000");
        ledger(ledgerRow("CROP_CYCLE", cycleId, "LABOUR", "200"), ledgerRow("CROP_CYCLE", otherCycleId, "LABOUR", "9999"), ledgerRow("GROUP", groupId, "FUEL", "7777"));
        sales(3, 0, new TargetRevenue("CROP_CYCLE", cycleId, bd("10"), bd("5000")), new TargetRevenue("CROP_CYCLE", otherCycleId, bd("1"), bd("8888")), new TargetRevenue("ANIMAL", animalId, bd("1"), bd("6666")));

        ProfitabilityResponse p = service().farm(TENANT, farmId, seasonId);

        num("5000.00", p.totals().revenue());
        num("1200.00", p.totals().directCost());
        assertTrue(p.units().stream().noneMatch(u -> "UNLISTED".equals(u.targetType())), "other targets must not appear as a catch-all row");
    }

    @Test
    @DisplayName("a season report says what it leaves out, by season name")
    void seasonNote() {
        seasonOfThisFarm("2026/27");
        when(cropCycleRepository.findAllActiveForSeason(eq(TENANT), eq(seasonId), any())).thenReturn(new PageImpl<>(List.of()));

        List<String> notes = service().farm(TENANT, farmId, seasonId).notes();

        assertTrue(notes.stream().anyMatch(n -> n.contains("Season report for 2026/27") && n.contains("Livestock and enterprises aren't tied to a season")), notes.toString());
    }

    @Test
    @DisplayName("a whole-farm report carries no season note")
    void noSeasonNoteForWholeFarm() {
        assertTrue(service().farm(TENANT, farmId).notes().stream().noneMatch(n -> n.contains("Season report")));
    }

    @Test
    @DisplayName("uncosted labour in a season report counts only that season's cycles, and uncounted sales are not claimed")
    void seasonLabourCountsOnlyItsCycles() {
        seasonOfThisFarm("2026/27");
        when(cropCycleRepository.findAllActiveForSeason(eq(TENANT), eq(seasonId), any())).thenReturn(new PageImpl<>(List.of(seasonCycle(cycleId, "Maize", "GROWING"))));
        AgInputApplication mine = mock(AgInputApplication.class), elsewhere = mock(AgInputApplication.class);
        when(mine.getCropCycleId()).thenReturn(cycleId); when(elsewhere.getCropCycleId()).thenReturn(otherCycleId);
        AgHarvestRecord harvestElsewhere = mock(AgHarvestRecord.class);
        when(harvestElsewhere.getCropCycleId()).thenReturn(otherCycleId);
        when(inputApplicationRepository.findUncostedLabourForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of(mine, elsewhere));
        when(harvestRecordRepository.findUncostedLabourForFarm(eq(TENANT), eq(farmId), any())).thenReturn(List.of(harvestElsewhere));
        sales(5, 4);

        List<String> notes = service().farm(TENANT, farmId, seasonId).notes();

        assertTrue(notes.stream().anyMatch(n -> n.contains("1 recorded labour entry isn't costed yet")), notes.toString());
        assertTrue(notes.stream().noneMatch(n -> n.contains("sale allocations aren't counted") || n.contains("sale allocation isn't counted")), notes.toString());
    }

    @Test
    @DisplayName("a season of another farm, or one that does not exist, is a 404 and nothing is loaded")
    void unknownSeason() {
        AgSeason elsewhere = mock(AgSeason.class);
        when(elsewhere.getFarmId()).thenReturn(UUID.randomUUID());
        UUID otherSeason = UUID.randomUUID();
        when(seasonRepository.findActiveById(eq(TENANT), eq(otherSeason))).thenReturn(Optional.of(elsewhere));

        assertThrows(ResourceNotFoundException.class, () -> service().farm(TENANT, farmId, otherSeason));
        assertThrows(ResourceNotFoundException.class, () -> service().farm(TENANT, farmId, UUID.randomUUID()));

        verifyNoInteractions(salesService, costEntryRepository, costReportingService);
    }

    // ---- all-farms overview --------------------------------------------------------------------------------------------

    private AgFarm farmNamed(UUID id, String name) {
        AgFarm f = mock(AgFarm.class);
        when(f.getId()).thenReturn(id); when(f.getName()).thenReturn(name);
        return f;
    }
    private ProfitabilityResponse reportFor(UUID id, String recorded, String revenue) {
        UUID c = UUID.randomUUID();
        return AgProfitabilityAggregator.build(id, List.of(new AgProfitabilityAggregator.UnitInput("CROP_CYCLE", c, "Maize", "HARVESTED", null, bd(recorded))), List.of(),
                List.of(new AgProfitabilityAggregator.RevenueRow("CROP_CYCLE", c, bd(revenue))), 0, 0, List.of());
    }

    @Test
    @DisplayName("the overview is each farm's own whole-farm report, summed: it asks the service for every farm and adds what comes back")
    void overviewSumsEachFarmsOwnReport() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        when(farmRepository.findAllActive(eq(TENANT), any())).thenReturn(new PageImpl<>(List.of(farmNamed(a, "Alpha"), farmNamed(b, "Beta"))));
        AgProfitabilityService spy = spy(service());
        doReturn(reportFor(a, "100", "500")).when(spy).farm(eq(TENANT), eq(a));
        doReturn(reportFor(b, "200", "700")).when(spy).farm(eq(TENANT), eq(b));

        var o = spy.overview(TENANT);

        assertEquals(List.of("Alpha", "Beta"), o.farms().stream().map(f -> f.farmName()).toList());
        num("1200.00", o.totals().revenue()); num("300.00", o.totals().directCost()); num("900.00", o.totals().grossMargin());
        verify(spy).farm(eq(TENANT), eq(a)); verify(spy).farm(eq(TENANT), eq(b));
    }

    @Test
    @DisplayName("no farms: an empty overview, and no farm report is built")
    void overviewWithNoFarms() {
        when(farmRepository.findAllActive(eq(TENANT), any())).thenReturn(new PageImpl<>(List.of()));
        AgProfitabilityService spy = spy(service());

        var o = spy.overview(TENANT);

        assertTrue(o.farms().isEmpty());
        num("0.00", o.totals().revenue());
        verify(spy, never()).farm(any(), any());
    }

    @Test
    @DisplayName("at most 50 farms are included, and the overview says so when the cap is reached")
    void overviewIsCapped() {
        List<AgFarm> many = new ArrayList<>();
        for (int i = 0; i < 50; i++) many.add(farmNamed(UUID.randomUUID(), "Farm " + i));
        when(farmRepository.findAllActive(eq(TENANT), eq(org.springframework.data.domain.Pageable.ofSize(50)))).thenReturn(new PageImpl<>(many));
        AgProfitabilityService spy = spy(service());
        doReturn(reportFor(UUID.randomUUID(), "1", "2")).when(spy).farm(eq(TENANT), any());

        var o = spy.overview(TENANT);

        assertEquals(50, o.farms().size());
        assertTrue(o.notes().stream().anyMatch(n -> n.contains("Only the first 50 farms are included")), o.notes().toString());
    }

    @Test
    @DisplayName("below the cap there is no cut-off note")
    void overviewBelowTheCap() {
        UUID a = UUID.randomUUID();
        when(farmRepository.findAllActive(eq(TENANT), any())).thenReturn(new PageImpl<>(List.of(farmNamed(a, "Alpha"))));
        AgProfitabilityService spy = spy(service());
        doReturn(reportFor(a, "1", "2")).when(spy).farm(eq(TENANT), eq(a));

        assertTrue(spy.overview(TENANT).notes().stream().noneMatch(n -> n.contains("Only the first")));
    }
}
