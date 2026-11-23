package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.OccurrenceBookRules.Kind;
import za.co.handyflow.platform.security.application.internal.OccurrenceBookRules.Line;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OccurrenceBookRulesTest {

    private static String code(Runnable r) {
        try { r.run(); return "none"; } catch (HandyFlowException e) { return e.getErrorCode(); }
    }

    @Test
    void dayIsASouthAfricanCalendarDay() {
        Instant[] w = OccurrenceBookRules.window(LocalDate.of(2026, 11, 20));
        assertEquals(Instant.parse("2026-11-19T22:00:00Z"), w[0]);
        assertEquals(Instant.parse("2026-11-20T22:00:00Z"), w[1]);
    }

    @Test
    void defaultsToTodayInSouthAfricaNotUtc() {
        // 23:30 UTC on the 20th is already the 21st in Johannesburg
        assertEquals(LocalDate.of(2026, 11, 21), OccurrenceBookRules.dayOrToday(null, Instant.parse("2026-11-20T23:30:00Z")));
    }

    @Test
    void futureDayRefusedPastAndTodayAllowed() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        assertEquals("OB_FUTURE_DAY", code(() -> OccurrenceBookRules.dayOrToday(LocalDate.of(2026, 11, 21), now)));
        assertEquals(LocalDate.of(2026, 11, 20), OccurrenceBookRules.dayOrToday(LocalDate.of(2026, 11, 20), now));
        assertEquals(LocalDate.of(2020, 1, 1), OccurrenceBookRules.dayOrToday(LocalDate.of(2020, 1, 1), now));
    }

    @Test
    void textIsTrimmedAndBounded() {
        assertEquals("abc", OccurrenceBookRules.cleanText("  abc "));
        assertEquals("OB_TEXT_INVALID", code(() -> OccurrenceBookRules.cleanText("ab")));
        assertEquals("OB_TEXT_INVALID", code(() -> OccurrenceBookRules.cleanText("   ")));
        assertEquals("OB_TEXT_INVALID", code(() -> OccurrenceBookRules.cleanText(null)));
        assertEquals(2000, OccurrenceBookRules.cleanText("x".repeat(2000)).length());
        assertEquals("OB_TEXT_INVALID", code(() -> OccurrenceBookRules.cleanText("x".repeat(2001))));
    }

    @Test
    void guardsMayNotWriteCorrectionsOrSystemKinds() {
        assertEquals(Kind.NOTE, OccurrenceBookRules.manualKind("note", false));
        assertEquals(Kind.HANDOVER, OccurrenceBookRules.manualKind("HANDOVER", false));
        assertEquals("OB_KIND_INVALID", code(() -> OccurrenceBookRules.manualKind("CORRECTION", false)));
        assertEquals(Kind.CORRECTION, OccurrenceBookRules.manualKind("CORRECTION", true));
        assertEquals("OB_KIND_INVALID", code(() -> OccurrenceBookRules.manualKind("INCIDENT", true)));
        assertEquals("OB_KIND_INVALID", code(() -> OccurrenceBookRules.manualKind("ON_DUTY", true)));
        assertEquals("OB_KIND_INVALID", code(() -> OccurrenceBookRules.manualKind(null, true)));
    }

    @Test
    void linesAreOldestFirstAndStable() {
        UUID g = UUID.randomUUID();
        Line a = new Line(Instant.parse("2026-11-20T08:00:00Z"), Kind.NOTE, "b", g, "G", null);
        Line b = new Line(Instant.parse("2026-11-20T07:00:00Z"), Kind.INCIDENT, "z", g, "G", null);
        Line c = new Line(Instant.parse("2026-11-20T08:00:00Z"), Kind.ON_DUTY, "a", g, "G", null);
        List<Line> out = OccurrenceBookRules.order(List.of(a, b, c));
        assertSame(b, out.get(0));
        assertSame(c, out.get(1));
        assertSame(a, out.get(2));
    }

    @Test
    void refKept_blankDropped_longRefused() {
        assertNull(OccurrenceBookRules.cleanRef("  "));
        assertNull(OccurrenceBookRules.cleanRef(null));
        assertEquals("r1", OccurrenceBookRules.cleanRef(" r1 "));
        assertEquals("OB_REF_INVALID", code(() -> OccurrenceBookRules.cleanRef("x".repeat(65))));
    }

    // ---- source scans: privacy and scoping of the query ----

    private static String sql() throws Exception {
        String s = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/OccurrenceBookRules.java"));
        return s.substring(s.indexOf("DAY_SQL"));
    }

    @Test
    void queryNeverReadsPersonalVisitorFields() throws Exception {
        String q = sql();
        for (String f : new String[]{"person_name", "id_number", "driver_name", "phone", "host_contact"}) {
            assertFalse(q.contains(f), "Occurrence Book must not expose " + f);
        }
    }

    @Test
    void everyBranchIsScopedToTenantAndSite() throws Exception {
        String q = sql();
        int branches = q.split("UNION ALL", -1).length;
        assertEquals(9, branches);
        assertEquals(branches, q.split("\\.tenant_id = p\\.t", -1).length - 1, "each branch filters tenant");
        assertEquals(branches, q.split("\\.site_id = p\\.s|sh\\.site_id = p\\.s", -1).length - 1, "each branch filters site");
    }

    @Test
    void deletedIncidentsAreExcluded() throws Exception {
        assertTrue(sql().contains("i.deleted_at IS NULL"));
    }

    @Test
    void migrationIsAppendOnly() throws Exception {
        String m = Files.readString(Path.of("src/main/resources/db/migration/V854__security_ob_entries.sql"));
        assertTrue(m.contains("BEFORE UPDATE OR DELETE"));
        assertTrue(m.contains("RAISE EXCEPTION"));
    }

    @Test
    void serviceHasNoUpdateOrDeleteOfEntries() throws Exception {
        String s = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/OccurrenceBookService.java"));
        assertFalse(s.toUpperCase().contains("UPDATE SECURITY_OB_ENTRIES"));
        assertFalse(s.toUpperCase().contains("DELETE FROM SECURITY_OB_ENTRIES"));
    }

    @Test
    void guardEntryTakesSiteFromSessionNotRequest() throws Exception {
        String c = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/api/OccurrenceBookController.java"));
        assertTrue(c.contains("resolveCurrentSiteForGuard"));
        assertFalse(c.substring(c.indexOf("record EntryRequest"), c.indexOf("private final")).contains("siteId"));
    }

    // ---- chunk 43: sign-off and read audit ----

    @Test
    void signedDayAcceptsOnlyCorrections() {
        assertEquals("OB_DAY_SIGNED", code(() -> OccurrenceBookRules.requireOpenOrCorrection(true, Kind.NOTE)));
        assertEquals("OB_DAY_SIGNED", code(() -> OccurrenceBookRules.requireOpenOrCorrection(true, Kind.HANDOVER)));
        assertEquals("none", code(() -> OccurrenceBookRules.requireOpenOrCorrection(true, Kind.CORRECTION)));
        assertEquals("none", code(() -> OccurrenceBookRules.requireOpenOrCorrection(false, Kind.NOTE)));
    }

    @Test
    void dayOfUsesSouthAfricanTime() {
        assertEquals(LocalDate.of(2026, 11, 21), OccurrenceBookRules.dayOf(Instant.parse("2026-11-20T22:00:00Z")));
        assertEquals(LocalDate.of(2026, 11, 20), OccurrenceBookRules.dayOf(Instant.parse("2026-11-20T21:59:59Z")));
    }

    @Test
    void cannotSignFutureDayButCanSignTodayAndThePast() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        assertEquals("OB_FUTURE_DAY", code(() -> OccurrenceBookRules.requireSignable(LocalDate.of(2026, 11, 21), now)));
        assertEquals("none", code(() -> OccurrenceBookRules.requireSignable(LocalDate.of(2026, 11, 20), now)));
        assertEquals("none", code(() -> OccurrenceBookRules.requireSignable(LocalDate.of(2026, 11, 1), now)));
        assertEquals("OB_DAY_REQUIRED", code(() -> OccurrenceBookRules.requireSignable(null, now)));
    }

    @Test
    void signoffNoteIsOptionalAndBounded() {
        assertNull(OccurrenceBookRules.cleanSignoffNote(null));
        assertNull(OccurrenceBookRules.cleanSignoffNote("  "));
        assertEquals("all quiet", OccurrenceBookRules.cleanSignoffNote(" all quiet "));
        assertEquals(500, OccurrenceBookRules.cleanSignoffNote("x".repeat(500)).length());
        assertEquals("OB_NOTE_INVALID", code(() -> OccurrenceBookRules.cleanSignoffNote("x".repeat(501))));
    }

    @Test
    void readAuditIsWrittenAtMostEveryFifteenMinutes() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        assertTrue(OccurrenceBookRules.viewAuditDue(null, now));
        assertFalse(OccurrenceBookRules.viewAuditDue(now.minusSeconds(60), now));
        assertFalse(OccurrenceBookRules.viewAuditDue(now.minusSeconds(14 * 60), now));
        assertTrue(OccurrenceBookRules.viewAuditDue(now.minusSeconds(15 * 60), now));
        assertTrue(OccurrenceBookRules.viewAuditDue(now.minusSeconds(3600), now));
    }

    @Test
    void entriesAreCheckedAgainstTheSignedDayOfTheirOwnTime() throws Exception {
        String s = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/OccurrenceBookService.java"));
        int add = s.indexOf("public void addEntry");
        String body = s.substring(add, s.indexOf("private String siteName"));
        assertTrue(body.contains("requireOpenOrCorrection"));
        assertTrue(body.contains("OccurrenceBookRules.dayOf(when, zone(tenantId))"));
        assertTrue(body.indexOf("requireOpenOrCorrection") < body.indexOf("INSERT INTO security_ob_entries"), "check before insert");
    }

    @Test
    void signingAndReadingAreAudited() throws Exception {
        String s = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/OccurrenceBookService.java"));
        assertTrue(s.contains("\"OB_SIGNED\""));
        assertTrue(s.contains("auditView(tenantId.getValue(), viewer"));
        assertFalse(s.substring(s.indexOf("public Day day(TenantId tenantId, UUID siteId, LocalDate asked, UUID viewer)") - 60,
                s.indexOf("public Day day(TenantId tenantId, UUID siteId, LocalDate asked, UUID viewer)")).contains("readOnly"),
                "a method that writes the audit trail must not be read-only");
    }

    @Test
    void signingNeedsManagePermissionAndNeverUnsigns() throws Exception {
        String c = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/api/OccurrenceBookController.java"));
        int i = c.indexOf("occurrence-book/sign");
        assertTrue(c.substring(i, i + 120).contains("SECURITY_MANAGE"));
        assertFalse(c.contains("DeleteMapping"));
        String m = Files.readString(Path.of("src/main/resources/db/migration/V855__security_ob_signoffs.sql"));
        assertTrue(m.contains("BEFORE UPDATE OR DELETE"));
        assertTrue(m.contains("UNIQUE (tenant_id, site_id, day)"));
    }
}
