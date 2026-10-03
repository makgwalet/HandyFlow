package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.repository.AgCostEntryRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.AllocationShare;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.AllocateFuelRequest;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.FuelDispatchRow;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.FuelOverview;
import za.co.handyflow.platform.fleet.application.FleetFacade;
import za.co.handyflow.platform.fleet.application.FleetFacade.EquipmentSummary;
import za.co.handyflow.platform.fuel.application.FuelFacade;
import za.co.handyflow.platform.fuel.application.FuelFacade.OwnDispatch;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgFuelCostServiceTest {

    @Mock FuelFacade fuelFacade;
    @Mock FleetFacade fleetFacade;
    @Mock AgFarmRepository farmRepository;
    @Mock AgCostEntryRepository costEntryRepository;
    @Mock AgCostEntryService costEntryService;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    final UUID farmId = UUID.randomUUID(), user = UUID.randomUUID(), cycleId = UUID.randomUUID(), tankId = UUID.randomUUID(), vehicleId = UUID.randomUUID();
    static final LocalDate FROM = LocalDate.of(2026, 9, 1), TO = LocalDate.of(2026, 9, 30);

    private AgFuelCostService service() { return new AgFuelCostService(fuelFacade, fleetFacade, farmRepository, costEntryRepository, costEntryService); }
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private void farmExists() { when(farmRepository.findActiveById(eq(TENANT), eq(farmId))).thenReturn(Optional.of(mock(AgFarm.class))); }
    private void tractor() { when(fleetFacade.findEquipment(eq(TENANT), eq(vehicleId))).thenReturn(Optional.of(new EquipmentSummary(vehicleId, "FARM 001 GP", "John Deere", "6120M", "TRACTOR", null, null))); }
    private OwnDispatch dispatch(UUID id, String at, String litres, String cost) {
        return new OwnDispatch(id, Instant.parse(at), tankId, "Main diesel tank", vehicleId, null, "Tractor 1", bd(litres), cost == null ? null : bd(cost), bd("1234.5"));
    }
    private void listing(OwnDispatch... d) { when(fuelFacade.findOwnDispatches(eq(TENANT), any(), any(), anyInt())).thenReturn(List.of(d)); }
    private AllocateFuelRequest allocateTo(UUID dispatchId) { return new AllocateFuelRequest(dispatchId, "Ploughing", List.of(new AllocationShare("CROP_CYCLE", cycleId, bd("100")))); }
    private void verifyNothingRecorded() { verify(costEntryService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()); }

    // ---- listing ----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("only dispatches not yet allocated are listed, each with its cost at the snapshotted rate, and the done ones are counted")
    void listsUnallocated() {
        farmExists(); tractor();
        UUID open = UUID.randomUUID(), done = UUID.randomUUID();
        listing(dispatch(open, "2026-09-15T08:30:00Z", "500", "22.4"), dispatch(done, "2026-09-14T08:30:00Z", "100", "22.4"));
        when(costEntryRepository.findActiveSourceRefs(eq(TENANT), eq("FUEL_DISPATCH"), any())).thenReturn(List.of(done));

        FuelOverview o = service().unallocated(TENANT, farmId, FROM, TO);

        assertEquals(1, o.dispatches().size());
        assertEquals(1, o.alreadyAllocated());
        FuelDispatchRow r = o.dispatches().get(0);
        assertEquals(open, r.dispatchId());
        assertEquals("Main diesel tank", r.tankName());
        assertEquals("FARM 001 GP (John Deere 6120M)", r.vehicle());
        assertEquals(0, bd("11200.00").compareTo(r.cost()));                // 500 L x R22.40
        assertEquals(0, bd("1234.5").compareTo(r.hoursReading()));
    }

    @Test
    @DisplayName("a dispatch with no cost recorded is listed with no cost, and cannot be guessed")
    void noCostListed() {
        farmExists(); tractor();
        listing(dispatch(UUID.randomUUID(), "2026-09-15T08:30:00Z", "500", null));
        when(costEntryRepository.findActiveSourceRefs(eq(TENANT), any(), any())).thenReturn(List.of());

        FuelDispatchRow r = service().unallocated(TENANT, farmId, FROM, TO).dispatches().get(0);

        assertNull(r.costPerLitre());
        assertNull(r.cost());
    }

    @Test
    @DisplayName("the date is the South African date: 22:30 UTC is already the next day in SAST")
    void sastDate() {
        farmExists(); tractor();
        listing(dispatch(UUID.randomUUID(), "2026-09-15T22:30:00Z", "100", "22"));
        when(costEntryRepository.findActiveSourceRefs(eq(TENANT), any(), any())).thenReturn(List.of());

        assertEquals(LocalDate.of(2026, 9, 16), service().unallocated(TENANT, farmId, FROM, TO).dispatches().get(0).date());
    }

    @Test
    @DisplayName("the range is whole SAST days, from the start of the first to the end of the last")
    void rangeBoundaries() {
        farmExists();
        when(fuelFacade.findOwnDispatches(eq(TENANT), any(), any(), anyInt())).thenReturn(List.of());

        service().unallocated(TENANT, farmId, FROM, TO);

        verify(fuelFacade).findOwnDispatches(eq(TENANT), eq(FROM.atStartOfDay(SAST).toInstant()), eq(TO.plusDays(1).atStartOfDay(SAST).toInstant()), anyInt());
    }

    @Test
    @DisplayName("the vehicle is looked up once however many of its dispatches are listed; no dispatches means no ledger query")
    void lookupsAreBatched() {
        farmExists(); tractor();
        listing(dispatch(UUID.randomUUID(), "2026-09-15T08:30:00Z", "100", "22"), dispatch(UUID.randomUUID(), "2026-09-16T08:30:00Z", "100", "22"));
        when(costEntryRepository.findActiveSourceRefs(eq(TENANT), any(), any())).thenReturn(List.of());

        service().unallocated(TENANT, farmId, FROM, TO);
        verify(fleetFacade, times(1)).findEquipment(eq(TENANT), eq(vehicleId));

        clearInvocations(costEntryRepository);
        listing();
        service().unallocated(TENANT, farmId, FROM, TO);
        verifyNoInteractions(costEntryRepository);
    }

    @Test
    @DisplayName("a bad range is refused")
    void badRange() {
        farmExists();
        assertThrows(IllegalArgumentException.class, () -> service().unallocated(TENANT, farmId, TO, FROM));
        assertThrows(IllegalArgumentException.class, () -> service().unallocated(TENANT, farmId, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 30)));
        assertThrows(ResourceNotFoundException.class, () -> service().unallocated(TENANT, UUID.randomUUID(), FROM, TO));
    }

    // ---- allocating -------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("allocating costs the litres at the tank's snapshotted cost per litre, on the SAST date, with the source pointing at the dispatch")
    void allocates() {
        farmExists(); tractor();
        UUID id = UUID.randomUUID();
        OwnDispatch d = dispatch(id, "2026-09-15T22:30:00Z", "500", "22.4");
        when(fuelFacade.findOwnDispatch(eq(TENANT), eq(id))).thenReturn(Optional.of(d));
        CostEntryResponse entry = mock(CostEntryResponse.class);
        when(costEntryService.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(entry));

        List<CostEntryResponse> out = service().allocate(TENANT, farmId, user, allocateTo(id));

        assertEquals(1, out.size());
        verify(costEntryService).record(eq(TENANT), eq(farmId), eq(user), eq(LocalDate.of(2026, 9, 16)), eq("FUEL"),
                eq("Fuel: 500 L from Main diesel tank to FARM 001 GP (John Deere 6120M)"), eq("FUEL_DISPATCH"), eq(id), eq(bd("500")), eq("L"),
                eq(bd("22.4000")), eq(bd("11200.00")), eq("Ploughing"), any());
    }

    @Test
    @DisplayName("fuel already allocated is refused: it can never be counted twice")
    void refusesAlreadyAllocated() {
        farmExists(); tractor();
        UUID id = UUID.randomUUID();
        OwnDispatch d = dispatch(id, "2026-09-15T08:30:00Z", "500", "22.4");
        when(fuelFacade.findOwnDispatch(eq(TENANT), eq(id))).thenReturn(Optional.of(d));
        when(costEntryRepository.countActiveBySource(eq(TENANT), eq("FUEL_DISPATCH"), eq(id))).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().allocate(TENANT, farmId, user, allocateTo(id)));

        assertTrue(ex.getMessage().contains("already allocated"), ex.getMessage());
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("a dispatch with no cost per litre, or no litres, cannot be costed")
    void refusesNoCost() {
        farmExists(); tractor();
        UUID noCost = UUID.randomUUID(), noLitres = UUID.randomUUID();
        OwnDispatch a = dispatch(noCost, "2026-09-15T08:30:00Z", "500", null);
        OwnDispatch b = dispatch(noLitres, "2026-09-15T08:30:00Z", "0", "22.4");
        when(fuelFacade.findOwnDispatch(eq(TENANT), eq(noCost))).thenReturn(Optional.of(a));
        when(fuelFacade.findOwnDispatch(eq(TENANT), eq(noLitres))).thenReturn(Optional.of(b));

        assertTrue(assertThrows(IllegalArgumentException.class, () -> service().allocate(TENANT, farmId, user, allocateTo(noCost))).getMessage().contains("no cost per litre"));
        assertThrows(IllegalArgumentException.class, () -> service().allocate(TENANT, farmId, user, allocateTo(noLitres)));
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("a dispatch that is not the tenant's own fuel (a customer sale, a deleted one, another tenant's) is simply not found")
    void notFound() {
        farmExists();
        assertThrows(ResourceNotFoundException.class, () -> service().allocate(TENANT, farmId, user, allocateTo(UUID.randomUUID())));
        assertThrows(ResourceNotFoundException.class, () -> service().allocate(TENANT, UUID.randomUUID(), user, allocateTo(UUID.randomUUID())));
        verifyNothingRecorded();
    }

    @Test
    @DisplayName("a request with no targets is refused")
    void refusesNoTargets() {
        farmExists(); tractor();
        UUID id = UUID.randomUUID();
        OwnDispatch d = dispatch(id, "2026-09-15T08:30:00Z", "500", "22.4");
        when(fuelFacade.findOwnDispatch(eq(TENANT), eq(id))).thenReturn(Optional.of(d));

        assertThrows(IllegalArgumentException.class, () -> service().allocate(TENANT, farmId, user, new AllocateFuelRequest(id, null, List.of())));
        verifyNothingRecorded();
    }
}
