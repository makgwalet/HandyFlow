package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the sweeps and queries still use them (a missed call compiles and runs). */
class AgReminderWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    @Test void theSweepsNeverWriteTheAcknowledgedFlag() throws IOException {
        for (String s : new String[] {"AgNotificationScheduler", "AgCropNotificationScheduler"}) {
            String code = src("application/internal/" + s + ".java");
            assertFalse(code.contains("acknowledgeReminder"), s);
            assertFalse(code.contains("acknowledgeFollowUp"), s);
        }
        assertTrue(src("application/internal/AgNotificationScheduler.java").contains("markReminderNotified(today)"));
        assertTrue(src("application/internal/AgNotificationScheduler.java").contains("markLowStockNotified(today)"));
        assertTrue(src("application/internal/AgCropNotificationScheduler.java").contains("markFollowUpNotified(today)"));
    }

    @Test void everySweepHoldsALeaseAndUsesTheSouthAfricanDate() throws IOException {
        for (String s : new String[] {"AgNotificationScheduler", "AgCropNotificationScheduler"}) {
            String code = src("application/internal/" + s + ".java");
            assertTrue(code.contains("lease.runExclusively("), s);
            assertTrue(code.contains("AgDates.today()"), s);
            assertFalse(code.contains("LocalDate.now()"), s);
            assertFalse(code.contains("@Transactional"), s + ": one transaction per record, not one for the whole sweep");
            assertTrue(code.contains("new TransactionTemplate("), s);
        }
    }

    @Test void remindersGoToTheFarmManagerAndNameTheAnimal() throws IOException {
        String support = src("application/internal/AgReminderSupport.java");
        assertTrue(support.contains("AgReminderRules.managerOrFallback("));
        assertTrue(support.contains("AgReminderRules.animalLabel("));
        assertTrue(src("application/internal/AgNotificationScheduler.java").contains("support.recipientsFor("));
        assertFalse(src("application/internal/AgNotificationScheduler.java").contains("\"animal \" + event.getAnimalId()"), "no raw ids in messages");
    }

    @Test void sweepQueriesSkipWhatIsGoneAndWhatWasJustSent() throws IOException {
        String health = src("domain/repository/AgHealthEventRepository.java");
        assertTrue(health.contains("e.reminderNotifiedOn <= :repeatBefore"));
        assertTrue(health.contains("a.status = 'ACTIVE'") && health.contains("g.status = 'ACTIVE'"));
        assertTrue(health.contains("f.deletedAt IS NULL AND f.status = 'ACTIVE'"));
        assertTrue(src("domain/repository/AgScoutingRecordRepository.java").contains("r.followUpNotifiedOn <= :repeatBefore"));
        String inv = src("domain/repository/AgInventoryItemRepository.java");
        assertTrue(inv.contains("i.lowStockNotifiedOn <= :repeatBefore"));
        assertTrue(inv.contains("f.status = 'ACTIVE'"));
    }

    @Test void theDashboardListStillShowsWhatOnlyAPersonHasNotAcknowledged() throws IOException {
        String health = src("domain/repository/AgHealthEventRepository.java");
        int farmQuery = health.indexOf("findDueForFarm");
        assertTrue(farmQuery > 0);
        assertTrue(health.substring(health.lastIndexOf("@Query", farmQuery), farmQuery).contains("e.reminderAcknowledged = false"));
        assertFalse(health.substring(health.lastIndexOf("@Query", farmQuery), farmQuery).contains("reminderNotifiedOn"), "being told about it must not hide it");
    }

    @Test void recordDatesUseTheSouthAfricanToday() throws IOException {
        for (String f : new String[] {"application/internal/AgInventoryItemService.java", "application/internal/AgMortalityRecordService.java",
                "application/internal/AgMovementRecordService.java", "domain/model/AgBreedingRecord.java"}) {
            assertFalse(src(f).contains("LocalDate.now()"), f);
            assertTrue(src(f).contains("AgDates.today()"), f);
        }
    }
}
