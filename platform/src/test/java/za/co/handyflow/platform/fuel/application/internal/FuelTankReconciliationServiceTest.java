package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.DipReading;
import za.co.handyflow.platform.fuel.domain.model.FuelStockMovement;
import za.co.handyflow.platform.fuel.domain.model.FuelStockMovement.Type;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.DipReadingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftReadingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelStockMovementRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.fuel.dto.FuelRetailDtos.TankReconciliation;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** D01: tank-to-pump reconciliation arithmetic and guards. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelTankReconciliationServiceTest {

    @Mock FuelTankRepository tanks;
    @Mock DipReadingRepository dips;
    @Mock FuelStockMovementRepository movements;
    @Mock FuelShiftReadingRepository readings;
    @InjectMocks FuelTankReconciliationService service;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final Instant T0 = Instant.parse("2026-11-01T06:00:00Z"), T1 = Instant.parse("2026-11-08T06:00:00Z");
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    private final FuelTank tank = FuelTank.create(T, "Tank 1", "DIESEL", bd("20000"), null, bd("20"));

    private DipReading dip(Instant at, String litres) { return DipReading.create(T, tank.getId(), at, bd(litres), bd(litres), "x", null); }

    private FuelStockMovement move(Type type, String delta, String source) {
        return FuelStockMovement.of(T, tank.getId(), type, bd(delta), BigDecimal.ZERO, source, UUID.randomUUID(), "r", null);
    }

    @Test @DisplayName("expected = opening dip + every recorded movement; variance is the closing dip minus that")
    void arithmetic() {
        var moved = List.of(move(Type.RECEIPT, "10000", "RECEIPT"), move(Type.DISPATCH, "-4000", "FORECOURT_SHIFT"), move(Type.DISPATCH, "-1000", "DISPATCH"));
        TankReconciliation r = FuelTankReconciliationService.compute(tank, dip(T0, "5000"), dip(T1, "9900"), moved, bd("4000"), null);
        assertEquals(0, bd("10000").compareTo(r.expectedLitres()), "5000 + 10000 - 4000 - 1000");
        assertEquals(0, bd("-100").compareTo(r.varianceLitres()));
        assertEquals(0, bd("5000").compareTo(r.throughputLitres()));
        assertEquals(0, bd("-2.00").compareTo(r.variancePct()), "-100 of 5000 litres out");
        assertEquals(3, r.movements().size());
    }

    @Test @DisplayName("meter litres are compared with the ledger's forecourt sales; a gap shows up as a difference")
    void meterAgainstLedger() {
        var moved = List.of(move(Type.DISPATCH, "-4000", "FORECOURT_SHIFT"), move(Type.REVERSAL, "500", "FORECOURT_SHIFT"));
        TankReconciliation r = FuelTankReconciliationService.compute(tank, dip(T0, "9000"), dip(T1, "5500"), moved, bd("3500"), null);
        assertEquals(0, bd("3500").compareTo(r.ledgerForecourtLitres()), "4000 sold less 500 reversed");
        assertEquals(0, BigDecimal.ZERO.compareTo(r.meterVsLedgerLitres()));
        TankReconciliation off = FuelTankReconciliationService.compute(tank, dip(T0, "9000"), dip(T1, "5500"), moved, bd("3600"), null);
        assertEquals(0, bd("100").compareTo(off.meterVsLedgerLitres()));
    }

    @Test @DisplayName("no tolerance given means nothing is flagged; with one, only a larger percentage is")
    void tolerance() {
        var moved = List.of(move(Type.DISPATCH, "-5000", "FORECOURT_SHIFT"));
        assertFalse(FuelTankReconciliationService.compute(tank, dip(T0, "9000"), dip(T1, "3900"), moved, bd("5000"), null).outsideTolerance());
        var r1 = FuelTankReconciliationService.compute(tank, dip(T0, "9000"), dip(T1, "3900"), moved, bd("5000"), bd("1"));
        assertTrue(r1.outsideTolerance(), "-100 of 5000 is -2%, beyond 1%");
        var r2 = FuelTankReconciliationService.compute(tank, dip(T0, "9000"), dip(T1, "3900"), moved, bd("5000"), bd("2"));
        assertFalse(r2.outsideTolerance(), "exactly at the limit is not beyond it");
    }

    @Test @DisplayName("no outflow means no percentage and nothing flagged")
    void noThroughput() {
        TankReconciliation r = FuelTankReconciliationService.compute(tank, dip(T0, "5000"), dip(T1, "5000"), List.of(), null, bd("1"));
        assertNull(r.variancePct());
        assertFalse(r.outsideTolerance());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.meterLitres()));
    }

    @Test @DisplayName("fewer than two dips is not an error: it says what is missing")
    void needsTwoDips() {
        when(tanks.findActiveById(T, tank.getId())).thenReturn(Optional.of(tank));
        var one = List.of(dip(T0, "5000"));
        when(dips.findByTankAndReadAtBetween(eq(T), eq(tank.getId()), any(), any())).thenReturn(one);
        TankReconciliation r = service.reconcile(T, tank.getId(), T0, T1, null);
        assertFalse(r.ready());
        assertTrue(r.message().contains("two dip readings"));
    }

    @Test @DisplayName("uses the first and last dip in the window and the movements between them")
    void usesFirstAndLast() {
        when(tanks.findActiveById(T, tank.getId())).thenReturn(Optional.of(tank));
        var three = List.of(dip(T0, "5000"), dip(T0.plusSeconds(86400), "4000"), dip(T1, "3000"));
        when(dips.findByTankAndReadAtBetween(eq(T), eq(tank.getId()), any(), any())).thenReturn(three);
        var moved = List.of(move(Type.DISPATCH, "-2000", "FORECOURT_SHIFT"));
        when(movements.findByTankBetween(T, tank.getId(), T0, T1)).thenReturn(moved);
        when(readings.sumMeterLitresForTank(T, tank.getId(), T0, T1)).thenReturn(bd("2000"));
        TankReconciliation r = service.reconcile(T, tank.getId(), T0, T1, null);
        assertTrue(r.ready());
        assertEquals(0, bd("3000").compareTo(r.expectedLitres()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.varianceLitres()));
    }

    @Test @DisplayName("another tenant's tank is not found; a backwards window and a silly tolerance are refused")
    void guards() {
        when(tanks.findActiveById(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.reconcile(T, UUID.randomUUID(), T0, T1, null));
        when(tanks.findActiveById(T, tank.getId())).thenReturn(Optional.of(tank));
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(T, tank.getId(), T1, T0, null));
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(T, tank.getId(), T0, T1, bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(T, tank.getId(), T0, T1, bd("101")));
    }
}
