package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Depot limits applied to the supply report, availability, ledger, transfers, reservations and forecourt shifts. No limit (null) changes nothing. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelDepotLimitFilteringTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }
    static final UUID DEPOT_MINE = UUID.randomUUID(), DEPOT_OTHER = UUID.randomUUID();

    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    @Mock FuelStockMovementRepository movements;
    @Mock FuelTransferRepository transfers;
    @Mock FuelReservationRepository reservations;
    @Mock FuelStockLedger ledger;
    @Mock za.co.handyflow.platform.crm.CrmFacade crm;
    @Mock FuelShiftRepository shifts;
    @Mock FuelShiftReadingRepository readings;
    @Mock FuelPumpRepository pumps;
    @Mock FuelCashControlService cash;
    @Mock ShiftNumberGenerator numbers;

    FuelStockService stock;
    FuelForecourtService forecourt;
    FuelTank mine, theirs, loose;

    @BeforeEach
    void setUp() {
        stock = new FuelStockService(tanks, depots, movements, transfers, reservations, ledger, crm);
        mine = FuelTank.create(T, "Mine", "DIESEL", bd("10000"), "Yard", null); mine.place(DEPOT_MINE, BigDecimal.ZERO);
        theirs = FuelTank.create(T, "Theirs", "DIESEL", bd("10000"), "Yard", null); theirs.place(DEPOT_OTHER, BigDecimal.ZERO);
        loose = FuelTank.create(T, "Loose", "DIESEL", bd("10000"), "Yard", null);
        mine.addStock(bd("1000"), bd("20")); theirs.addStock(bd("2000"), bd("20")); loose.addStock(bd("3000"), bd("20"));
        when(tanks.findAllActive(T)).thenReturn(List.of(mine, theirs, loose));
        when(depots.findAllActive(T)).thenReturn(List.of());
        when(reservations.sumLiveByTank(eq(T), any())).thenReturn(List.of());
        when(movements.sumForTank(eq(T), any())).thenReturn(BigDecimal.ZERO);
    }

    @Test @DisplayName("availability lists only tanks in the user's depots, and a tank in no depot is left out for a limited user")
    void availability() {
        var all = stock.getAvailability(T);
        assertEquals(3, all.tanks().size());
        var limited = stock.getAvailability(T, Set.of(DEPOT_MINE));
        assertEquals(1, limited.tanks().size());
        assertEquals("Mine", limited.tanks().get(0).tankName());
        assertTrue(limited.depots().stream().allMatch(d -> d.depotId().equals(DEPOT_MINE)));
    }

    @Test @DisplayName("the ledger, transfers and reservations ask only for the user's tanks, and an empty set returns nothing without a query")
    void listsUseTheLimit() {
        var pageable = PageRequest.of(0, 50);
        Set<UUID> ids = Set.of(mine.getId());
        when(movements.findByTanks(T, ids, pageable)).thenReturn(new PageImpl<>(List.of()));
        when(transfers.findInvolving(T, ids, pageable)).thenReturn(new PageImpl<>(List.of()));
        when(reservations.findForTanks(T, ids, pageable)).thenReturn(new PageImpl<>(List.of()));
        stock.getLedger(T, null, ids, pageable); stock.getTransfers(T, ids, pageable); stock.getReservations(T, ids, pageable);
        verify(movements).findByTanks(T, ids, pageable); verify(transfers).findInvolving(T, ids, pageable); verify(reservations).findForTanks(T, ids, pageable);
        verify(movements, never()).findAllForTenant(any(), any()); verify(transfers, never()).findAllForTenant(any(), any()); verify(reservations, never()).findAllForTenant(any(), any());

        assertTrue(stock.getLedger(T, null, Set.of(), pageable).isEmpty());
        assertTrue(stock.getTransfers(T, Set.of(), pageable).isEmpty());
        assertTrue(stock.getReservations(T, Set.of(), pageable).isEmpty());
    }

    @Test @DisplayName("with no limit the lists are the whole company's, as before")
    void noLimit() {
        var pageable = PageRequest.of(0, 50);
        when(movements.findAllForTenant(T, pageable)).thenReturn(new PageImpl<>(List.of()));
        when(transfers.findAllForTenant(T, pageable)).thenReturn(new PageImpl<>(List.of()));
        when(reservations.findAllForTenant(T, pageable)).thenReturn(new PageImpl<>(List.of()));
        stock.getLedger(T, null, pageable); stock.getTransfers(T, pageable); stock.getReservations(T, pageable);
        verify(movements).findAllForTenant(T, pageable); verify(transfers).findAllForTenant(T, pageable); verify(reservations).findAllForTenant(T, pageable);
    }

    // ── supply report ────────────────────────────────────────────────────────

    @Test @DisplayName("the supply report counts only the user's forecourts, so totals match what they may see")
    void supplyReport() {
        var svc = new FuelForecourtSupplyReportService(shifts, readings, tanks, depots);
        FuelDepot terminal = depot(UUID.randomUUID(), "Terminal", null), fcMine = depot(DEPOT_MINE, "Mine FC", terminal.getId()), fcOther = depot(DEPOT_OTHER, "Other FC", terminal.getId());
        when(depots.findAllActive(T)).thenReturn(List.of(terminal, fcMine, fcOther));
        FuelShift s = shift(UUID.randomUUID());
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of(s));
        when(readings.findByShifts(eq(T), any())).thenReturn(List.of(reading(s.getId(), mine, "100", "2300"), reading(s.getId(), theirs, "40", "920"), reading(s.getId(), loose, "7", "161")));

        var all = svc.report(T, 30, true);
        assertEquals(0, bd("147").compareTo(all.totalLitres()));
        var limited = svc.report(T, 30, true, Set.of(DEPOT_MINE));
        assertEquals(0, bd("100").compareTo(limited.totalLitres()));
        assertEquals(0, bd("2300").compareTo(limited.totalSales()));
        assertEquals(1, limited.suppliers().size());
        assertEquals(List.of("Mine FC"), limited.suppliers().get(0).forecourts().stream().map(f -> f.name()).toList());
    }

    // ── forecourt shifts ─────────────────────────────────────────────────────

    private FuelForecourtService forecourt() {
        return new FuelForecourtService(pumps, shifts, readings, tanks, ledger, numbers, cash);
    }

    private FuelShift shift(UUID id) {
        FuelShift s = mock(FuelShift.class);
        when(s.getId()).thenReturn(id); when(s.getClosedAt()).thenReturn(Instant.now().minusSeconds(60));
        when(s.getLitresSold()).thenReturn(bd("10")); when(s.getExpectedAmount()).thenReturn(bd("230")); when(s.getVarianceAmount()).thenReturn(BigDecimal.ZERO);
        return s;
    }
    private FuelShiftReading reading(UUID shiftId, FuelTank t, String litres, String sales) {
        FuelShiftReading r = mock(FuelShiftReading.class);
        when(r.getShiftId()).thenReturn(shiftId); when(r.getTankId()).thenReturn(t.getId()); when(r.getFuelType()).thenReturn("DIESEL");
        when(r.getLitresSold()).thenReturn(bd(litres)); when(r.getSalesValue()).thenReturn(bd(sales));
        return r;
    }
    private FuelDepot depot(UUID id, String name, UUID supplier) {
        FuelDepot d = mock(FuelDepot.class);
        when(d.getId()).thenReturn(id); when(d.getName()).thenReturn(name); when(d.getSupplyingDepotId()).thenReturn(supplier);
        return d;
    }

    @Test @DisplayName("a limited user sees a shift only when all its pumps are on their tanks; the summary counts the same shifts")
    void shiftsAndSummary() {
        forecourt = forecourt();
        FuelShift whole = shift(UUID.randomUUID()), mixed = shift(UUID.randomUUID());
        when(shifts.findRecent(eq(T), any())).thenReturn(List.of(whole, mixed));
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of(whole, mixed));
        var rdWhole = reading(whole.getId(), mine, "10", "230");
        var rdMixA = reading(mixed.getId(), mine, "5", "115");
        var rdMixB = reading(mixed.getId(), theirs, "5", "115");
        when(readings.findByShifts(eq(T), any())).thenReturn(List.of(rdWhole, rdMixA, rdMixB));
        Set<UUID> allowed = Set.of(mine.getId());

        assertEquals(2, forecourt.listShifts(T, 50).size());
        var seen = forecourt.listShifts(T, 50, allowed);
        assertEquals(1, seen.size()); assertEquals(whole.getId(), seen.get(0).id());
        assertEquals(2, forecourt.summary(T, 7).shifts());
        assertEquals(1, forecourt.summary(T, 7, allowed).shifts());
    }

    @Test @DisplayName("opening one shift that touches another depot's tank is refused with 403 for a limited user")
    void oneShift() {
        forecourt = forecourt();
        FuelShift mixed = shift(UUID.randomUUID());
        when(shifts.findByTenantAndId(T, mixed.getId())).thenReturn(Optional.of(mixed));
        when(readings.findByShift(T, mixed.getId())).thenReturn(List.of(reading(mixed.getId(), mine, "5", "115"), reading(mixed.getId(), theirs, "5", "115")));
        assertEquals(mixed.getId(), forecourt.getShift(T, mixed.getId()).id());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> forecourt.getShift(T, mixed.getId(), Set.of(mine.getId())));
        assertEquals("FUEL_SCOPE_DEPOT", e.getErrorCode());
    }

    @Test @DisplayName("a limited user gets a full page of their own shifts by reading further back, not a short page")
    void shiftsPagesPastOthers() {
        forecourt = forecourt();
        FuelShift a = shift(UUID.randomUUID()), b = shift(UUID.randomUUID()), c = shift(UUID.randomUUID());
        // page 0 holds a (other depot) and b (other depot); page 1 holds c (mine). Asking for 2 per page.
        var rA = reading(a.getId(), theirs, "5", "115"); var rB = reading(b.getId(), theirs, "5", "115"); var rC = reading(c.getId(), mine, "5", "115");
        when(shifts.findRecent(eq(T), org.mockito.ArgumentMatchers.argThat(p -> p != null && p.getPageNumber() == 0))).thenReturn(List.of(a, b));
        when(shifts.findRecent(eq(T), org.mockito.ArgumentMatchers.argThat(p -> p != null && p.getPageNumber() == 1))).thenReturn(List.of(c));
        when(readings.findByShifts(eq(T), any())).thenReturn(List.of(rA, rB, rC));
        var seen = forecourt.listShifts(T, 2, Set.of(mine.getId()));
        assertEquals(1, seen.size()); assertEquals(c.getId(), seen.get(0).id());
        verify(shifts, times(2)).findRecent(eq(T), any());
    }
}
