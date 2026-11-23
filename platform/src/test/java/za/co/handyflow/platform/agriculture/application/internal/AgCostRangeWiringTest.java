package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The rules are tested on their own; this checks the repository, service and controller still use them. */
class AgCostRangeWiringTest {

    private static final String DIR = "src/main/java/za/co/handyflow/platform/agriculture/";

    private static String src(String name) throws IOException { return Files.readString(Path.of(DIR + name)); }

    private static int count(String text, String part) { return text.split(java.util.regex.Pattern.quote(part), -1).length - 1; }

    @Test void theRangedQueriesIncludeBothEndsAndNeverTakeANull() throws IOException {
        String r = src("domain/repository/AgCostEntryRepository.java");
        assertEquals(4, count(r, "AND e.entryDate >= :from AND e.entryDate <= :to"), "two lists and two sums");
        assertFalse(r.contains(":from IS NULL"), "no null binds");
        assertFalse(r.contains(":to IS NULL"), "no null binds");
        assertEquals(2, count(r, "AND e.entryDate <= :to ORDER BY e.entryDate DESC, e.createdAt DESC, e.id DESC"), "paged ranged lists keep the id tie-break");
    }

    @Test void theServiceChecksTheOrderOfTheDatesAndUsesTheUnfilteredQueryOnlyWhenThereIsNoRange() throws IOException {
        String s = src("application/internal/AgCostEntryService.java");
        assertEquals(2, count(s, "AgDateRangeRules.require(from, to);"), "list and totals");
        assertEquals(2, count(s, "AgDateRangeRules.isUnbounded(from, to)"), "list and totals");
        assertEquals(2, count(s, "AgDateRangeRules.from(from), AgDateRangeRules.to(to), pageable)"), "both ranged lists");
        assertEquals(2, count(s, "AgDateRangeRules.from(from), AgDateRangeRules.to(to))"), "both ranged sums");
        assertTrue(s.contains("return list(tenantId, farmId, targetType, targetId, null, null, pageable);"), "old signature kept");
        assertTrue(s.contains("return totals(tenantId, farmId, targetType, targetId, null, null);"), "old signature kept");
    }

    @Test void theControllerPassesTheDatesToBothEndpoints() throws IOException {
        String c = src("api/AgCostEntryController.java");
        assertEquals(4, count(c, "@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate"), "from and to on list and on totals");
        assertTrue(c.contains("farmId, targetType, targetId, from, to, pageable)"));
        assertTrue(c.contains("farmId, targetType, targetId, from, to)))"));
    }
}
