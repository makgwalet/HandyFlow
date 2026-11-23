package za.co.handyflow.platform.agriculture.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-scan checks for the Spring layer of the farm calendar, which a plain unit test cannot reach. Run with the platform folder as the working directory. */
class AgCalendarWiringTest {

    private static final Path MAIN = Path.of("src/main/java/za/co/handyflow/platform/agriculture");

    private static String read(String relative) throws IOException { return Files.readString(MAIN.resolve(relative)); }

    private static boolean before(String text, String first, String second) {
        int a = text.indexOf(first), b = text.indexOf(second);
        return a >= 0 && b >= 0 && a < b;
    }

    private static int count(String text, String part) {
        int n = 0, at = 0;
        while ((at = text.indexOf(part, at)) >= 0) { n++; at += part.length(); }
        return n;
    }

    @Test void theServiceChecksTheWindowAndTheFarmBeforeReadingAnything() throws IOException {
        String s = read("application/internal/AgCalendarService.java");
        assertTrue(before(s, "AgCalendarRules.requireWindow(from, to)", "references.requireActiveFarm(tenantId, farmId)"));
        assertTrue(before(s, "references.requireActiveFarm(tenantId, farmId)", "healthEventRepository.findCalendarForFarm("));
        assertTrue(s.contains("@Transactional(readOnly = true)"));
        assertFalse(s.contains(".save(") || s.contains(".delete("), "the calendar writes nothing");
    }

    @Test void anAcknowledgedReminderIsNeverShownEvenIfTheQueryReturnsIt() throws IOException {
        assertTrue(read("application/internal/AgCalendarService.java").contains("!e.isReminderAcknowledged() && AgCalendarRules.inWindow(e.getNextDueDate(), from, to)"));
    }

    @Test void everySourceIsReadForThisTenantsFarmAndWindow() throws IOException {
        String s = read("application/internal/AgCalendarService.java");
        for (String call : new String[]{
                "healthEventRepository.findCalendarForFarm(tenantId, farmId, from, to)",
                "breedingRecordRepository.findExpectedBirthsForCalendar(tenantId, farmId, from, to)",
                "cropCycleRepository.findForCalendar(tenantId, farmId, from, to)",
                "scoutingRecordRepository.findFollowUpsForCalendar(tenantId, farmId, from, to)",
                "healthEventRepository.findWithdrawalsForCalendar(tenantId, farmId,"})
            assertTrue(s.contains(call), call);
    }

    @Test void withdrawalsLookBackTheLongestPeriodAndAreCapped() throws IOException {
        String s = read("application/internal/AgCalendarService.java");
        assertTrue(s.contains("from.minusDays(AgWithdrawalRules.MAX_DAYS)"));
        assertTrue(s.contains("PageRequest.of(0, WITHDRAWAL_ROW_CAP)"));
        assertTrue(s.contains("AgCalendarRules.withdrawalEnds("));
    }

    @Test void namesFromOtherTenantsAreNeverUsed() throws IOException {
        String s = read("application/internal/AgCalendarService.java");
        assertEquals(3, count(s, "tenantId.equals("), "animals, groups and crop cycles are each checked against the tenant");
    }

    @Test void onlyOpenAndUnacknowledgedItemsAreRead() throws IOException {
        String h = read("domain/repository/AgHealthEventRepository.java");
        String cal = h.substring(h.indexOf("findCalendarForFarm") - 900, h.indexOf("findCalendarForFarm"));
        assertTrue(cal.contains("e.reminderAcknowledged = false"));
        assertTrue(cal.contains("a.deletedAt IS NULL AND a.status = 'ACTIVE'"));
        String b = read("domain/repository/AgBreedingRecordRepository.java");
        assertTrue(b.contains("b.outcome IN ('PENDING', 'CONFIRMED_PREGNANT')"));
        String sc = read("domain/repository/AgScoutingRecordRepository.java");
        assertTrue(sc.contains("r.followUpAcknowledged = false\n        AND r.cropCycleId IN") || sc.contains("r.followUpAcknowledged = false"));
        String c = read("domain/repository/AgCropCycleRepository.java");
        assertTrue(c.contains("c.status = 'PLANNED' AND c.plantingDate IS NOT NULL"));
    }

    @Test void theEndpointIsReadOnlyAndGuarded() throws IOException {
        String c = read("api/AgCalendarController.java");
        assertTrue(c.contains("@GetMapping(\"/{farmId}/calendar\")"));
        assertTrue(c.contains("hasAuthority('AGRICULTURE_READ')"));
        assertTrue(c.contains("featureGuard.requireModule(\"agriculture\")"));
        assertFalse(c.contains("@PostMapping") || c.contains("@PutMapping") || c.contains("@PatchMapping") || c.contains("@DeleteMapping"));
        assertTrue(c.contains("TenantContext.getTenantIdAsObject()"));
    }

    @Test void everyNewQueryIsBalanced() throws IOException {
        for (String f : new String[]{"AgHealthEventRepository", "AgBreedingRecordRepository", "AgCropCycleRepository", "AgScoutingRecordRepository"}) {
            String s = read("domain/repository/" + f + ".java");
            int at = 0;
            while ((at = s.indexOf("ForCalendar", at)) >= 0 || false) {
                int q = s.lastIndexOf("@Query(", at);
                String query = s.substring(q, at);
                int open = count(query, "("), close = count(query, ")");
                assertEquals(open, close, f + " query near " + s.substring(Math.max(0, at - 30), at + 20));
                at += 11;
            }
        }
    }
}
