package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A ratchet, not a clean-up: Security code must not add new places that decide a business day in UTC, the server's zone or a hard-coded zone.
 * Each file may use these at most as often as it does today (the counts below); a file not listed may use none. When you convert a use to
 * BusinessCalendar, lower its count here. Use {@code BusinessCalendar} (or {@code BusinessTime} with a zone) instead.
 */
class TimeZoneRatchetTest {

    private static final Path ROOT = Path.of("src/main/java/za/co/handyflow/platform/security");

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("ZoneOffset\\.UTC"), Pattern.compile("ZoneId\\.systemDefault\\(\\)"),
            Pattern.compile("ZoneId\\.of\\(\"Africa/Johannesburg\"\\)"), Pattern.compile("LocalDate\\.now\\(\\)"),
            Pattern.compile("LocalDateTime\\.now\\(\\)"), Pattern.compile("\\.atStartOfDay\\(\\)"),
            Pattern.compile("toLocalDateTime\\(\\)\\.toInstant"));

    /** Uses still to convert, per file (path under the security package). */
    static final Map<String, Integer> BASELINE = Map.ofEntries(
            Map.entry("application/internal/ArmouryComplianceScheduler.java", 1),
            Map.entry("application/internal/ArmouryPdfService.java", 1),
            Map.entry("application/internal/CompetencyReadinessService.java", 1),
            Map.entry("application/internal/CoverageService.java", 3),
            Map.entry("application/internal/GateDashboardService.java", 1),
            Map.entry("application/internal/GuardCompetencyExpiryScheduler.java", 1),
            Map.entry("application/internal/GuardCompetencyService.java", 1),
            Map.entry("application/internal/GuardComplaintService.java", 1),
            Map.entry("application/internal/GuardDirectoryService.java", 1),
            Map.entry("application/internal/GuardOverviewService.java", 2),
            Map.entry("application/internal/GuardPerformanceService.java", 1),
            Map.entry("application/internal/GuardPerformanceSnapshotJob.java", 1),
            Map.entry("application/internal/GuardRatingService.java", 1),
            Map.entry("application/internal/GuardReviewService.java", 1),
            Map.entry("application/internal/GuardScreeningComplianceScheduler.java", 1),
            Map.entry("application/internal/GuardScreeningService.java", 1),
            Map.entry("application/internal/IncidentPdfService.java", 1),
            Map.entry("application/internal/MonthlyPackService.java", 3),
            Map.entry("application/internal/NoShowAlertScheduler.java", 1),
            Map.entry("application/internal/OfflineTimestamp.java", 1),
            Map.entry("application/internal/PsiraComplianceScheduler.java", 1),
            Map.entry("application/internal/RotationService.java", 1),
            Map.entry("application/internal/SecurityDashboardService.java", 9),
            Map.entry("application/internal/SecurityPdfBrandingHelper.java", 1),
            Map.entry("application/internal/ShiftSwapService.java", 1),
            Map.entry("application/internal/WeeklyHoursService.java", 3));

    static int count(String source) {
        int n = 0;
        for (Pattern p : FORBIDDEN) { var m = p.matcher(source); while (m.find()) n++; }
        return n;
    }

    @Test
    void noFileUsesMoreThanItDoesToday() throws Exception {
        List<String> over = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String rel = ROOT.relativize(f).toString().replace('\\', '/');
                int n = count(Files.readString(f));
                int allowed = BASELINE.getOrDefault(rel, 0);
                if (n > allowed) over.add(rel + " uses " + n + ", allowed " + allowed);
            }
        }
        assertTrue(over.isEmpty(), "Decide days with BusinessCalendar, not UTC or a fixed zone: " + over);
    }

    @Test
    void theReportsAndPayStatementsAreDoneAndMustStayThatWay() throws Exception {
        for (String f : new String[]{"application/internal/ReportingService.java", "application/internal/PdfReportService.java",
                "application/internal/SecurityGuardPayStatementPdfService.java"}) {
            assertFalse(BASELINE.containsKey(f), f);
            assertEquals(0, count(Files.readString(ROOT.resolve(f))), f);
        }
    }

    @Test
    void theCounterSeesEachKindOfUse() {
        assertEquals(1, count("x.toInstant(ZoneOffset.UTC)"));
        assertEquals(1, count("ZoneId.systemDefault()"));
        assertEquals(1, count("ZoneId.of(\"Africa/Johannesburg\")"));
        assertEquals(1, count("LocalDate.now()"));
        assertEquals(1, count("LocalDateTime.now()"));
        assertEquals(1, count("d.atStartOfDay()"));
        assertEquals(1, count("t.toLocalDateTime().toInstant(z)"));
        assertEquals(0, count("LocalDate.now(clock); d.atStartOfDay(zone); ZoneId.of(id)"));
    }

    @Test
    void theBaselineListsOnlyFilesThatExist() {
        for (String f : BASELINE.keySet()) assertTrue(Files.exists(ROOT.resolve(f)), f + " is listed but missing; lower or remove it");
    }
}
