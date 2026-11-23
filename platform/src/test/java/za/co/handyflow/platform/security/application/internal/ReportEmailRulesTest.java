package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportEmailRulesTest {

    private static String code(Runnable r) {
        try { r.run(); } catch (HandyFlowException e) { return e.getErrorCode(); }
        return "NONE";
    }

    // ---- addresses and names ----

    @Test
    void cleansAValidAddress() {
        assertEquals("a.b@client.co.za", ReportEmailRules.cleanEmail("  A.B@Client.co.za "));
    }

    @Test
    void refusesAnythingThatIsNotExactlyOneAddress() {
        for (String bad : new String[]{null, "", "no-at.com", "a@b", "a b@c.com", "a@b.com,c@d.com", "a@b.com;c@d.com", "<a@b.com>",
                "a@b.com\r\nBcc: x@y.com", "a@b.com\nx@y.com", "\"a\"@b.com", "a@@b.com", "a@b..", "x".repeat(250) + "@b.com"})
            assertEquals("INVALID_EMAIL", code(() -> ReportEmailRules.cleanEmail(bad)), String.valueOf(bad));
    }

    @Test
    void cleansNamesAndRefusesEmptyOrLongOnes() {
        assertEquals("Jo Client", ReportEmailRules.cleanName("  Jo \t\n Client "));
        assertEquals("INVALID_NAME", code(() -> ReportEmailRules.cleanName("  ")));
        assertEquals("INVALID_NAME", code(() -> ReportEmailRules.cleanName(null)));
        assertEquals("INVALID_NAME", code(() -> ReportEmailRules.cleanName("x".repeat(101))));
        assertEquals(100, ReportEmailRules.cleanName("x".repeat(100)).length());
    }

    @Test
    void atMostFiveRecipients() {
        ReportEmailRules.requireRoom(4);
        assertEquals("TOO_MANY_RECIPIENTS", code(() -> ReportEmailRules.requireRoom(5)));
    }

    // ---- when it may be sent ----

    @Test
    void onlyAFinishedSouthAfricanMonthMaySend() {
        Instant firstMorning = Instant.parse("2026-10-31T22:30:00Z"); // 00:30 on 1 Nov in South Africa
        ReportEmailRules.requireFinished(YearMonth.of(2026, 10), firstMorning);
        assertEquals("MONTH_NOT_FINISHED", code(() -> ReportEmailRules.requireFinished(YearMonth.of(2026, 11), firstMorning)));
        Instant lastEvening = Instant.parse("2026-10-31T20:00:00Z");  // 22:00 on 31 Oct
        assertEquals("MONTH_NOT_FINISHED", code(() -> ReportEmailRules.requireFinished(YearMonth.of(2026, 10), lastEvening)));
        assertEquals("MONTH_NOT_FINISHED", code(() -> ReportEmailRules.requireFinished(YearMonth.of(2027, 1), lastEvening)));
        ReportEmailRules.requireFinished(YearMonth.of(2025, 12), lastEvening);
    }

    @Test
    void needsARecipient() {
        assertEquals("NO_RECIPIENTS", code(() -> ReportEmailRules.requireRecipients(0)));
        ReportEmailRules.requireRecipients(1);
    }

    @Test
    void notTwiceWithinTenMinutes() {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");
        ReportEmailRules.requireNotJustSent(null, now);
        assertEquals("JUST_SENT", code(() -> ReportEmailRules.requireNotJustSent(now.minusSeconds(599), now)));
        ReportEmailRules.requireNotJustSent(now.minusSeconds(600), now);
        ReportEmailRules.requireNotJustSent(now.minusSeconds(3600), now);
    }

    // ---- the message ----

    @Test
    void subjectNamesSiteAndMonth() {
        assertEquals("Monthly service report — Centurion Mall — October 2026", ReportEmailRules.subject(" Centurion Mall ", ReportEmailRules.monthTitle(YearMonth.of(2026, 10))));
        assertFalse(ReportEmailRules.subject("Mall\r\nBcc: x@y.com", "October 2026").contains("\n"));
        assertFalse(ReportEmailRules.subject("Mall\r\nBcc: x@y.com", "October 2026").contains("\r"));
    }

    @Test
    void htmlEscapesEverythingTypedByPeople() {
        String h = ReportEmailRules.html(new ReportEmailRules.Content("<script>alert(1)</script> & \"Co\"", "October 2026", "2026-10-01 to 2026-10-31",
                List.of(new ReportEmailRules.Line("Cover", "<b>x</b>"))));
        assertFalse(h.contains("<script>"));
        assertFalse(h.contains("<b>x</b>"));
        assertTrue(h.contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;Co&quot;"));
        assertTrue(h.contains("&lt;b&gt;x&lt;/b&gt;"));
        assertTrue(h.contains("Period 2026-10-01 to 2026-10-31"));
        assertTrue(h.contains("Cover"));
    }

    // ---- wording, the same as the web page ----

    @Test
    void numbersPrintLikeTheWebPage() {
        assertEquals("47", PackWording.num(47.0));
        assertEquals("97.9", PackWording.num(97.9));
        assertEquals("0", PackWording.num(0));
    }

    @Test
    void wordsCoverAndNeverClaimsCoverWithoutPosts() {
        assertEquals("47 of 48 required guard-hours covered (97.9%).", PackWording.cover(true, 4, 48.0, 47.0, 97.9));
        assertEquals("47 of 48 required guard-hours covered.", PackWording.cover(true, 4, 48.0, 47.0, null));
        assertEquals("No posts are set up for this site, so cover is not measured.", PackWording.cover(false, 0, 0, 0, null));
        assertEquals("No post-days had finished in this period.", PackWording.cover(true, 0, 0, 0, null));
        assertEquals("4 post-days measured, 1 short, 2 with no one rostered.", PackWording.coverDetail(true, 4, 1, 2));
        assertEquals("4 post-days measured.", PackWording.coverDetail(true, 4, 0, 0));
        assertEquals("", PackWording.coverDetail(false, 4, 1, 1));
    }

    @Test
    void wordsPatrolsIncidentsVisitorsBookAndShifts() {
        assertEquals("No patrol rounds were due.", PackWording.patrols(0, 0, 0, 0, 0, 0, null));
        assertEquals("1 of 3 rounds complete, 1 partial, 1 missed. 8 of 15 checkpoints scanned (53.3%).", PackWording.patrols(3, 1, 1, 1, 15, 8, 53.3));
        assertEquals("No incidents.", PackWording.incidents(0, 0, Map.of()));
        assertEquals("3 incidents (2 High, 1 Low), 1 resolved.", PackWording.incidents(3, 1, Map.of("LOW", 1, "HIGH", 2)));
        assertEquals("1 incident (1 Critical), 0 resolved.", PackWording.incidents(1, 0, Map.of("CRITICAL", 1)));
        assertEquals("No visitors or contractors logged.", PackWording.visitors(0, 0, Map.of()));
        assertEquals("3 logged (1 Contractor, 2 Visitor), 1 with a vehicle.", PackWording.visitors(3, 1, Map.of("VISITOR", 2, "CONTRACTOR", 1)));
        assertEquals("Occurrence Book signed off on 28 of 31 days.", PackWording.book(28, 31));
        assertEquals("20 completed, 2 missed.", PackWording.shifts(Map.of("COMPLETED", 20, "MISSED", 2, "CANCELLED", 0)));
        assertEquals("No shifts in this period.", PackWording.shifts(Map.of()));
        assertEquals("No alarm events.", PackWording.alarms(0));
        assertEquals("1 alarm event.", PackWording.alarms(1));
        assertEquals("4 alarm events.", PackWording.alarms(4));
    }

    // ---- wiring, read from the source ----

    private static String src(String dir, String f) throws Exception {
        for (String root : new String[]{"src/main/java", "platform/src/main/java", "work/src/main/java"}) {
            Path p = Path.of(root, "za/co/handyflow/platform/security", dir, f);
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing " + f); return "";
    }

    @Test
    void sendingIsManagerOnlyAndEveryRouteIsFeatureGuarded() throws Exception {
        String c = src("api", "MonthlyPackEmailController.java");
        assertEquals(6, count(c, "@PreAuthorize(\"hasAuthority('SECURITY_MANAGE')\")"));   // 4 report routes + the contact setting (get, put)
        assertEquals(0, count(c, "SECURITY_READ"));
        assertEquals(6, count(c, "featureGuard.requireModule(\"security\");"));
    }

    @Test
    void sendChecksEverythingBeforeAnyMailGoesOutAndAuditsBeforeSending() throws Exception {
        String s = src("application/internal", "MonthlyPackEmailService.java");
        int send = s.indexOf("Sent send(TenantId tenantId, UUID siteId, UUID actor, String monthAsked, Instant now)");
        String body = s.substring(send, s.indexOf("static List<Line> lines"));
        int mailCall = body.indexOf("mail.send(");
        for (String guard : new String[]{"mail == null", "requireRecipients(", "requireContact(", "requireFinished(", "requireNotJustSent(", "audit(tenant, actor, siteId, \"PACK_EMAILED\""})
            assertTrue(body.indexOf(guard) > 0 && body.indexOf(guard) < mailCall, guard);
        assertFalse(body.contains("//ReportEmailRules") || body.contains("// ReportEmailRules") || body.contains("//audit") || body.contains("// audit"), "no check may be commented out");
        assertEquals(1, count(body, "mail.send("));
        assertTrue(body.contains("try { mail.send(r.email(), subject, html); }"));
    }

    @Test
    void theEmailUsesTheSameAlarmWordingAsThePage() throws Exception {
        String s = src("application/internal", "MonthlyPackEmailService.java");
        assertTrue(s.contains("PackWording.alarms(p.alarms())"));
    }

    @Test
    void theReportCarriesNoPersonalDetail() throws Exception {
        String s = src("application/internal", "MonthlyPackEmailService.java");
        String lines = s.substring(s.indexOf("static List<Line> lines"), s.indexOf("private List<Recipient> list"));
        for (String banned : new String[]{"guardName", "visitorName", "plate", "title()", "description"}) assertFalse(lines.contains(banned), banned);
    }

    @Test
    void recipientQueriesAreTenantScopedAndRemovalKeepsHistory() throws Exception {
        String s = src("application/internal", "MonthlyPackEmailService.java");
        assertFalse(s.contains("DELETE FROM"));
        assertTrue(s.contains("SET active = FALSE, removed_at = now(), removed_by = ?"));
        assertTrue(s.contains("WHERE id = ? AND tenant_id = ? AND site_id = ? AND active"));
        assertTrue(s.contains("WHERE tenant_id = ? AND site_id = ? AND active ORDER BY"));
    }

    private static int count(String s, String x) { int n = 0, i = 0; while ((i = s.indexOf(x, i)) >= 0) { n++; i += x.length(); } return n; }
}
