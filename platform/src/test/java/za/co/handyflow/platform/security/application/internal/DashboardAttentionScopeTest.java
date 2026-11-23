package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.dto.SecurityDashboardDtos.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The dashboard carries three things at once, and a later change must not drop one: the site filter for limited users (chunk 90),
 * the coverage and weekly-hours items (chunks 48 and 53), and the company's own time zone (chunk 72).
 */
class DashboardAttentionScopeTest {

    private static final Shifts S = new Shifts(0, 0, 0, 0, 0);
    private static final Workforce W = new Workforce(10, 9, 2, 1, 1, 1);
    private static final Incidents I = new Incidents(0, 0, 0, 0);
    private static final Complaints C = new Complaints(2, 1);
    private static final Gate G = new Gate(0, 0, 0);
    private static final HoursAttention.Counts H = new HoursAttention.Counts(2, 1, 3);

    private static AttentionItem item(List<AttentionItem> l, String code) { return l.stream().filter(i -> i.code().equals(code)).findFirst().orElse(null); }

    @Test
    void allThreeBuildShapesExist() {
        assertNotNull(DashboardAttention.build(0, 0, S, W, I, C, G));
        assertNotNull(DashboardAttention.build(0, 0, S, W, I, C, G, 1, 1));
        assertNotNull(DashboardAttention.build(0, 0, S, W, I, C, G, 1, 1, H));
    }

    @Test
    void wholeCompanyGetsGuardsComplaintsCoverageAndHours() {
        List<AttentionItem> l = DashboardAttention.build(0, 0, S, W, I, C, G, 3, 2, H);
        assertEquals(3, item(l, "COVERAGE_NONE").count());
        assertEquals(2, item(l, "COVERAGE_SHORT").count());
        assertEquals(2, item(l, "HOURS_EXCEEDS").count());
        assertEquals(1, item(l, "HOURS_OVER").count());
        assertEquals(3, item(l, "REST_BREACH").count());
        assertEquals(2, item(l, "PSIRA_EXPIRED").count());
        assertEquals(1, item(l, "URGENT_COMPLAINTS").count());
        assertNotNull(item(l, "PSIRA_EXPIRING"));
    }

    @Test
    void limitedUserDoesNotGetGuardOrComplaintItemsButKeepsTheRest() {
        // Workforce and complaints are null for a limited user; nothing may throw, and the other items still show.
        List<AttentionItem> l = DashboardAttention.build(1, 0, S, null, new Incidents(2, 1, 1, 4), null, G, 3, 2, H);
        assertNull(item(l, "PSIRA_EXPIRED"));
        assertNull(item(l, "PSIRA_EXPIRING"));
        assertNull(item(l, "COMPETENCIES_EXPIRED"));
        assertNull(item(l, "COMPETENCIES_EXPIRING"));
        assertNull(item(l, "URGENT_COMPLAINTS"));
        assertNotNull(item(l, "CRITICAL_INCIDENTS"));
        assertNotNull(item(l, "ALARMS"));
        assertNotNull(item(l, "COVERAGE_NONE"));
    }

    // ---- the service's wiring, read from the source ----

    private static String src(String f) throws Exception {
        for (String root : new String[]{"src/main/java", "platform/src/main/java", "work/src/main/java"}) {
            Path p = Path.of(root, "za/co/handyflow/platform/security/application/internal", f);
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing " + f); return "";
    }

    @Test
    void dashboardServiceStillUsesCoverageHoursAndTheCompanyZone() throws Exception {
        String s = src("SecurityDashboardService.java");
        assertTrue(s.contains("BusinessCalendar.zoneOf(calendar, tenantId)"));
        assertFalse(s.contains("SAST"), "a fixed zone came back");
        assertTrue(s.contains("private CoverageService coverage;"));
        assertTrue(s.contains("private WeeklyHoursService weeklyHours;"));
        assertTrue(s.contains("DashboardAttention.build(openAlarms, n(alarmRow, \"new_alarms\"), shifts, workforce, incidents, complaints, gate, cov[0], cov[1], hours)"));
    }

    @Test
    void limitedUsersGetNoCompanyWideCoverageOrHoursFigures() throws Exception {
        String s = src("SecurityDashboardService.java");
        assertTrue(s.contains("int[] cov = limited ? new int[]{0, 0} : coverageCounts(tenantId, now);"));
        assertTrue(s.contains("HoursAttention.Counts hours = limited ? HoursAttention.Counts.NONE : hoursCounts(tenantId, now);"));
    }

    @Test
    void gateTimesUseTheCompanyZoneToo() throws Exception {
        String s = src("SecurityDashboardService.java");
        assertTrue(s.contains("dayStartTz(today, zone)"));
        assertTrue(s.contains("private static Timestamp dayStartTz(LocalDate today, ZoneId zone)"));
    }
}
