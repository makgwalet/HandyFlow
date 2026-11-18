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
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.Reconciliation;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Forecourt supply reconciliation: inbound litres by source against litres sold. Read-only. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelForecourtSupplyReconciliationServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelTransferRepository transfers;
    @Mock FuelReceiptRepository receipts;
    @Mock FuelPumpRepository pumps;
    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    @Mock FuelShiftRepository shifts;
    @Mock FuelShiftReadingRepository readings;
    @Mock FuelDepotSupplyShareRepository shareRepo;
    @Mock FuelSettingsRepository settingsRepo;
    @Mock FuelPosImportRepository importsRepo;
    FuelForecourtSupplyReconciliationService svc;

    FuelDepot terminal, other, fc;
    FuelTank tTerminal, tOther, tFc, tFc2;

    @BeforeEach void setUp() {
        svc = new FuelForecourtSupplyReconciliationService(transfers, receipts, pumps, tanks, depots, shifts, readings, shareRepo, new FuelShiftReportClock(settingsRepo, importsRepo));
        terminal = depot("Terminal", null);
        other = depot("Other depot", null);
        fc = depot("Forecourt", terminal.getId());
        tTerminal = tank(terminal); tOther = tank(other); tFc = tank(fc); tFc2 = tank(fc);
        when(depots.findAllActive(T)).thenReturn(List.of(terminal, other, fc));
        when(tanks.findAllActive(T)).thenReturn(List.of(tTerminal, tOther, tFc, tFc2));
        FuelPump p = mock(FuelPump.class); var hoisted1 = tFc.getId(); when(p.getTankId()).thenReturn(hoisted1);
        when(pumps.findAllActive(T)).thenReturn(List.of(p));
        when(transfers.findBetween(eq(T), any(), any())).thenReturn(List.of());
        when(receipts.findReceivedBetween(eq(T), any(), any())).thenReturn(List.of());
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of());
    }

    private FuelDepot depot(String name, UUID supplier) {
        FuelDepot d = mock(FuelDepot.class);
        when(d.getId()).thenReturn(UUID.randomUUID()); when(d.getName()).thenReturn(name); when(d.getSupplyingDepotId()).thenReturn(supplier);
        return d;
    }
    private FuelTank tank(FuelDepot d) {
        FuelTank t = mock(FuelTank.class);
        when(t.getId()).thenReturn(UUID.randomUUID()); var hoisted2 = d.getId(); when(t.getDepotId()).thenReturn(hoisted2);
        return t;
    }
    private FuelTransfer transfer(FuelTank from, FuelTank to, String litres) {
        FuelTransfer t = mock(FuelTransfer.class);
        var hoisted3 = from.getId(); when(t.getFromTankId()).thenReturn(hoisted3); var hoisted4 = to.getId(); when(t.getToTankId()).thenReturn(hoisted4); when(t.getLitres()).thenReturn(bd(litres));
        return t;
    }
    private FuelReceipt receipt(FuelTank to, String litres) {
        FuelReceipt r = mock(FuelReceipt.class);
        var hoisted5 = to.getId(); when(r.getTankId()).thenReturn(hoisted5); when(r.getLitresReceived()).thenReturn(bd(litres));
        return r;
    }

    @Test @DisplayName("inbound litres are split by source: supplying depot, other depot, direct supplier; moves inside the depot are ignored")
    void splitsBySource() {
        var hoisted6 = List.of(
                transfer(tTerminal, tFc, "6000"), transfer(tOther, tFc, "1000"), transfer(tFc2, tFc, "500"), transfer(tFc, tOther, "300")); when(transfers.findBetween(eq(T), any(), any())).thenReturn(hoisted6);
        var hoisted7 = List.of(receipt(tFc, "3000"), receipt(tOther, "9999")); when(receipts.findReceivedBetween(eq(T), any(), any())).thenReturn(hoisted7);
        Reconciliation r = svc.report(T, 30, null);
        assertEquals(1, r.forecourts().size());
        var f = r.forecourts().get(0);
        assertEquals("Terminal", f.supplierName());
        assertEquals(0, bd("6000").compareTo(f.fromSupplierDepot()));
        assertEquals(0, bd("1000").compareTo(f.fromOtherDepots()));
        assertEquals(0, bd("3000").compareTo(f.fromSuppliers()));
        assertEquals(0, bd("10000").compareTo(f.totalIn()));
        assertEquals(0, bd("60.0").compareTo(f.supplierDepotSharePct()));
    }

    @Test @DisplayName("litres sold come from closed-shift readings on the forecourt's tanks; unmeasured readings are skipped")
    void soldLitres() {
        FuelShift s = mock(FuelShift.class); when(s.getId()).thenReturn(UUID.randomUUID()); when(s.getClosedAt()).thenReturn(Instant.now().minusSeconds(60));
        when(shifts.findClosedSince(eq(T), any())).thenReturn(List.of(s));
        FuelShiftReading a = mock(FuelShiftReading.class), b = mock(FuelShiftReading.class);
        var hoisted8 = tFc.getId(); when(a.getTankId()).thenReturn(hoisted8); when(a.getLitresSold()).thenReturn(bd("120"));
        var hoisted9 = tFc.getId(); when(b.getTankId()).thenReturn(hoisted9); when(b.getLitresSold()).thenReturn(null);
        when(readings.findByShifts(eq(T), any())).thenReturn(List.of(a, b));
        var f = svc.report(T, 30, null).forecourts().get(0);
        assertEquals(0, bd("120").compareTo(f.litresSold()));
        assertNull(f.supplierDepotSharePct(), "no inbound litres, so no share");
    }

    @Test @DisplayName("a depot limit leaves out forecourts the user may not see")
    void limited() {
        assertTrue(svc.report(T, 30, Set.of(other.getId())).forecourts().isEmpty());
        assertEquals(1, svc.report(T, 30, Set.of(fc.getId())).forecourts().size());
    }

    @Test @DisplayName("with a saved split, transfers from any depot in the split count as from the supplying depots")
    void split() {
        FuelDepotSupplyShare a = mock(FuelDepotSupplyShare.class), b = mock(FuelDepotSupplyShare.class);
        var hoisted10 = fc.getId(); when(a.getDepotId()).thenReturn(hoisted10); var hoisted11 = terminal.getId(); when(a.getSupplyingDepotId()).thenReturn(hoisted11); when(a.getPercentage()).thenReturn(bd("70"));
        var hoisted12 = fc.getId(); when(b.getDepotId()).thenReturn(hoisted12); var hoisted13 = other.getId(); when(b.getSupplyingDepotId()).thenReturn(hoisted13); when(b.getPercentage()).thenReturn(bd("30"));
        when(shareRepo.findAllForTenant(T)).thenReturn(List.of(a, b));
        var hoisted14 = List.of(transfer(tTerminal, tFc, "700"), transfer(tOther, tFc, "300")); when(transfers.findBetween(eq(T), any(), any())).thenReturn(hoisted14);
        var f = svc.report(T, 30, null).forecourts().get(0);
        assertEquals(0, bd("1000").compareTo(f.fromSupplierDepot()));
        assertEquals(0, BigDecimal.ZERO.compareTo(f.fromOtherDepots()));
        assertEquals("Terminal 70%, Other depot 30%", f.supplierName());
    }
}
