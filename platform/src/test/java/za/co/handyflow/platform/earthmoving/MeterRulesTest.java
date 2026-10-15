package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    // ── readings as they stand, and disputes ─────────────────────────────────

    static MeterRules.Point pt(UUID id, String km, String at, UUID corrects) {
        return new MeterRules.Point(id, "KILOMETRES", n(km), Instant.parse(at), corrects);
    }

    @Test void theReadingAtAMomentIsTheNewestRecordedBeforeIt() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var all = List.of(pt(b, "10900", "2026-10-10T10:00:00Z", null), pt(a, "10000", "2026-09-30T10:00:00Z", null));
        assertEquals(a, MeterRules.standingBefore(all, "KILOMETRES", Instant.parse("2026-10-05T00:00:00Z")).id());
        assertEquals(n("10900"), MeterRules.standingBefore(all, "KILOMETRES", Instant.parse("2026-10-11T00:00:00Z")).reading());
        assertNull(MeterRules.standingBefore(all, "KILOMETRES", Instant.parse("2026-09-01T00:00:00Z")));
        assertNull(MeterRules.standingBefore(all, "HOURS", Instant.parse("2026-10-11T00:00:00Z")));
    }
    @Test void aCorrectionTakesThePlaceInTimeOfTheReadingItReplaces() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var all = List.of(pt(c, "10850", "2026-10-12T08:00:00Z", b), pt(b, "10900", "2026-10-10T10:00:00Z", null), pt(a, "10000", "2026-09-30T10:00:00Z", null));
        // The period ended on the 10th, before the correction was made, but it sees the corrected figure.
        var end = MeterRules.standingBefore(all, "KILOMETRES", Instant.parse("2026-10-11T00:00:00Z"));
        assertEquals(c, end.id());
        assertEquals(n("10850"), end.reading());
        // Before the replaced reading was taken, nothing changes.
        assertEquals(a, MeterRules.standingBefore(all, "KILOMETRES", Instant.parse("2026-10-05T00:00:00Z")).id());
        // A later reading still wins over the correction.
        UUID d = UUID.randomUUID();
        var later = new java.util.ArrayList<>(all);
        later.add(pt(d, "11200", "2026-10-14T08:00:00Z", null));
        assertEquals(d, MeterRules.standingBefore(later, "KILOMETRES", Instant.parse("2026-10-15T00:00:00Z")).id());
    }
    @Test void aDisputeAndItsResolutionNeedWords() {
        assertEquals("Driver says 10850", MeterRules.checkDispute(" Driver says 10850 "));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkDispute("no"));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkDispute(null));
        assertEquals("Checked against the logbook", MeterRules.checkResolution("Checked against the logbook"));
        assertThrows(IllegalArgumentException.class, () -> MeterRules.checkResolution(" "));
    }
    @Test void aDisputeIsOpenUntilConfirmedOrCorrectedAndOnlyClosesOnce() {
        var d = EarthMeterDispute.raise(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), "Driver says 10850", null);
        assertTrue(d.isOpen());
        d.confirm("Checked against the logbook", null);
        assertEquals("CONFIRMED", d.getStatus());
        assertNotNull(d.getResolvedAt());
        assertThrows(IllegalStateException.class, () -> d.confirm("again please", null));
        assertThrows(IllegalStateException.class, () -> d.corrected("fixed", null));
        var e = EarthMeterDispute.raise(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), "Looks too high", null);
        e.corrected("Corrected to 10850", null);
        assertEquals("CORRECTED", e.getStatus());
    }
    @Test void aCorrectionRemembersWhichReadingItReplaces() {
        UUID replaced = UUID.randomUUID();
        var r = EarthMeterReading.correctionOf(TenantId.generate(), UUID.randomUUID(), "KILOMETRES", n("10850"), n("10900"), "Typed it twice", null, replaced);
        assertEquals("CORRECTION", r.getSource());
        assertEquals(replaced, r.getCorrectsId());
        assertEquals(replaced, r.point().correctsId());
    }
}
