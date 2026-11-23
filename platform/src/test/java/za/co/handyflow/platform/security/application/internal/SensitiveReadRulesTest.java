package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveReadRulesTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    private static final UUID A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID C = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static Map<String, String> vars(Object... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], String.valueOf(kv[i + 1]));
        return m;
    }

    private static SensitiveReadRules.Target t(String pattern, Map<String, String> v) {
        return SensitiveReadRules.classify("GET", pattern, v, TENANT).orElse(null);
    }

    @Test
    void screeningEvidenceIsLoggedAgainstTheGuard() {
        var x = t("/api/v1/security/guards/{guardId}/screening/{screeningId}/evidence/{evidenceId}/download", vars("guardId", A, "screeningId", B, "evidenceId", C));
        assertEquals("GUARD", x.entityType());
        assertEquals(A, x.entityId());
        assertEquals(SensitiveReadRules.ACTION_EVIDENCE, x.action());
        assertEquals(Map.of("kind", "SCREENING_EVIDENCE", "screeningId", B.toString(), "evidenceId", C.toString()), x.metadata());
    }

    @Test
    void competencyEvidenceNamesTheCompetency() {
        var x = t("/api/v1/security/guards/{guardId}/competencies/{id}/evidence/{evidenceId}/download", vars("guardId", A, "id", B, "evidenceId", C));
        assertEquals(A, x.entityId());
        assertEquals(Map.of("kind", "COMPETENCY_EVIDENCE", "competencyId", B.toString(), "evidenceId", C.toString()), x.metadata());
    }

    @Test
    void complaintAndIncidentEvidenceAreLoggedAgainstThatRecord() {
        var c = t("/api/v1/security/complaints/{id}/evidence/{evidenceId}/download", vars("id", A, "evidenceId", B));
        assertEquals("COMPLAINT", c.entityType());
        assertEquals(A, c.entityId());
        var i = t("/api/v1/security/incidents/{id}/evidence/{evidenceId}/download", vars("id", A, "evidenceId", B));
        assertEquals("INCIDENT", i.entityType());
        assertEquals("INCIDENT_EVIDENCE", i.metadata().get("kind"));
    }

    @Test
    void payrollExportsAreLoggedWithTheirFormat() {
        var csv = t("/api/v1/security/payroll/periods/{id}/export/csv", vars("id", A));
        assertEquals("PAYROLL_PERIOD", csv.entityType());
        assertEquals(SensitiveReadRules.ACTION_EXPORT, csv.action());
        assertEquals(Map.of("format", "CSV"), csv.metadata());
        assertEquals(Map.of("format", "JSON"), t("/api/v1/security/payroll/periods/{id}/export/json", vars("id", A)).metadata());
    }

    @Test
    void aPayStatementIsLoggedAgainstTheGuardAndNamesThePeriod() {
        var x = t("/api/v1/security/payroll/periods/{id}/guards/{guardId}/pdf", vars("id", A, "guardId", B));
        assertEquals("GUARD", x.entityType());
        assertEquals(B, x.entityId());
        assertEquals(SensitiveReadRules.ACTION_PAY_STATEMENT, x.action());
        assertEquals(Map.of("periodId", A.toString()), x.metadata());
    }

    @Test
    void vettingAndTheDeclinedRegisterAreLogged() {
        var v = t("/api/v1/security/cp/vetting/principals/{principalId}", vars("principalId", A));
        assertEquals("PRINCIPAL", v.entityType());
        assertEquals(A, v.entityId());
        var d = t("/api/v1/security/cp/vetting/declined", vars());
        assertEquals("DECLINED_REGISTER", d.entityType());
        assertEquals(TENANT, d.entityId());   // a register has no single record: the subject is the company
        assertEquals(SensitiveReadRules.ACTION_DECLINED, d.action());
        assertTrue(SensitiveReadRules.classify("GET", "/api/v1/security/cp/vetting/declined", vars(), null).isEmpty());
    }

    @Test
    void theVettingCompliancePdfIsLoggedAgainstThePrincipal() {
        var x = t("/api/v1/security/cp/principals/{id}/vetting/pdf", vars("id", A));
        assertEquals("PRINCIPAL", x.entityType());
        assertEquals(A, x.entityId());
        assertEquals(SensitiveReadRules.ACTION_VETTING, x.action());
        assertEquals(Map.of("kind", "COMPLIANCE_PDF"), x.metadata());
    }

    @Test
    void onlyGetsOnWatchedRoutesAreLogged() {
        String p = "/api/v1/security/payroll/periods/{id}/export/csv";
        assertTrue(SensitiveReadRules.classify("POST", p, vars("id", A), TENANT).isEmpty());
        assertTrue(SensitiveReadRules.classify("DELETE", p, vars("id", A), TENANT).isEmpty());
        assertTrue(SensitiveReadRules.classify("get", p, vars("id", A), TENANT).isPresent());
        assertTrue(SensitiveReadRules.classify("GET", "/api/v1/security/guards/{id}", vars("id", A), TENANT).isEmpty());
        assertTrue(SensitiveReadRules.classify("GET", "/api/v1/security/guards/{guardId}/documents", vars("guardId", A), TENANT).isEmpty());
        assertTrue(SensitiveReadRules.classify("GET", null, vars(), TENANT).isEmpty());
    }

    @Test
    void aMissingOrMalformedIdIsNeverGuessedAt() {
        String p = "/api/v1/security/guards/{guardId}/screening/{screeningId}/evidence/{evidenceId}/download";
        assertTrue(SensitiveReadRules.classify("GET", p, vars("guardId", A, "screeningId", B), TENANT).isEmpty());
        assertTrue(SensitiveReadRules.classify("GET", p, vars("guardId", "not-a-uuid", "screeningId", B, "evidenceId", C), TENANT).isEmpty());
        assertTrue(SensitiveReadRules.classify("GET", p, null, TENANT).isEmpty());
    }

    @Test
    void onlyASuccessfulAnswerCountsAsHandedOver() {
        assertTrue(SensitiveReadRules.delivered(200, null));
        assertTrue(SensitiveReadRules.delivered(204, null));
        assertFalse(SensitiveReadRules.delivered(199, null));
        assertFalse(SensitiveReadRules.delivered(302, null));
        assertFalse(SensitiveReadRules.delivered(403, null));
        assertFalse(SensitiveReadRules.delivered(404, null));
        assertFalse(SensitiveReadRules.delivered(500, null));
        assertFalse(SensitiveReadRules.delivered(200, new RuntimeException("x")));
    }

    @Test
    void metadataIsSortedQuotedAndFlagsSupportSessions() {
        assertEquals("{}", SensitiveReadRules.json(Map.of(), false));
        assertEquals("{\"format\":\"CSV\"}", SensitiveReadRules.json(Map.of("format", "CSV"), false));
        assertEquals("{\"a\":\"1\",\"b\":\"2\",\"supportSession\":true}", SensitiveReadRules.json(Map.of("b", "2", "a", "1"), true));
        assertEquals("{\"supportSession\":true}", SensitiveReadRules.json(Map.of(), true));
        assertEquals("{\"k\":\"a\\\"b\\\\c\"}", SensitiveReadRules.json(Map.of("k", "a\"b\\c"), false));
    }

    @Test
    void theLogShowsEverythingTheRulesWriteAndTheRevealAction() {
        assertTrue(SensitiveReadRules.LOGGED_ACTIONS.containsAll(List.of("PII_REVEALED", "EVIDENCE_DOWNLOADED", "EXPORTED", "PAY_STATEMENT_VIEWED", "VETTING_VIEWED", "DECLINED_REGISTER_VIEWED")));
        assertEquals(6, SensitiveReadRules.LOGGED_ACTIONS.size());
    }

    @Test
    void limitsAndPlaceholders() {
        assertEquals(50, SensitiveReadAuditService.clamp(null));
        assertEquals(50, SensitiveReadAuditService.clamp(0));
        assertEquals(50, SensitiveReadAuditService.clamp(-5));
        assertEquals(7, SensitiveReadAuditService.clamp(7));
        assertEquals(200, SensitiveReadAuditService.clamp(200));
        assertEquals(200, SensitiveReadAuditService.clamp(5000));
        assertEquals("?,?,?", SensitiveReadAuditService.placeholders(3));
    }

    // ---- the watched patterns are real routes (a typo here would mean silently logging nothing) ----

    private static Path apiDir() {
        for (String root : new String[]{"src/main/java", "platform/src/main/java", "work/src/main/java"}) {
            Path p = Path.of(root, "za/co/handyflow/platform/security/api");
            if (Files.isDirectory(p)) return p;
        }
        fail("no api directory"); return null;
    }

    private static final Pattern CLASS_MAP = Pattern.compile("(?m)^@RequestMapping\\(\"([^\"]*)\"\\)");
    private static final Pattern GET_MAP = Pattern.compile("@GetMapping(?:\\((?:value\\s*=\\s*)?\"([^\"]*)\"[^)]*\\))?");

    private static Set<String> realGetRoutes() throws Exception {
        Set<String> out = new HashSet<>();
        try (Stream<Path> files = Files.list(apiDir())) {
            for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                String s = Files.readString(f);
                Matcher c = CLASS_MAP.matcher(s);
                String prefix = c.find() ? c.group(1) : "";
                Matcher g = GET_MAP.matcher(s);
                while (g.find()) out.add(prefix + (g.group(1) == null ? "" : g.group(1)));
            }
        }
        return out;
    }

    @Test
    void everyWatchedPatternIsARealGetRoute() throws Exception {
        Set<String> real = realGetRoutes();
        for (String p : SensitiveReadRules.patterns()) assertTrue(real.contains(p), "no GET route " + p);
    }

    @Test
    void theRealEvidenceDownloadAndExportRoutesAreAllWatched() throws Exception {
        Set<String> watched = new HashSet<>(SensitiveReadRules.patterns());
        for (String r : realGetRoutes()) {
            boolean looksSensitive = r.endsWith("/download") || r.contains("/export/") || r.endsWith("/vetting/declined") || r.endsWith("/vetting/pdf") || r.matches(".*/payroll/periods/\\{id\\}/guards/\\{guardId\\}/pdf");
            if (looksSensitive && r.startsWith("/api/v1/security/")) assertTrue(watched.contains(r), "unwatched sensitive route " + r);
        }
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
    void theInterceptorActsOnlyAfterTheRequestAndNeverBreaksIt() throws Exception {
        String s = src("infrastructure", "SensitiveReadInterceptor.java");
        assertTrue(s.contains("public void afterCompletion("));
        assertFalse(s.contains("preHandle"));
        assertTrue(s.contains("SensitiveReadRules.delivered(response.getStatus(), ex)"));
        assertTrue(s.contains("catch (RuntimeException e)"));
        assertTrue(s.contains("log.error("));
        assertTrue(s.contains("support ? null : TenantContext.getCurrentUserId()"));
    }

    @Test
    void theInterceptorIsRegisteredOnSecurityRoutesAndTheLogIsAdminOnly() throws Exception {
        assertTrue(src("infrastructure", "SensitiveReadWebConfig.java").contains(".addPathPatterns(\"/api/v1/security/**\")"));
        String c = src("api", "SecurityAccessLogController.java");
        assertTrue(c.contains("@PreAuthorize(\"hasAuthority('SECURITY_ADMIN')\")"));
        assertTrue(c.contains("featureGuard.requireModule(\"security\");"));
        assertTrue(c.contains("TenantContext.getTenantIdAsObject()"));
    }

    @Test
    void theLogQueryIsTenantScopedAndTheWriteHasItsOwnTransaction() throws Exception {
        String s = src("application/internal", "SensitiveReadAuditService.java");
        assertTrue(s.contains("WHERE tenant_id = ? AND action IN ("));
        assertTrue(s.contains("Propagation.REQUIRES_NEW"));
        assertFalse(s.contains("DELETE"));
        assertFalse(s.contains("UPDATE "));
    }
}
