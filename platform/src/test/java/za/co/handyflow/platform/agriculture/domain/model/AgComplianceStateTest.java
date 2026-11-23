package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgComplianceStateTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);

    @Test void farmMarksAreSetTrimmedAndCanBeCleared() {
        AgFarm f = AgFarm.create(TenantId.generate(), "Rietvlei", "LIVESTOCK", null, null, null, null);
        f.setMarks(" ZK 4821 ", "Swallow-tail left", "REG/77");
        assertEquals("ZK 4821", f.getBrandMark());
        assertEquals("Swallow-tail left", f.getEarMark());
        assertEquals("REG/77", f.getMarkRegistrationNumber());
        f.setMarks("ZK 4821", " ", null);
        assertEquals("ZK 4821", f.getBrandMark());
        assertNull(f.getEarMark());
        assertNull(f.getMarkRegistrationNumber());
    }

    @Test void aBadMarkChangesNothing() {
        AgFarm f = AgFarm.create(TenantId.generate(), "Rietvlei", "LIVESTOCK", null, null, null, null);
        f.setMarks("ZK 4821", null, null);
        assertThrows(IllegalArgumentException.class, () -> f.setMarks("ZK 4821", "x".repeat(61), null));
        assertEquals("ZK 4821", f.getBrandMark());
    }

    @Test void editingTheFarmDoesNotTouchItsMarks() {
        AgFarm f = AgFarm.create(TenantId.generate(), "Rietvlei", "LIVESTOCK", null, null, null, null);
        f.setMarks("ZK 4821", "left", "REG/77");
        f.update("Rietvlei 2", null, null, null, null, null, null, null, null);
        assertEquals("ZK 4821", f.getBrandMark());
        assertEquals("REG/77", f.getMarkRegistrationNumber());
    }

    private static AgInputApplication spray(Integer phi) {
        return AgInputApplication.create(TenantId.generate(), UUID.randomUUID(), DAY, "PESTICIDE", null, "Karate", new BigDecimal("2"),
                "L", null, null, null, null, null, null, null, phi);
    }

    @Test void aSprayKeepsItsInterval() {
        assertEquals(14, spray(14).getPreHarvestIntervalDays());
        assertNull(spray(null).getPreHarvestIntervalDays());
    }

    @Test void aSprayWithANonsenseIntervalIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> spray(-1));
        assertThrows(IllegalArgumentException.class, () -> spray(731));
    }

    @Test void theOldSprayFactoryStillWorksWithoutAnInterval() {
        AgInputApplication a = AgInputApplication.create(TenantId.generate(), UUID.randomUUID(), DAY, "FERTILISER", null, null,
                new BigDecimal("50"), "kg", null, null, null, null, null, null, null);
        assertNull(a.getPreHarvestIntervalDays());
    }

    private static AgHealthEvent treatment(Integer days) {
        return AgHealthEvent.create(TenantId.generate(), UUID.randomUUID(), null, "TREATMENT", DAY, "Antibiotic",
                "Terramycin", null, null, null, null, null, days, null, null, null);
    }

    @Test void aTreatmentWithANonsensePeriodIsRefused() {
        assertEquals(14, treatment(14).getWithdrawalPeriodDays());
        assertNull(treatment(null).getWithdrawalPeriodDays());
        assertThrows(IllegalArgumentException.class, () -> treatment(-3));
        assertThrows(IllegalArgumentException.class, () -> treatment(5000));
        AgHealthEvent e = treatment(14);
        assertThrows(IllegalArgumentException.class, () -> e.update(null, null, null, null, null, 9999, null, null));
        assertEquals(14, e.getWithdrawalPeriodDays());
    }

    @Test void addingANoteKeepsWhatWasThere() {
        AgAnimal a = AgAnimal.create(TenantId.generate(), UUID.randomUUID(), null, null, UUID.randomUUID(), "AN-001", "Bessie", null, "FEMALE",
                null, false, null, null, "PURCHASED", DAY, null);
        a.addNote("first");
        a.addNote(null);
        a.addNote("second");
        assertEquals("first\nsecond", a.getNotes());
    }
}
