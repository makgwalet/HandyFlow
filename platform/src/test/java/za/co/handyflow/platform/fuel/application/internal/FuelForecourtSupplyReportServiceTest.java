package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.Report;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Phase 8d: forecourt sales grouped by supplying depot. Read-only. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelForecourtSupplyReportServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelShiftRepository shifts;
    @Mock FuelShiftReadingRepository readings;
    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    FuelForecourtSupplyReportService svc;

    @BeforeEach void setUp() { svc = new FuelForecourtSupplyReportService(shifts, readings, tanks, depots); }

    private FuelDepot depot(String name, UUID supplier) {
        FuelDepot d = mock(FuelDepot.class);
        when(d.getId()).thenReturn(UUID.randomUUID()); when(d.getName()).thenReturn(name); when(d.getSupplyingDepotId()).thenReturn(supplier);
        return d;
    }
    private FuelTank tank(FuelDepot d) {
        FuelTank t = mock(FuelTank.class);
        UUID depotId = d == null ? null : d.getId();
        when(t.getId()).thenReturn(UUID.randomUUID()); when(t.getDepotId()).thenReturn(depotId);
        return t;
    }
    private FuelShift shift(UUID id) {
        FuelShift s = mock(FuelShift.class);
        when(s.getId()).thenReturn(id); when(s.getClosedAt()).thenReturn(Instant.now().minusSeconds(3600));
        return s;
    }
    private FuelShiftReading reading(UUID shiftId, FuelTank t, String litres, String sales) {
        FuelShiftReading r = mock(FuelShiftReading.class);
        UUID tankId = t.getId();
        when(r.getShiftId()).thenReturn(shiftId); when(r.getTankId()).thenReturn(tankId);
        when(r.getLitresSold()).thenReturn(litres == null ? null : bd(litres)); when(r.getSalesValue()).thenReturn(sales == null ? null : bd(sales));
        return r;
    }

    @Test @DisplayName("sales are grouped under the depot that supplies each forecourt, and the totals add up")
    void groups() {
        FuelDepot terminal = depot("Terminal", null);
        FuelDepot fc1 = depot("Forecourt One", terminal.getId()), fc2 = depot("Forecourt Two", terminal.getId());
        FuelTank t1 = tank(fc1), t2 = tank(fc2);
        FuelShift s1 = shift(UUID.randomUUID()), s2 = shift(UUID.randomUUID());
        when(depots.findAllActive(T)).thenReturn(List.of(terminal, fc1, fc2));
        when(tanks.findAllActive(T)).thenReturn(List.of(t1, t2));
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of(s1, s2));
        var rd0 = reading(s1.getId(), t1, "100", "2300");
        var rd1 = reading(s2.getId(), t2, "50", "1150");
        when(readings.findByShifts(eq(T), any())).thenReturn(List.of(rd0, rd1));
        Report r = svc.report(T, 30, true);
        assertEquals(1, r.suppliers().size());
        var sup = r.suppliers().get(0);
        assertEquals("Terminal", sup.name());
        assertFalse(sup.unassigned());
        assertEquals(2, sup.forecourts().size());
        assertEquals("Forecourt One", sup.forecourts().get(0).name()); // busiest first
        assertEquals(0, bd("150").compareTo(r.totalLitres()));
        assertEquals(0, bd("3450").compareTo(r.totalSales()));
    }

    @Test @DisplayName("a forecourt with no supplier, and a tank in no depot, go in the no-supplier bucket so nothing drops out")
    void noSupplierBucket() {
        FuelDepot fc = depot("Lonely Forecourt", null);
        FuelTank t = tank(fc), loose = tank(null);
        FuelShift s = shift(UUID.randomUUID());
        when(depots.findAllActive(T)).thenReturn(List.of(fc));
        when(tanks.findAllActive(T)).thenReturn(List.of(t, loose));
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of(s));
        var rd0 = reading(s.getId(), t, "10", "230");
        var rd1 = reading(s.getId(), loose, "5", "115");
        when(readings.findByShifts(eq(T), any())).thenReturn(List.of(rd0, rd1));
        Report r = svc.report(T, 30, true);
        assertEquals(1, r.suppliers().size());
        assertTrue(r.suppliers().get(0).unassigned());
        assertEquals("No supplier set", r.suppliers().get(0).name());
        assertEquals(2, r.suppliers().get(0).forecourts().size());
        assertEquals(0, bd("15").compareTo(r.totalLitres()));
    }

    @Test @DisplayName("money is hidden unless the reader may see margin")
    void moneyHidden() {
        FuelDepot fc = depot("F", null);
        FuelTank t = tank(fc);
        FuelShift s = shift(UUID.randomUUID());
        when(depots.findAllActive(T)).thenReturn(List.of(fc)); when(tanks.findAllActive(T)).thenReturn(List.of(t));
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of(s));
        var rd0 = reading(s.getId(), t, "10", "230");
        when(readings.findByShifts(eq(T), any())).thenReturn(List.of(rd0));
        Report r = svc.report(T, 30, false);
        assertNull(r.totalSales());
        assertNull(r.suppliers().get(0).salesValue());
        assertNull(r.suppliers().get(0).forecourts().get(0).salesValue());
        assertEquals(0, bd("10").compareTo(r.totalLitres()));
    }

    @Test @DisplayName("a reading with no litres is counted as left out, not guessed")
    void leftOut() {
        FuelDepot fc = depot("F", null);
        FuelTank t = tank(fc);
        FuelShift s = shift(UUID.randomUUID());
        when(depots.findAllActive(T)).thenReturn(List.of(fc)); when(tanks.findAllActive(T)).thenReturn(List.of(t));
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of(s));
        var rd0 = reading(s.getId(), t, null, null);
        when(readings.findByShifts(eq(T), any())).thenReturn(List.of(rd0));
        Report r = svc.report(T, 30, true);
        assertEquals(1, r.readingsLeftOut());
        assertTrue(r.suppliers().isEmpty());
    }

    @Test @DisplayName("no closed shifts gives an empty report and does not load readings")
    void empty() {
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of());
        Report r = svc.report(T, 30, true);
        assertTrue(r.suppliers().isEmpty());
        verify(readings, never()).findByShifts(any(), any());
    }

    @Test @DisplayName("shifts closed after the period ends are not counted, and days is clamped to 1..366")
    void periodBounds() {
        FuelShift future = mock(FuelShift.class);
        when(future.getId()).thenReturn(UUID.randomUUID()); when(future.getClosedAt()).thenReturn(Instant.now().plusSeconds(5 * 86400L));
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of(future));
        assertTrue(svc.report(T, 0, true).suppliers().isEmpty());
        assertEquals(1, java.time.temporal.ChronoUnit.DAYS.between(svc.report(T, 0, true).from(), svc.report(T, 0, true).to()) + 1);
        verify(readings, never()).findByShifts(any(), any());
    }

    @Test @DisplayName("a supplier that has been deactivated falls back to no supplier")
    void deactivatedSupplier() {
        UUID gone = UUID.randomUUID();
        FuelDepot fc = depot("F", gone);
        FuelTank t = tank(fc);
        FuelShift s = shift(UUID.randomUUID());
        when(depots.findAllActive(T)).thenReturn(List.of(fc)); when(tanks.findAllActive(T)).thenReturn(List.of(t));
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of(s));
        var rd0 = reading(s.getId(), t, "10", "230");
        when(readings.findByShifts(eq(T), any())).thenReturn(List.of(rd0));
        assertTrue(svc.report(T, 30, true).suppliers().get(0).unassigned());
    }
}
