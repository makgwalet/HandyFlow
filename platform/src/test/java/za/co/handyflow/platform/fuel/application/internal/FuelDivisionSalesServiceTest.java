package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.DivisionSalesDtos.Report;
import za.co.handyflow.platform.fuel.dto.DivisionSalesDtos.Row;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Sales by division: what counts in each division, the comparison period, tank scope and who sees money. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelDivisionSalesServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelDispatchRepository dispatches;
    @Mock FuelShiftRepository shifts;
    @Mock FuelShiftReadingRepository readings;
    @Mock FuelTruckStopStayRepository stays;
    @Mock FuelShiftReportClock clock;
    @Mock FuelDivisionGuard divisions;
    @Mock FuelScopeService scopes;
    @InjectMocks FuelDivisionSalesService service;

    final UUID tankA = UUID.randomUUID(), tankB = UUID.randomUUID();
    final Instant recent = Instant.now().minus(2, ChronoUnit.DAYS);
    final Instant earlier = Instant.now().minus(40, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        when(clock.basis(T)).thenReturn(FuelShiftReportClock.DEFAULT_BASIS);
        when(divisions.activeDivisions(T)).thenReturn(EnumSet.allOf(FuelDivision.class));
        when(scopes.scopeOf(T, USER)).thenReturn(FuelScopeService.Scope.UNRESTRICTED);
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(List.of());
        when(dispatches.findByTenantAndDispatchedAtBetween(any(), any(), any())).thenReturn(List.of());
        when(shifts.findClosedSince(any(), any())).thenReturn(List.of());
        when(stays.findCheckedOutBetween(any(), any(), any())).thenReturn(List.of());
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelDelivery load(UUID tank, String litres, String total, Instant at, boolean reversed) {
        FuelDelivery d = mock(FuelDelivery.class);
        when(d.getTankId()).thenReturn(tank); when(d.getLitresDelivered()).thenReturn(bd(litres));
        when(d.getTotalAmount()).thenReturn(total == null ? null : bd(total)); when(d.getPricePerLitre()).thenReturn(bd("20"));
        when(d.getDeliveredAt()).thenReturn(at); when(d.getReversedAt()).thenReturn(reversed ? Instant.now() : null);
        return d;
    }

    private Row row(Report r, FuelDivision d) { return r.divisions().stream().filter(x -> x.division().equals(d.name())).findFirst().orElse(null); }

    @Test @DisplayName("loads count by invoiced amount, reversed loads do not, and the earlier period is kept for comparison")
    void distribution() {
        var hoisted1 = List.of(
                load(tankA, "1000", "21000.00", recent, false), load(tankA, "500", null, recent, false),
                load(tankA, "900", "9000", recent, true), load(tankA, "300", "6300", earlier, false)); when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(hoisted1);
        Row r = row(service.report(T, USER, 30, true, null), FuelDivision.DISTRIBUTION);
        assertEquals(2, r.count());
        assertEquals(0, bd("1500").compareTo(r.litres()));
        assertEquals(0, bd("31000").compareTo(r.revenue()));
        assertEquals(1, r.previousCount());
        assertEquals(0, bd("6300").compareTo(r.previousRevenue()));
        assertEquals(30, r.dailyRevenue().size());
    }

    @Test @DisplayName("only the tanks the person may see are counted")
    void tankScope() {
        var hoisted2 = List.of(
                load(tankA, "1000", "21000", recent, false), load(tankB, "1000", "25000", recent, false)); when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(hoisted2);
        Row r = row(service.report(T, USER, 30, true, Set.of(tankA)), FuelDivision.DISTRIBUTION);
        assertEquals(1, r.count());
        assertEquals(0, bd("21000").compareTo(r.revenue()));
    }

    @Test @DisplayName("money is hidden without the margin permission, litres and counts stay")
    void noMoney() {
        var hoisted3 = List.of(load(tankA, "1000", "21000", recent, false)); when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(hoisted3);
        Report rep = service.report(T, USER, 30, false, null);
        Row r = row(rep, FuelDivision.DISTRIBUTION);
        assertFalse(rep.moneyShown());
        assertNull(r.revenue()); assertNull(r.dailyRevenue()); assertNull(r.previousRevenue()); assertNull(rep.total().revenue());
        assertEquals(1, r.count());
        assertEquals(0, bd("1000").compareTo(r.litres()));
    }

    @Test @DisplayName("a shift is counted once however many pumps it has")
    void retailShiftCountedOnce() {
        FuelShift s = mock(FuelShift.class);
        UUID id = UUID.randomUUID();
        when(s.getId()).thenReturn(id); when(s.getClosedAt()).thenReturn(recent);
        when(shifts.findClosedSince(any(), any())).thenReturn(List.of(s));
        FuelShiftReading a = mock(FuelShiftReading.class), b = mock(FuelShiftReading.class);
        for (var x : List.of(a, b)) { when(x.getShiftId()).thenReturn(id); when(x.getLitresSold()).thenReturn(bd("100")); when(x.getSalesValue()).thenReturn(bd("2000")); }
        when(a.getTankId()).thenReturn(tankA); when(b.getTankId()).thenReturn(tankA);
        when(readings.findByShifts(any(), any())).thenReturn(List.of(a, b));
        Row r = row(service.report(T, USER, 30, true, null), FuelDivision.RETAIL_FORECOURT);
        assertEquals(1, r.count());
        assertEquals(0, bd("200").compareTo(r.litres()));
        assertEquals(0, bd("4000").compareTo(r.revenue()));
    }

    @Test @DisplayName("only customer-billed dispatches are sales, internal issues and transfers are not")
    void wholesale() {
        FuelDispatch sold = mock(FuelDispatch.class), internal = mock(FuelDispatch.class), moved = mock(FuelDispatch.class);
        for (var d : List.of(sold, internal, moved)) { when(d.getTankId()).thenReturn(tankA); when(d.getLitresDispensed()).thenReturn(bd("1000")); when(d.getDispatchedAt()).thenReturn(recent); }
        when(sold.getPricePerLitre()).thenReturn(bd("22"));
        when(internal.getPricePerLitre()).thenReturn(null);
        when(moved.getPricePerLitre()).thenReturn(bd("22")); when(moved.isDeliveredByTransfer()).thenReturn(true);
        when(dispatches.findByTenantAndDispatchedAtBetween(any(), any(), any())).thenReturn(List.of(sold, internal, moved));
        Row r = row(service.report(T, USER, 30, true, null), FuelDivision.WHOLESALE_SUPPLY);
        assertEquals(1, r.count());
        assertEquals(0, bd("22000").compareTo(r.revenue()));
    }

    @Test @DisplayName("truck stop takes checked-out stays at nightly rate, with no litres")
    void truckStop() {
        FuelTruckStopStay s = mock(FuelTruckStopStay.class);
        when(s.getCheckedOutAt()).thenReturn(recent); when(s.amount()).thenReturn(bd("450.00"));
        when(stays.findCheckedOutBetween(any(), any(), any())).thenReturn(List.of(s));
        Row r = row(service.report(T, USER, 30, true, null), FuelDivision.TRUCK_STOP);
        assertEquals(1, r.count());
        assertEquals(0, bd("450").compareTo(r.revenue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.litres()));
    }

    @Test @DisplayName("divisions the company does not run, or the person is not scoped to, do not appear")
    void divisionsFiltered() {
        when(divisions.activeDivisions(T)).thenReturn(EnumSet.of(FuelDivision.DISTRIBUTION, FuelDivision.TRUCK_STOP));
        var hoisted4 = new FuelScopeService.Scope(Set.of(FuelDivision.TRUCK_STOP), Set.of()); when(scopes.scopeOf(T, USER)).thenReturn(hoisted4);
        Report rep = service.report(T, USER, 30, true, null);
        assertEquals(List.of("TRUCK_STOP"), rep.divisions().stream().map(Row::division).toList());
    }

    @Test @DisplayName("the total adds the divisions and the period length is limited")
    void totalAndSpan() {
        var hoisted5 = List.of(load(tankA, "1000", "21000", recent, false)); when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(hoisted5);
        FuelTruckStopStay s = mock(FuelTruckStopStay.class);
        when(s.getCheckedOutAt()).thenReturn(recent); when(s.amount()).thenReturn(bd("450"));
        when(stays.findCheckedOutBetween(any(), any(), any())).thenReturn(List.of(s));
        Report rep = service.report(T, USER, 9999, true, null);
        assertEquals(0, bd("21450").compareTo(rep.total().revenue()));
        assertEquals(366, rep.total().dailyRevenue().size());
        assertEquals(2, rep.total().count());
    }
}
