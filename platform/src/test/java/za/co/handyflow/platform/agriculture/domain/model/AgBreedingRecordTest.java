package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgBreedingRecordTest {

    private static final LocalDate MATED = LocalDate.now().minusDays(60);
    private static final LocalDate BORN = LocalDate.now().minusDays(1);

    private static AgBreedingRecord record() {
        return AgBreedingRecord.create(TenantId.generate(), UUID.randomUUID(), null, "natural", MATED, null, null, null, null);
    }

    @Test void startsPendingAndStoresTheShortType() {
        AgBreedingRecord r = record();
        assertEquals("PENDING", r.getOutcome());
        assertEquals("NATURAL", r.getBreedingType());
    }

    @Test void longInseminationSpellingIsStoredAsAI() {
        AgBreedingRecord r = AgBreedingRecord.create(TenantId.generate(), UUID.randomUUID(), null,
                "ARTIFICIAL_INSEMINATION", MATED, null, null, null, null);
        assertEquals("AI", r.getBreedingType());
    }

    @Test void confirmThenBirthRecordsLiveCount() {
        AgBreedingRecord r = record();
        r.confirmPregnant(MATED.plusDays(150));
        assertEquals("CONFIRMED_PREGNANT", r.getOutcome());
        assertEquals(MATED.plusDays(150), r.getExpectedDueDate());
        r.recordBirth(BORN, 2, 1);
        assertEquals("BIRTHED", r.getOutcome());
        assertEquals(2, r.getOffspringCount());
        assertEquals(1, r.getStillbornCount());
        assertEquals(BORN, r.getActualBirthDate());
    }

    @Test void allStillbornIsStillborn() {
        AgBreedingRecord r = record();
        r.recordBirth(BORN, 0, 2);
        assertEquals("STILLBORN", r.getOutcome());
        assertEquals(0, r.getOffspringCount());
    }

    @Test void birthMayBeRecordedWithoutConfirmation() {
        AgBreedingRecord r = record();
        r.recordBirth(BORN, 1, null);
        assertEquals("BIRTHED", r.getOutcome());
        assertEquals(0, r.getStillbornCount());
    }

    @Test void confirmingKeepsTheDueDateWhenNoneGiven() {
        AgBreedingRecord r = AgBreedingRecord.create(TenantId.generate(), UUID.randomUUID(), null, "AI", MATED, null, null, MATED.plusDays(280), null);
        r.confirmPregnant(null);
        assertEquals(MATED.plusDays(280), r.getExpectedDueDate());
    }

    @Test void aFailedRecordCannotBeBornOrConfirmed() {
        AgBreedingRecord r = record();
        r.markFailed();
        assertThrows(IllegalStateException.class, () -> r.recordBirth(BORN, 1, 0));
        assertThrows(IllegalStateException.class, () -> r.confirmPregnant(null));
        assertThrows(IllegalStateException.class, r::markAborted);
        assertEquals("FAILED", r.getOutcome());
        assertNull(r.getActualBirthDate());
    }

    @Test void aBornRecordCannotBeFailedOrBornAgain() {
        AgBreedingRecord r = record();
        r.recordBirth(BORN, 1, 0);
        assertThrows(IllegalStateException.class, r::markFailed);
        assertThrows(IllegalStateException.class, () -> r.recordBirth(BORN, 3, 0));
        assertEquals(1, r.getOffspringCount());
    }

    @Test void notPregnantIsFinal() {
        AgBreedingRecord r = record();
        r.markNotPregnant();
        assertThrows(IllegalStateException.class, () -> r.confirmPregnant(null));
    }

    @Test void aRefusedBirthChangesNothing() {
        AgBreedingRecord r = record();
        assertThrows(IllegalArgumentException.class, () -> r.recordBirth(MATED.minusDays(1), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> r.recordBirth(BORN, 0, 0));
        assertEquals("PENDING", r.getOutcome());
        assertNull(r.getActualBirthDate());
        assertNull(r.getOffspringCount());
    }

    @Test void dueDateBeforeMatingIsRefusedOnConfirm() {
        AgBreedingRecord r = record();
        assertThrows(IllegalArgumentException.class, () -> r.confirmPregnant(MATED.minusDays(1)));
        assertEquals("PENDING", r.getOutcome());
    }

    @Test void createRefusesBadInput() {
        TenantId t = TenantId.generate();
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRecord.create(t, UUID.randomUUID(), null, "x", MATED, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRecord.create(t, UUID.randomUUID(), null, "AI", LocalDate.now().plusDays(2), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRecord.create(t, UUID.randomUUID(), UUID.randomUUID(), "AI", MATED, null, null, null, null));
    }
}
