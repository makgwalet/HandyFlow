package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SiteRateTest {

    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";
    private static LocalDate d(String s) { return LocalDate.parse(s); }
    private static SiteRateRules.Rate rate(String from, String to) {
        return new SiteRateRules.Rate(UUID.randomUUID(), "HOURLY", 5000, d(from), to == null ? null : d(to));
    }
    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }

    // ── validation ──────────────────────────────────────────────────────────

    @Test
    void aSensibleRatePasses() {
        assertDoesNotThrow(() -> SiteRateRules.validate("HOURLY", 8500L, d("2026-12-01"), null));
        assertDoesNotThrow(() -> SiteRateRules.validate("MONTHLY_FIXED", 4_500_000L, d("2026-12-01"), d("2027-11-30")));
        assertDoesNotThrow(() -> SiteRateRules.validate("HOURLY", 8500L, d("2026-12-01"), d("2026-12-01")));
    }

    @Test
    void theTypeMustBeKnown() {
        assertEquals("RATE_TYPE", code(() -> SiteRateRules.validate("DAILY", 100L, d("2026-12-01"), null)));
        assertEquals("RATE_TYPE", code(() -> SiteRateRules.validate(null, 100L, d("2026-12-01"), null)));
    }

    @Test
    void theAmountMustBePositiveAndNotAbsurd() {
        assertEquals("RATE_AMOUNT", code(() -> SiteRateRules.validate("HOURLY", 0L, d("2026-12-01"), null)));
        assertEquals("RATE_AMOUNT", code(() -> SiteRateRules.validate("HOURLY", -5L, d("2026-12-01"), null)));
        assertEquals("RATE_AMOUNT", code(() -> SiteRateRules.validate("HOURLY", null, d("2026-12-01"), null)));
        assertDoesNotThrow(() -> SiteRateRules.validate("HOURLY", SiteRateRules.MAX_HOURLY_CENTS, d("2026-12-01"), null));
        assertEquals("RATE_TOO_HIGH", code(() -> SiteRateRules.validate("HOURLY", SiteRateRules.MAX_HOURLY_CENTS + 1, d("2026-12-01"), null)));
        assertDoesNotThrow(() -> SiteRateRules.validate("MONTHLY_FIXED", SiteRateRules.MAX_HOURLY_CENTS + 1, d("2026-12-01"), null));
        assertDoesNotThrow(() -> SiteRateRules.validate("MONTHLY_FIXED", SiteRateRules.MAX_MONTHLY_CENTS, d("2026-12-01"), null));
        assertEquals("RATE_TOO_HIGH", code(() -> SiteRateRules.validate("MONTHLY_FIXED", SiteRateRules.MAX_MONTHLY_CENTS + 1, d("2026-12-01"), null)));
    }

    @Test
    void theDatesMustMakeSense() {
        assertEquals("RATE_FROM", code(() -> SiteRateRules.validate("HOURLY", 100L, null, null)));
        assertEquals("RATE_DATES", code(() -> SiteRateRules.validate("HOURLY", 100L, d("2026-12-02"), d("2026-12-01"))));
        assertEquals("RATE_DATE", code(() -> SiteRateRules.validate("HOURLY", 100L, d("1999-12-31"), null)));
        assertEquals("RATE_DATE", code(() -> SiteRateRules.validate("HOURLY", 100L, d("2101-01-01"), null)));
        assertDoesNotThrow(() -> SiteRateRules.validate("HOURLY", 100L, d("2000-01-01"), null));
        assertEquals("RATE_DATE", code(() -> SiteRateRules.validate("HOURLY", 100L, d("2026-12-01"), d("2101-01-01"))));
    }

    // ── overlap ─────────────────────────────────────────────────────────────

    @Test
    void thePlanForANewSiteIsEmpty() {
        SiteRateRules.Plan p = SiteRateRules.plan(List.of(), d("2026-12-01"), null);
        assertNull(p.closeId());
        assertNull(p.closeTo());
    }

    @Test
    void aNewOpenRateEndsTheCurrentOpenRateTheDayBefore() {
        SiteRateRules.Rate open = rate("2026-01-01", null);
        SiteRateRules.Plan p = SiteRateRules.plan(List.of(open), d("2026-12-01"), null);
        assertEquals(open.id(), p.closeId());
        assertEquals(d("2026-11-30"), p.closeTo());
    }

    @Test
    void aNewOpenRateStartingTheSameDayAsTheCurrentOneIsRefused() {
        assertEquals("RATE_OVERLAP", code(() -> SiteRateRules.plan(List.of(rate("2026-12-01", null)), d("2026-12-01"), null)));
        assertEquals("RATE_OVERLAP", code(() -> SiteRateRules.plan(List.of(rate("2026-12-05", null)), d("2026-12-01"), null)));
    }

    @Test
    void aClosedRateIsNeverClosedOrOverlapped() {
        assertEquals("RATE_OVERLAP", code(() -> SiteRateRules.plan(List.of(rate("2026-01-01", "2026-06-30")), d("2026-06-30"), null)));
        assertDoesNotThrow(() -> SiteRateRules.plan(List.of(rate("2026-01-01", "2026-06-30")), d("2026-07-01"), null));
        assertEquals("RATE_OVERLAP", code(() -> SiteRateRules.plan(List.of(rate("2026-01-01", "2026-06-30")), d("2026-03-01"), d("2026-03-31"))));
    }

    @Test
    void aBoundedRateInsideAnOpenRateMustEndTheOpenOneFirst() {
        assertEquals("RATE_OVERLAP", code(() -> SiteRateRules.plan(List.of(rate("2026-01-01", null)), d("2026-06-01"), d("2026-06-30"))));
    }

    @Test
    void touchingRatesDoNotOverlap() {
        assertDoesNotThrow(() -> SiteRateRules.plan(List.of(rate("2026-01-01", "2026-06-30")), d("2026-07-01"), d("2026-12-31")));
        assertDoesNotThrow(() -> SiteRateRules.plan(List.of(rate("2026-07-01", "2026-12-31")), d("2026-01-01"), d("2026-06-30")));
        assertEquals("RATE_OVERLAP", code(() -> SiteRateRules.plan(List.of(rate("2026-07-01", "2026-12-31")), d("2026-01-01"), d("2026-07-01"))));
    }

    @Test
    void aGapBetweenRatesIsAllowed() {
        assertDoesNotThrow(() -> SiteRateRules.plan(List.of(rate("2026-01-01", "2026-03-31")), d("2026-06-01"), null));
    }

    // ── which rate applies ───────────────────────────────────────────────────

    @Test
    void theRateInForceOnADayIsFoundIncludingBothEnds() {
        SiteRateRules.Rate old = rate("2026-01-01", "2026-06-30");
        SiteRateRules.Rate now = rate("2026-07-01", null);
        List<SiteRateRules.Rate> all = List.of(old, now);
        assertEquals(old.id(), SiteRateRules.rateOn(all, d("2026-01-01")).orElseThrow().id());
        assertEquals(old.id(), SiteRateRules.rateOn(all, d("2026-06-30")).orElseThrow().id());
        assertEquals(now.id(), SiteRateRules.rateOn(all, d("2026-07-01")).orElseThrow().id());
        assertEquals(now.id(), SiteRateRules.rateOn(all, d("2030-01-01")).orElseThrow().id());
        assertTrue(SiteRateRules.rateOn(all, d("2025-12-31")).isEmpty());
        assertTrue(SiteRateRules.rateOn(all, null).isEmpty());
        assertTrue(SiteRateRules.rateOn(List.of(), d("2026-07-01")).isEmpty());
    }

    // ── ending ──────────────────────────────────────────────────────────────

    @Test
    void onlyAnOpenRateCanBeEndedAndNotBeforeItStarted() {
        SiteRateRules.Rate open = rate("2026-07-01", null);
        assertEquals(d("2026-12-31"), SiteRateRules.requireEndable(open, d("2026-12-31")));
        assertEquals(d("2026-07-01"), SiteRateRules.requireEndable(open, d("2026-07-01")));
        assertEquals("RATE_DATES", code(() -> SiteRateRules.requireEndable(open, d("2026-06-30"))));
        assertEquals("RATE_ALREADY_ENDED", code(() -> SiteRateRules.requireEndable(rate("2026-01-01", "2026-06-30"), d("2026-12-31"))));
        assertEquals("RATE_NOT_FOUND", code(() -> SiteRateRules.requireEndable(null, d("2026-12-31"))));
        assertEquals("RATE_END", code(() -> SiteRateRules.requireEndable(open, null)));
        assertEquals("RATE_DATE", code(() -> SiteRateRules.requireEndable(open, d("2101-01-01"))));
    }

    // ── what the code does ──────────────────────────────────────────────────

    private static String src(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    @Test
    void addingLocksTheSiteAndChecksBeforeItWrites() throws Exception {
        String s = src("application/internal/SiteRateService.java");
        int add = s.indexOf("public Row add");
        String body = s.substring(add, s.indexOf("public Row end"));
        int validate = body.indexOf("SiteRateRules.validate(");
        int lock = body.indexOf("requireSite(tenantId, siteId, true)");
        int plan = body.indexOf("SiteRateRules.plan(");
        int firstWrite = Math.min(body.indexOf("UPDATE security_site_rates"), body.indexOf("INSERT INTO security_site_rates"));
        assertTrue(validate >= 0 && validate < lock && lock < plan && plan < firstWrite, "validate, lock, plan, then write");
        assertTrue(s.contains("FOR UPDATE"));
        assertTrue(s.contains("@Transactional\n    public Row add") && s.contains("@Transactional\n    public Row end"));
    }

    @Test
    void everyQueryIsTenantScopedAndRatesAreNeverDeleted() throws Exception {
        String s = src("application/internal/SiteRateService.java");
        assertFalse(s.contains("DELETE FROM"), "rates are never deleted");
        for (String q : s.split("jdbc\\.(query|update|queryForList|queryForObject)\\(")) {
            if (!q.contains("security_site_rates") && !q.contains("security_sites")) continue;
            String head = q.substring(0, Math.min(q.length(), 420));
            if (head.contains("INSERT INTO")) { assertTrue(head.contains("tenant_id"), "insert without a tenant"); continue; }
            assertTrue(head.contains("tenant_id = ?"), "tenant scope missing near: " + head.substring(0, Math.min(head.length(), 80)));
        }
    }

    @Test
    void bothChangesAreAudited() throws Exception {
        String s = src("application/internal/SiteRateService.java");
        assertTrue(s.contains("\"RATE_ADDED\"") && s.contains("\"RATE_ENDED\""));
        assertEquals(3, count(s, "audit(t, actor"));
        assertTrue(s.contains("'SITE_RATE'"));
    }

    @Test
    void theEndpointsNeedAdminCheckTheScopeAndTheModule() throws Exception {
        String c = src("api/SiteRateController.java");
        assertEquals(3, count(c, "@PreAuthorize(\"hasAuthority('SECURITY_ADMIN')\")"));
        assertEquals(3, count(c, "featureGuard.requireModule(\"security\");\n        site(siteId);"));
        assertTrue(c.contains("required = false"));
        assertFalse(c.contains("tenantId\""), "the company never comes from the request");
        assertTrue(c.contains("TenantContext.getCurrentUserId(), siteId, r.rateType()"));
    }

    @Test
    void theMigrationKeepsRatesSafe() throws Exception {
        String m = Files.readString(Path.of("src/main/resources/db/migration/V863__security_site_rates.sql"));
        assertTrue(m.contains("REFERENCES security_sites(id)") && m.contains("REFERENCES tenants(id)"));
        assertFalse(m.contains("ON DELETE CASCADE"), "a site's rate history must not vanish silently");
        assertTrue(m.contains("CHECK (amount_cents > 0)"));
        assertTrue(m.contains("CHECK (rate_type IN ('HOURLY', 'MONTHLY_FIXED'))"));
        assertTrue(m.contains("CHECK (effective_to IS NULL OR effective_to >= effective_from)"));
        assertTrue(m.contains("WHERE effective_to IS NULL"));
    }

    private static int count(String s, String what) {
        int n = 0, i = 0;
        while ((i = s.indexOf(what, i)) >= 0) { n++; i += what.length(); }
        return n;
    }
}
