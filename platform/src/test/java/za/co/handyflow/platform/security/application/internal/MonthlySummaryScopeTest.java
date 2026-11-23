package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MonthlySummaryScopeTest {

    private static final UUID S1 = new UUID(0, 1), S2 = new UUID(0, 2), S3 = new UUID(0, 3);
    private record Rec(String name, UUID site) {}
    private static final List<Rec> ALL = Arrays.asList(new Rec("a", S1), new Rec("b", S2), new Rec("c", S3), new Rec("d", null), new Rec("e", S1));

    private static List<String> names(List<Rec> l) { return l.stream().map(Rec::name).toList(); }

    @Test
    void wholeCompanyKeepsEverythingEvenRecordsWithNoSite() {
        assertEquals(List.of("a", "b", "c", "d", "e"), names(MonthlySummaryScope.keep(ALL, Rec::site, null)));
    }

    @Test
    void limitedKeepsOnlyRecordsAtTheAllowedSites() {
        assertEquals(List.of("a", "b", "e"), names(MonthlySummaryScope.keep(ALL, Rec::site, Set.of(S1, S2))).stream().sorted().toList());
    }

    @Test
    void aRecordWithNoSiteIsNotAtAnyAllowedSite() {
        assertFalse(names(MonthlySummaryScope.keep(ALL, Rec::site, Set.of(S1, S2, S3))).contains("d"));
    }

    @Test
    void nothingAllowedMeansNothingKept() {
        assertEquals(List.of(), MonthlySummaryScope.keep(ALL, Rec::site, Set.of()));
    }

    @Test
    void orderIsKept() {
        assertEquals(List.of("a", "e"), names(MonthlySummaryScope.keep(ALL, Rec::site, Set.of(S1))));
    }

    // ---- the service, read from the source ----

    private static String src(String f) throws Exception {
        for (String root : new String[]{"src/main/java", "platform/src/main/java", "work/src/main/java"}) {
            Path p = Path.of(root, "za/co/handyflow/platform/security", f);
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing " + f); return "";
    }

    @Test
    void bothListsAreNarrowedBeforeAnyFigureIsWorkedOut() throws Exception {
        String s = src("application/internal/ReportingService.java");
        int a = s.indexOf("public MonthlySummaryReport getMonthlySummaryReport(TenantId tenantId, YearMonth month, java.util.Set<UUID> allowed)");
        assertTrue(a > 0);
        String body = s.substring(a);
        assertTrue(body.contains("MonthlySummaryScope.keep(shiftRepository.findByTenantInRange(tenantId, from, to), Shift::getSiteId, allowed)"));
        assertTrue(body.contains("MonthlySummaryScope.keep(incidentRepository.findByTenantInRange(tenantId, from, to), Incident::getSiteId, allowed)"));
        assertTrue(body.indexOf("MonthlySummaryScope.keep(shiftRepository") < body.indexOf("long totalShifts"));
    }

    @Test
    void theOldTwoArgumentMethodIsTheWholeCompany() throws Exception {
        String s = src("application/internal/ReportingService.java");
        assertTrue(s.contains("public MonthlySummaryReport getMonthlySummaryReport(TenantId tenantId, YearMonth month) {\n        return getMonthlySummaryReport(tenantId, month, null);"));
    }

    @Test
    void theMonthIsStillDecidedInTheCompanyZoneInOnePlace() throws Exception {
        String s = src("application/internal/ReportingService.java");
        assertEquals(4, s.split("calendar\\.monthRange\\(tenantId, month\\)", -1).length - 1, "the time-zone test counts these");
        assertFalse(s.contains("ZoneOffset"));
        assertFalse(s.contains("atStartOfDay"));
    }

    @Test
    void thePdfStaysForTheWholeCompanyBecauseItIsLabelledCompanyWide() throws Exception {
        String c = src("api/ReportingController.java");
        int a = c.indexOf("getMonthlySummaryPdf(");
        String body = c.substring(a, c.indexOf("// ── Helpers", a));
        assertTrue(body.contains("wholeCompany();"));
        assertFalse(body.contains("allowed()"));
    }
}
