package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MeterRulesTest {
    static BigDecimal n(String s) { return new BigDecimal(s); }

    @Test void blankMeterTypeMeansHoursAndUnknownIsRefused() {
        assertEquals("HOURS", MeterRules.normalizeType(null));
        assertEquals("HOURS", MeterRules.normalizeType("  "));
        assertEquals("KILOMETRES", MeterRules.normalizeType(" kilometres "));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.normalizeType("MILES"));
    }
    @Test void unitsAndWords() {
        assertEquals("km", MeterRules.unit("KILOMETRES"));
        assertEquals("hrs", MeterRules.unit("HOURS"));
        assertEquals("kilometres", MeterRules.word("KILOMETRES"));
        assertEquals("hours", MeterRules.word("HOURS"));
    }
    @Test void aCorrectionNeedsAReasonAndAChange() {
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkCorrection(n("500"), n("50"), n("0"), n("0"), " "));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkCorrection(n("500"), n("500"), n("0"), n("0"), "typed an extra zero"));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkCorrection(n("500"), null, n("0"), n("0"), "typed an extra zero"));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkCorrection(n("500"), n("-1"), n("0"), n("0"), "typed an extra zero"));
        assertEquals("typed an extra zero", MeterRules.checkCorrection(n("5000"), n("500"), n("480"), n("250"), " typed an extra zero "));
    }
    @Test void aCorrectionCannotGoBelowTheEarlierReadingOrTheLastService() {
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkCorrection(n("5000"), n("400"), n("480"), n("0"), "typed an extra zero"));
        assertThrows(IllegalStateException.class, () -> MeterRules.checkCorrection(n("5000"), n("400"), n("0"), n("450"), "typed an extra zero"));
        assertDoesNotThrow(() -> MeterRules.checkCorrection(n("500"), n("900"), n("480"), n("450"), "typed too little"));
    }
    @Test void changingTheMeterNeedsAllThreeNumbersAndAReason() {
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkChange("HOURS", "HOURS", n("10"), n("0"), n("5000"), "truck odometer"));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkChange("HOURS", "KILOMETRES", null, n("0"), n("5000"), "truck odometer"));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkChange("HOURS", "KILOMETRES", n("100"), n("200"), n("5000"), "truck odometer"));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkChange("HOURS", "KILOMETRES", n("100"), n("0"), n("0"), "truck odometer"));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkChange("HOURS", "KILOMETRES", n("100"), n("0"), n("5000"), "abc"));
        assertEquals("truck odometer", MeterRules.checkChange("HOURS", "KILOMETRES", n("48000"), n("45000"), n("5000"), "truck odometer"));
    }
    @Test void aMachineIsDueByKilometresToo() {
        EarthAsset a = EarthAsset.create(TenantId.generate(), "Tipper", "T-01", "TRUCK", null, null, null, null, null,
                "OWN", null, null, null, null, null, null);
        a.changeMeter("KILOMETRES", n("52000"), n("45000"), n("5000"));
        assertEquals("KILOMETRES", a.getMeterType());
        assertTrue(a.isDueForService());
        a.correctReading(n("49000"));
        assertFalse(a.isDueForService());
        assertEquals(n("49000"), a.getCurrentHours());
    }
    @Test void aNewMachineDefaultsToHoursAndCanChooseAtRegistration() {
        EarthAsset a = EarthAsset.create(TenantId.generate(), "Dozer", "D-01", "DOZER", null, null, null, null, null,
                "OWN", null, null, null, null, null, null);
        assertEquals("HOURS", a.getMeterType());
        a.useMeter("kilometres");
        assertEquals("KILOMETRES", a.getMeterType());
        assertThrows(IllegalArgumentException.class, () -> a.useMeter("MILES"));
    }
    @Test void readingRowsKnowTheirSource() {
        var r = EarthMeterReading.of(TenantId.generate(), UUID.randomUUID(), "HOURS", n("10"), n("5"), "MANUAL", null, null);
        assertEquals("MANUAL", r.getSource());
        assertThrows(IllegalArgumentException.class, () -> EarthMeterReading.of(TenantId.generate(), UUID.randomUUID(), "HOURS", n("10"), null, "GUESS", null, null));
    }
}
