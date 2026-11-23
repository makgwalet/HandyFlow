package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the services, entities and migration still use them. */
class AgOverrideWiringTest {

    private static final String MAIN = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(MAIN + name)); }

    @Test void aSaleStoresTheOverrideOnTheMovementAndNotInTheNotes() throws IOException {
        String s = src("application/internal/AgMovementRecordService.java");
        assertTrue(s.contains("requireAnimalOrGroupClearOrOverride("));
        assertTrue(s.contains("record.recordWithdrawalOverride(override);"));
        assertFalse(s.contains("withOverrideNote"), "the reason is no longer appended to the notes");
        assertTrue(s.indexOf("record.recordWithdrawalOverride(override);") < s.indexOf("movementRecordRepository.save(record);"), "stored before the save");
    }

    @Test void aHarvestStoresTheOverrideOnTheHarvestAndNotInTheNotes() throws IOException {
        String s = src("application/internal/AgHarvestRecordService.java");
        assertTrue(s.contains("requireCropClearOrOverride("));
        assertTrue(s.contains("record.recordWithdrawalOverride(override);"));
        assertFalse(s.contains("withOverrideNote"));
        assertTrue(s.contains("req.notes());"), "the notes are the person's own notes, untouched");
    }

    @Test void aStatusChangeToMarketStoresTheOverrideOnTheAnimal() throws IOException {
        String s = src("application/internal/AgAnimalService.java");
        assertTrue(s.contains("animal.recordWithdrawalOverride(withdrawal.requireAnimalOrGroupClearOrOverride("));
        assertFalse(s.contains("animal.addNote("));
    }

    @Test void theResponsesReturnTheStoredOverride() throws IOException {
        assertTrue(src("application/internal/AgMovementRecordService.java").contains("m.getWithdrawalOverrideReason(), m.getWithdrawalHeldUntil()"));
        assertTrue(src("application/internal/AgHarvestRecordService.java").contains("h.getWithdrawalOverrideReason(), h.getWithdrawalHeldUntil()"));
        assertTrue(src("application/internal/AgAnimalService.java").contains("a.getWithdrawalOverrideReason(), a.getWithdrawalHeldUntil()"));
    }

    @Test void theThreeEntitiesMapTheTwoColumns() throws IOException {
        for (String f : new String[] {"AgMovementRecord", "AgHarvestRecord", "AgAnimal"}) {
            String e = src("domain/model/" + f + ".java");
            assertTrue(e.contains("@Column(name = \"withdrawal_override_reason\", columnDefinition = \"TEXT\")"), f);
            assertTrue(e.contains("@Column(name = \"withdrawal_held_until\")"), f);
        }
    }

    @Test void theMigrationAddsTheColumnsToAllThreeTables() throws IOException {
        String m = Files.readString(Path.of("src/main/resources/db/migration/V887__agriculture_withdrawal_override_fields.sql"));
        for (String t : new String[] {"ag_movement_records", "ag_harvest_records", "ag_animals"}) {
            assertTrue(m.contains("ALTER TABLE " + t), t);
            assertTrue(m.contains("'" + t + "'"), t + " is in the notes copy as well");
        }
        assertTrue(m.contains("ADD COLUMN IF NOT EXISTS withdrawal_override_reason TEXT"));
        assertTrue(m.contains("WHERE withdrawal_override_reason IS NULL"), "the copy is safe to run twice");
    }
}
