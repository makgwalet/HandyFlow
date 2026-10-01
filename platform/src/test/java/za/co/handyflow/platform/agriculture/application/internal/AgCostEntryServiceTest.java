package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import za.co.handyflow.platform.agriculture.domain.model.*;
import za.co.handyflow.platform.agriculture.domain.repository.*;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.AllocationShare;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostTotalsResponse;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CreateCostEntryRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgCostEntryServiceTest {

    @Mock AgCostEntryRepository costEntryRepository;
    @Mock AgFarmRepository farmRepository;
    @Mock AgCropCycleRepository cropCycleRepository;
    @Mock AgGroupRepository groupRepository;
    @Mock AgAnimalRepository animalRepository;
    @Mock AgEnterpriseRepository enterpriseRepository;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
    final UUID farmId = UUID.randomUUID();
    final UUID otherFarm = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final UUID cycleId = UUID.randomUUID();
    final UUID groupId = UUID.randomUUID();

    private AgCostEntryService service() {
        return new AgCostEntryService(costEntryRepository, farmRepository,
                new AgTargetOwnership(cropCycleRepository, groupRepository, animalRepository, enterpriseRepository));
    }

    private static void assertNumber(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private void farmExists() {
        when(farmRepository.findActiveById(eq(TENANT), eq(farmId))).thenReturn(Optional.of(mock(AgFarm.class)));
    }

    private void cycleOn(UUID owner) {
        AgCropCycle c = mock(AgCropCycle.class);
        when(c.getFarmId()).thenReturn(owner);
        when(cropCycleRepository.findActiveById(eq(TENANT), eq(cycleId))).thenReturn(Optional.of(c));
    }

    private void groupOn(UUID owner) {
        AgGroup g = mock(AgGroup.class);
        when(g.getFarmId()).thenReturn(owner);
        when(groupRepository.findActiveById(eq(TENANT), eq(groupId))).thenReturn(Optional.of(g));
    }

    private CreateCostEntryRequest request(String amount, LocalDate date, AllocationShare... shares) {
        return new CreateCostEntryRequest(date, null, "Hired sprayer", new BigDecimal(amount), null, null, null, List.of(shares));
    }

    private static AllocationShare share(String type, UUID id, String pct) { return new AllocationShare(type, id, new BigDecimal(pct)); }

    @Test
    @DisplayName("a cost split across two targets becomes two rows that add up exactly, in one allocation group")
    void splitsExactly() {
        farmExists(); cycleOn(farmId); groupOn(farmId);

        List<CostEntryResponse> rows = service().createManual(TENANT, farmId, user,
                request("100.00", TODAY, share("CROP_CYCLE", cycleId, "33.33"), share("GROUP", groupId, "66.67")));

        assertEquals(2, rows.size());
        assertNumber("100.00", rows.get(0).amount().add(rows.get(1).amount()));
        assertNumber("33.33", rows.get(0).amount());
        assertNumber("66.67", rows.get(1).amount());
        assertEquals(rows.get(0).allocationGroupId(), rows.get(1).allocationGroupId());
        assertEquals("OTHER_DIRECT", rows.get(0).category());
        assertEquals("MANUAL", rows.get(0).sourceType());
        assertEquals("ACTIVE", rows.get(0).status());
        assertEquals(user, rows.get(0).createdBy());
        verify(costEntryRepository).saveAll(any());
    }

    @Test
    @DisplayName("a target that belongs to another farm is refused and nothing is saved")
    void refusesTargetOfAnotherFarm() {
        farmExists(); cycleOn(otherFarm);

        assertThrows(IllegalArgumentException.class, () -> service().createManual(TENANT, farmId, user,
                request("100", TODAY, share("CROP_CYCLE", cycleId, "100"))));
        verify(costEntryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("an unknown target is a 404 and nothing is saved")
    void refusesUnknownTarget() {
        farmExists();

        assertThrows(ResourceNotFoundException.class, () -> service().createManual(TENANT, farmId, user,
                request("100", TODAY, share("GROUP", groupId, "100"))));
        verify(costEntryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("one bad target in a split stops the whole cost, so a half-allocated cost can never be saved")
    void allOrNothing() {
        farmExists(); cycleOn(farmId);                      // the group does not exist

        assertThrows(ResourceNotFoundException.class, () -> service().createManual(TENANT, farmId, user,
                request("100", TODAY, share("CROP_CYCLE", cycleId, "50"), share("GROUP", groupId, "50"))));
        verify(costEntryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("percentages that do not total 100 are refused")
    void refusesBadPercentages() {
        farmExists(); cycleOn(farmId); groupOn(farmId);

        assertThrows(IllegalArgumentException.class, () -> service().createManual(TENANT, farmId, user,
                request("100", TODAY, share("CROP_CYCLE", cycleId, "60"), share("GROUP", groupId, "30"))));
        verify(costEntryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("only OTHER_DIRECT can be entered by hand")
    void refusesHandEnteredLabour() {
        farmExists(); cycleOn(farmId);
        CreateCostEntryRequest labour = new CreateCostEntryRequest(TODAY, "LABOUR", "Weeding", new BigDecimal("100"), null, null, null, List.of(share("CROP_CYCLE", cycleId, "100")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().createManual(TENANT, farmId, user, labour));
        assertTrue(ex.getMessage().contains("OTHER_DIRECT"));
        verify(costEntryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("a cost dated in the future, or on a farm that does not exist, is refused")
    void refusesFutureDateAndUnknownFarm() {
        farmExists(); cycleOn(farmId);
        assertThrows(IllegalArgumentException.class, () -> service().createManual(TENANT, farmId, user,
                request("100", TODAY.plusDays(1), share("CROP_CYCLE", cycleId, "100"))));

        assertThrows(ResourceNotFoundException.class, () -> service().createManual(TENANT, UUID.randomUUID(), user,
                request("100", TODAY, share("CROP_CYCLE", cycleId, "100"))));
        verify(costEntryRepository, never()).saveAll(any());
    }

    private List<AgCostEntry> savedGroup(UUID group) {
        AgCostEntry a = AgCostEntry.create(TENANT, farmId, TODAY, "OTHER_DIRECT", "Hired sprayer", "MANUAL", null, "CROP_CYCLE", cycleId, null, null, null,
                new BigDecimal("60.00"), new BigDecimal("60"), group, null, user);
        AgCostEntry b = AgCostEntry.create(TENANT, farmId, TODAY, "OTHER_DIRECT", "Hired sprayer", "MANUAL", null, "GROUP", groupId, null, null, null,
                new BigDecimal("40.00"), new BigDecimal("40"), group, null, user);
        return List.of(a, b);
    }

    @Test
    @DisplayName("reversing a split cost reverses every row and returns the negating rows")
    void reversesTheWholeGroup() {
        UUID group = UUID.randomUUID();
        List<AgCostEntry> rows = savedGroup(group);
        when(costEntryRepository.findByAllocationGroup(eq(TENANT), eq(group))).thenReturn(rows);

        List<CostEntryResponse> reversals = service().reverseGroup(TENANT, group, user, "Wrong crop");

        assertEquals(2, reversals.size());
        assertNumber("-60.00", reversals.get(0).amount());
        assertNumber("-40.00", reversals.get(1).amount());
        assertEquals("REVERSAL", reversals.get(0).status());
        assertEquals("REVERSED", rows.get(0).getStatus());
        assertEquals("REVERSED", rows.get(1).getStatus());
        verify(costEntryRepository, times(2)).saveAll(any());           // the originals, then the reversals
    }

    @Test
    @DisplayName("a cost that was already reversed cannot be reversed again; an unknown group is a 404")
    void reverseTwiceAndUnknown() {
        UUID group = UUID.randomUUID();
        List<AgCostEntry> rows = savedGroup(group);
        rows.get(0).reverse(null, user);                                  // already reversed
        when(costEntryRepository.findByAllocationGroup(eq(TENANT), eq(group))).thenReturn(rows);

        assertThrows(IllegalStateException.class, () -> service().reverseGroup(TENANT, group, user, null));
        verify(costEntryRepository, never()).saveAll(any());

        assertThrows(ResourceNotFoundException.class, () -> service().reverseGroup(TENANT, UUID.randomUUID(), user, null));
    }

    @Test
    @DisplayName("totals are net, in a fixed category order, and sum to the total")
    void totals() {
        farmExists();
        when(costEntryRepository.sumByCategoryForFarm(eq(TENANT), eq(farmId))).thenReturn(List.<Object[]>of(
                new Object[] {"OTHER_DIRECT", new BigDecimal("100.00")}, new Object[] {"LABOUR", new BigDecimal("250.50")}));

        CostTotalsResponse r = service().totals(TENANT, farmId, null, null);

        assertEquals(List.of("LABOUR", "OTHER_DIRECT"), r.byCategory().stream().map(c -> c.category()).toList());
        assertNumber("350.50", r.total());
    }

    @Test
    @DisplayName("a target filter uses the target queries; a target type needs an id and must be known")
    void listAndTotalsFilter() {
        farmExists();
        when(costEntryRepository.findForTarget(eq(TENANT), eq("GROUP"), eq(groupId), any())).thenReturn(new PageImpl<AgCostEntry>(savedGroup(UUID.randomUUID())));
        when(costEntryRepository.findForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<AgCostEntry>(List.of()));

        assertEquals(2, service().list(TENANT, farmId, "GROUP", groupId, PageRequest.of(0, 50)).getContent().size());
        assertEquals(0, service().list(TENANT, farmId, null, null, PageRequest.of(0, 50)).getContent().size());
        assertThrows(IllegalArgumentException.class, () -> service().list(TENANT, farmId, "GROUP", null, PageRequest.of(0, 50)));
        assertThrows(IllegalArgumentException.class, () -> service().list(TENANT, farmId, "TRACTOR", groupId, PageRequest.of(0, 50)));
        assertThrows(IllegalArgumentException.class, () -> service().totals(TENANT, farmId, "TRACTOR", groupId));
    }
}
