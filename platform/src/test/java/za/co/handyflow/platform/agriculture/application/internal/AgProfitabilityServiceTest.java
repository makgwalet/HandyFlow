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
        return new AgProfitabilityService(farmRepository, cropCycleRepository, groupRepository, animalRepository, enterpriseRepository, costEntryRepository,
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
}
