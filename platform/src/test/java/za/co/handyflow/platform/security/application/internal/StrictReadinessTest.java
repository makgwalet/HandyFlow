package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.StrictReadiness.Status.*;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrictReadinessTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private static StrictReadiness.Cert cert(String cat, String expiry, boolean verified) {
        return new StrictReadiness.Cert(cat, expiry == null ? null : LocalDate.parse(expiry), verified);
    }
    private static StrictReadiness.Result run(boolean legacy, StrictReadiness.Cert... c) { return StrictReadiness.classify(List.of(c), legacy, TODAY); }

    @Test void allThreeVerifiedCategoriesIsReady() {
        var r = run(false, cert("HANDGUN", "2027-01-01", true), cert("SHOTGUN", "2027-01-01", true), cert("RIFLE", "2027-01-01", true));
        assertEquals(READY_ALL, r.status());
        assertFalse(StrictReadiness.blockedByStrict(r.status()));
    }

    @Test void oneVerifiedCategoryIsPartialAndNotBlocked() {
        var r = run(false, cert("HANDGUN", "2027-01-01", true));
        assertEquals(READY_PARTIAL, r.status());
        assertEquals(java.util.Set.of("HANDGUN"), r.covered());
        assertFalse(StrictReadiness.blockedByStrict(r.status()));
    }

    @Test void uncategorisedCertificateNeedsACategory() {
        var r = run(false, cert(null, "2027-01-01", true));
        assertEquals(NEEDS_CATEGORY, r.status());
        assertTrue(StrictReadiness.blockedByStrict(r.status()));
        assertTrue(r.hint().contains("category"));
    }

    @Test void categorisedButUnverifiedNeedsVerification() {
        var r = run(false, cert("RIFLE", "2027-01-01", false));
        assertEquals(NEEDS_VERIFICATION, r.status());
        assertTrue(StrictReadiness.blockedByStrict(r.status()));
    }

    @Test void onlyTheOldGuardLevelDateIsLegacyOnly() {
        assertEquals(LEGACY_ONLY, run(true).status());
        assertTrue(StrictReadiness.blockedByStrict(LEGACY_ONLY));
    }

    @Test void expiredCertificatesDoNotCountAndAreNotBlockers() {
        var r = run(false, cert("HANDGUN", "2026-11-20", true));
        assertEquals(EXPIRED_ONLY, r.status());
        assertFalse(StrictReadiness.blockedByStrict(r.status()));
        assertEquals(READY_PARTIAL, run(false, cert("HANDGUN", "2026-11-21", true)).status(), "expires today still counts");
        assertEquals(EXPIRED_ONLY, run(false, cert("HANDGUN", null, true)).status(), "no expiry never counts");
    }

    @Test void aCategorisedVerifiedCertBeatsAnUncategorisedOne() {
        assertEquals(READY_PARTIAL, run(false, cert(null, "2027-01-01", false), cert("SHOTGUN", "2027-01-01", true)).status());
    }

    // ── agrees with the real issue check in CompetencyChecks (strict = true) ──
    @Test void blockedMeansLegacyAllowsSomeCategoryButStrictAllowsNone() {
        record Case(boolean legacy, StrictReadiness.Cert... certs) {}
        Case[] cases = {
            new Case(false, cert(null, "2027-01-01", true)), new Case(false, cert("RIFLE", "2027-01-01", false)), new Case(true),
            new Case(false, cert("HANDGUN", "2027-01-01", true)), new Case(false, cert("HANDGUN", "2026-01-01", true))};
        for (Case c : cases) {
            var certs = java.util.Arrays.stream(c.certs).map(x -> new CompetencyChecks.Cert(x.category(), x.expiry(), x.verified())).toList();
            boolean legacyOk = CompetencyChecks.CATEGORIES.stream().anyMatch(cat -> CompetencyChecks.check(cat, certs, c.legacy, false, TODAY) == null);
            boolean strictAnyOk = CompetencyChecks.CATEGORIES.stream().anyMatch(cat -> CompetencyChecks.check(cat, certs, c.legacy, true, TODAY) == null);
            boolean blocked = StrictReadiness.blockedByStrict(StrictReadiness.classify(java.util.Arrays.asList(c.certs), c.legacy, TODAY).status());
            assertEquals(legacyOk && !strictAnyOk, blocked, String.valueOf(java.util.Arrays.toString(c.certs)) + " legacy=" + c.legacy);
        }
    }

    private static String read(String p) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/" + p)); }

    @Test void endpointIsManageOnlyAndTheReportIgnoresPeopleWhoCarryNoFirearms() throws Exception {
        String c = read("api/CompetencyReadinessController.java");
        assertTrue(c.contains("@GetMapping\n    @PreAuthorize(\"hasAuthority('SECURITY_MANAGE')\")"));
        String s = read("application/internal/CompetencyReadinessService.java");
        assertTrue(s.contains("if (mine.isEmpty() && !legacy) continue;"));
        assertTrue(s.contains("if (!g.isSchedulable()) continue;"));
        assertTrue(s.contains("StrictReadiness.blockedByStrict(r.status())"));
        assertTrue(s.contains("blocked == 0"));
    }
}
