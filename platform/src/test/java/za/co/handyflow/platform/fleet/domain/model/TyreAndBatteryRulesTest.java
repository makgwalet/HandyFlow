package za.co.handyflow.platform.fleet.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TyreAndBatteryRulesTest {
    TenantId tenant = TenantId.generate();
    UUID vehicle = UUID.randomUUID();
    Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    static BigDecimal d(String v) { return new BigDecimal(v); }

    Tyre tyre() { return Tyre.create(tenant, "T-1", "Bridgestone", "R268", "315/80R22.5", null, null, null, d("16.0"), null); }
    Battery battery(int volts, LocalDate warranty) {
        return Battery.create(tenant, "B-1", "Exide", "E100", volts, 100, null, LocalDate.of(2026, 1, 1), null, warranty, null);
    }

    // ── Tread classification ──
    @Test void treadIsClassifiedAgainstTheLegalMinimumAndTheReplaceSoonLine() {
        assertEquals(TreadStatus.NO_READING, Tyre.classify(null));
        assertEquals(TreadStatus.REPLACE_NOW, Tyre.classify(d("1.0")));
        assertEquals(TreadStatus.REPLACE_NOW, Tyre.classify(d("0.5")));
        assertEquals(TreadStatus.REPLACE_SOON, Tyre.classify(d("1.1")));
        assertEquals(TreadStatus.REPLACE_SOON, Tyre.classify(d("2.9")));
        assertEquals(TreadStatus.OK, Tyre.classify(d("3.0")));
    }

    @Test void aNewTyreStartsInStockWithItsFirstReadingAsNewTread() {
        Tyre t = tyre();
        assertEquals(TyreStatus.IN_STOCK, t.getStatus());
        assertEquals(0, t.getLastTreadDepthMm().compareTo(d("16.0")));
        assertEquals(TreadStatus.OK, t.treadStatus());
    }

    // ── Fitting and kilometres ──
    @Test void kilometresRunOnEachFitmentAreBanked() {
        Tyre t = tyre();
        t.fit(vehicle, "FL", 10_000, t0);
        assertEquals(5_000, t.kmInService(15_000));
        t.remove(18_000, TyreStatus.IN_STOCK, t0.plusSeconds(60), null);
        assertEquals(8_000, t.kmInService(99_999)); // not fitted: the current odometer no longer counts
        t.fit(vehicle, "FR", 18_000, t0);
        assertEquals(10_000, t.kmInService(20_000));
    }

    @Test void aTyreCannotBeFittedTwiceOrRemovedBelowItsFittingOdometer() {
        Tyre t = tyre();
        t.fit(vehicle, "FL", 10_000, t0);
        assertThrows(IllegalStateException.class, () -> t.fit(vehicle, "FR", 10_000, t0));
        assertThrows(IllegalArgumentException.class, () -> t.remove(9_999, TyreStatus.IN_STOCK, t0, null));
        assertThrows(IllegalArgumentException.class, () -> t.remove(11_000, TyreStatus.FITTED, t0, null));
    }

    @Test void scrappingNeedsAReasonAndAFittedTyreMustComeOffFirst() {
        Tyre t = tyre();
        t.fit(vehicle, "FL", 0, t0);
        assertThrows(IllegalStateException.class, () -> t.scrap("worn", t0));
        assertThrows(IllegalArgumentException.class, () -> t.remove(100, TyreStatus.SCRAPPED, t0, null));
        t.remove(100, TyreStatus.SCRAPPED, t0, "Sidewall cut");
        assertEquals(TyreStatus.SCRAPPED, t.getStatus());
        assertThrows(IllegalStateException.class, () -> t.recordMeasurement(d("5"), t0));
    }

    @Test void rotationBanksKilometresAndMovesThePosition() {
        Tyre t = tyre();
        t.fit(vehicle, "FL", 1_000, t0);
        t.rotate("RL", 5_000, t0.plusSeconds(1));
        assertEquals("RL", t.getCurrentPosition());
        assertEquals(4_000, t.getAccumulatedKm());
        assertThrows(IllegalArgumentException.class, () -> t.rotate("RR", 4_999, t0));
    }

    @Test void treadCannotGrowButAMeasurementCanFallAndRetreadRestoresIt() {
        Tyre t = tyre();
        t.recordMeasurement(d("10.0"), t0);
        assertThrows(IllegalArgumentException.class, () -> t.recordMeasurement(d("14.0"), t0));
        t.recordMeasurement(d("9.5"), t0);
        t.fit(vehicle, "FL", 0, t0);
        t.remove(100, TyreStatus.AWAITING_RETREAD, t0, null);
        t.completeRetread(d("14.0"));
        assertEquals(1, t.getRetreadCount());
        assertEquals(TyreStatus.IN_STOCK, t.getStatus());
        assertEquals(0, t.getLastTreadDepthMm().compareTo(d("14.0")));
        assertThrows(IllegalStateException.class, () -> t.completeRetread(d("14.0")));
    }

    // ── Batteries ──
    @Test void voltageIsJudgedAgainstA12VReferenceSoA24VBatteryNeedsDouble() {
        assertEquals(BatteryTestResult.GOOD, BatteryTestResult.classify(d("12.4"), 12));
        assertEquals(BatteryTestResult.WEAK, BatteryTestResult.classify(d("12.39"), 12));
        assertEquals(BatteryTestResult.WEAK, BatteryTestResult.classify(d("12.0"), 12));
        assertEquals(BatteryTestResult.FAILED, BatteryTestResult.classify(d("11.99"), 12));
        assertEquals(BatteryTestResult.GOOD, BatteryTestResult.classify(d("24.8"), 24));
        assertEquals(BatteryTestResult.WEAK, BatteryTestResult.classify(d("24.0"), 24));
        assertEquals(BatteryTestResult.FAILED, BatteryTestResult.classify(d("23.9"), 24));
        assertEquals(BatteryTestResult.FAILED, BatteryTestResult.classify(d("12.4"), 24)); // a 12 V reading on a 24 V battery is flat
    }

    @Test void onlyTwelveAndTwentyFourVoltBatteriesExist() {
        assertThrows(IllegalArgumentException.class, () -> battery(6, null));
        assertThrows(IllegalArgumentException.class, () -> battery(48, null));
        assertDoesNotThrow(() -> battery(12, null));
        assertDoesNotThrow(() -> battery(24, null));
    }

    @Test void aWarrantyCannotEndBeforeThePurchase() {
        assertThrows(IllegalArgumentException.class, () -> battery(12, LocalDate.of(2025, 12, 31)));
    }

    @Test void warrantyCoversTheExpiryDateItself() {
        Battery b = battery(12, LocalDate.of(2027, 6, 30));
        assertTrue(b.isInWarranty(LocalDate.of(2027, 6, 30)));
        assertFalse(b.isInWarranty(LocalDate.of(2027, 7, 1)));
        assertFalse(battery(12, null).isInWarranty(LocalDate.of(2026, 1, 2)));
    }

    @Test void daysInstalledAreBankedOnRemovalAndCountedWhileInstalled() {
        Battery b = battery(12, null);
        b.install(vehicle, "MAIN", 1_000, t0);
        assertEquals(30, b.totalDaysInService(t0.plusSeconds(30L * 86_400)));
        b.remove(2_000, BatteryStatus.IN_STOCK, t0.plusSeconds(10L * 86_400), null);
        assertEquals(10, b.getDaysInService());
        assertEquals(10, b.totalDaysInService(t0.plusSeconds(500L * 86_400))); // not installed: the clock stopped
    }

    @Test void aBatteryCannotBeRemovedBeforeItWasInstalledOrScrappedWithoutAReason() {
        Battery b = battery(12, null);
        b.install(vehicle, "MAIN", 1_000, t0);
        assertThrows(IllegalArgumentException.class, () -> b.remove(999, BatteryStatus.IN_STOCK, t0.plusSeconds(1), null));
        assertThrows(IllegalArgumentException.class, () -> b.remove(1_500, BatteryStatus.IN_STOCK, t0.minusSeconds(1), null));
        assertThrows(IllegalArgumentException.class, () -> b.remove(1_500, BatteryStatus.SCRAPPED, t0.plusSeconds(1), null));
        assertThrows(IllegalStateException.class, () -> b.scrap("dead", t0));
    }

    @Test void aTestIsRecordedOnTheBatteryWithItsClassifiedResult() {
        Battery b = battery(12, null);
        assertEquals(BatteryTestResult.WEAK, b.recordTest(d("12.1"), t0));
        assertEquals(BatteryTestResult.WEAK, b.getLastTestResult());
        b.scrap("failed test", t0);
        assertThrows(IllegalStateException.class, () -> b.recordTest(d("12.6"), t0));
    }
}
