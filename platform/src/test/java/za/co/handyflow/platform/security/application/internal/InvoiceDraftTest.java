package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceDraftTest {

    private static final String INT = "src/main/java/za/co/handyflow/platform/security/application/internal/";
    private static final String API = "src/main/java/za/co/handyflow/platform/security/api/";

    private static String read(String p) {
        try { return Files.readString(Path.of(p)); } catch (java.io.IOException e) { throw new IllegalStateException(p, e); }
    }

    private static String code(Runnable r) {
        try { r.run(); return "OK"; } catch (HandyFlowException e) { return e.getStatus().name() + " " + e.getErrorCode(); }
    }

    private static String method(String src, String name) {
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("(public|private|static)[^;=(]*\\s" + name + "\\(").matcher(src);
        assertTrue(mm.find(), "missing method " + name);
        int i = mm.start();
        int open = src.indexOf('{', i), depth = 0, j = open;
        for (; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) break;
        }
        return src.substring(i, j + 1);
    }

    // ---- rules ----

    @Test void oneLiveDraftPerMonth() {
        assertEquals("OK", code(() -> InvoiceDraftRules.requireNoLiveDraft(List.of())));
        assertEquals("OK", code(() -> InvoiceDraftRules.requireNoLiveDraft(List.of("VOID", "VOID"))));
        assertEquals("CONFLICT DRAFT_EXISTS", code(() -> InvoiceDraftRules.requireNoLiveDraft(List.of("DRAFT"))));
        assertEquals("CONFLICT DRAFT_EXISTS", code(() -> InvoiceDraftRules.requireNoLiveDraft(List.of("VOID", "APPROVED"))));
    }

    @Test void onlyADraftIsApproved() {
        assertEquals("OK", code(() -> InvoiceDraftRules.requireApprovable("DRAFT", false, 100, 0, false, false)));
        assertEquals("CONFLICT DRAFT_NOT_DRAFT", code(() -> InvoiceDraftRules.requireApprovable("APPROVED", false, 100, 0, false, true)));
        assertEquals("CONFLICT DRAFT_NOT_DRAFT", code(() -> InvoiceDraftRules.requireApprovable("VOID", false, 100, 0, false, true)));
        assertEquals("CONFLICT DRAFT_NOT_DRAFT", code(() -> InvoiceDraftRules.requireApprovable(null, false, 100, 0, false, true)));
    }

    @Test void monthInProgressAndEmptyAreRefusedEvenWhenAcknowledged() {
        assertEquals("CONFLICT MONTH_IN_PROGRESS", code(() -> InvoiceDraftRules.requireApprovable("DRAFT", true, 100, 0, false, true)));
        assertEquals("CONFLICT DRAFT_EMPTY", code(() -> InvoiceDraftRules.requireApprovable("DRAFT", false, 0, 0, false, true)));
        assertEquals("CONFLICT DRAFT_EMPTY", code(() -> InvoiceDraftRules.requireApprovable("DRAFT", false, -1, 0, false, true)));
        assertEquals("OK", code(() -> InvoiceDraftRules.requireApprovable("DRAFT", false, 1, 0, false, false)));
    }

    @Test void warningsNeedAnAcknowledgement() {
        assertEquals("CONFLICT DRAFT_WARNINGS", code(() -> InvoiceDraftRules.requireApprovable("DRAFT", false, 100, 2.5, false, false)));
        assertEquals("CONFLICT DRAFT_WARNINGS", code(() -> InvoiceDraftRules.requireApprovable("DRAFT", false, 100, 0, true, false)));
        assertEquals("CONFLICT DRAFT_WARNINGS", code(() -> InvoiceDraftRules.requireApprovable("DRAFT", false, 100, 1, true, false)));
        assertEquals("OK", code(() -> InvoiceDraftRules.requireApprovable("DRAFT", false, 100, 2.5, false, true)));
        assertEquals("OK", code(() -> InvoiceDraftRules.requireApprovable("DRAFT", false, 100, 0, true, true)));
        String both = assertThrows(HandyFlowException.class, () -> InvoiceDraftRules.requireApprovable("DRAFT", false, 100, 1, true, false)).getMessage();
        assertTrue(both.contains("no contract rate") && both.contains("VAT"), both);
    }

    @Test void voidingNeedsAReasonAndAnExistingLiveDraft() {
        assertEquals("OK", code(() -> InvoiceDraftRules.requireVoidable("DRAFT", "Wrong site rate")));
        assertEquals("OK", code(() -> InvoiceDraftRules.requireVoidable("APPROVED", "Client disputed hours")));
        assertEquals("CONFLICT DRAFT_ALREADY_VOID", code(() -> InvoiceDraftRules.requireVoidable("VOID", "Wrong site rate")));
        assertEquals("NOT_FOUND DRAFT_NOT_FOUND", code(() -> InvoiceDraftRules.requireVoidable("SOMETHING", "Wrong site rate")));
        assertEquals("BAD_REQUEST DRAFT_REASON", code(() -> InvoiceDraftRules.requireVoidable("DRAFT", null)));
        assertEquals("BAD_REQUEST DRAFT_REASON", code(() -> InvoiceDraftRules.requireVoidable("DRAFT", "  abc ")));
        assertEquals("BAD_REQUEST DRAFT_REASON", code(() -> InvoiceDraftRules.requireVoidable("DRAFT", "x".repeat(501))));
        assertEquals("Wrong", InvoiceDraftRules.requireVoidable("DRAFT", "  Wrong ")); // 5 characters after trimming
        assertEquals("x".repeat(500), InvoiceDraftRules.requireVoidable("DRAFT", "x".repeat(500)));
    }

    @Test void notesAreOptionalButBounded() {
        assertNull(InvoiceDraftRules.cleanNote(null));
        assertNull(InvoiceDraftRules.cleanNote("   "));
        assertEquals("PO 4471", InvoiceDraftRules.cleanNote("  PO 4471 "));
        assertEquals("BAD_REQUEST DRAFT_NOTE", code(() -> InvoiceDraftRules.cleanNote("n".repeat(501))));
        assertEquals("OK", code(() -> InvoiceDraftRules.cleanNote("n".repeat(500))));
    }

    @Test void monthMustLookLikeAMonth() {
        assertEquals("2026-10", InvoiceDraftRules.requireMonth("2026-10"));
        assertEquals("2026-01", InvoiceDraftRules.requireMonth("2026-01"));
        assertEquals("2026-12", InvoiceDraftRules.requireMonth("2026-12"));
        for (String bad : new String[]{null, "", "2026-13", "2026-00", "2026-1", "26-10", "2026-10-01", " 2026-10", "2026/10"}) {
            assertEquals("BAD_REQUEST DRAFT_MONTH", code(() -> InvoiceDraftRules.requireMonth(bad)), String.valueOf(bad));
        }
    }

    // ---- service ----

    @Test void everySqlStatementIsTenantScoped() {
        String s = read(INT + "InvoiceDraftService.java");
        for (String name : new String[]{"list", "get", "create", "approve", "voidDraft", "lock", "rowOf"}) {
            String m = method(s, name);
            String[] parts = m.split("jdbc\\.(query|queryForList|queryForObject|update)\\(");
            assertTrue(parts.length > 1, name + " should run SQL");
            for (int k = 1; k < parts.length; k++) {
                String q = parts[k];
                int end = q.indexOf(");");
                String stmt = end > 0 ? q.substring(0, end) : q;
                if (stmt.contains("INSERT INTO security_audit_log")) continue;
                assertTrue(stmt.contains("tenant_id"), name + ": " + stmt);
            }
        }
        assertTrue(method(s, "audit").contains("tenant_id"));
    }

    @Test void draftsAreReachedOnlyThroughTheirSite() {
        String s = read(INT + "InvoiceDraftService.java");
        assertTrue(method(s, "list").contains("site_id = ?"));
        assertTrue(method(s, "lock").contains("id = ? AND tenant_id = ? AND site_id = ? FOR UPDATE"));
        assertTrue(method(s, "get").contains("id = ? AND tenant_id = ? AND site_id = ?"));
    }

    @Test void approveAndVoidLockTheRowAndNeverDelete() {
        String s = read(INT + "InvoiceDraftService.java");
        assertFalse(s.contains("DELETE FROM"), "drafts are kept for the record");
        assertTrue(method(s, "approve").contains("lock(t, siteId, id)"));
        assertTrue(method(s, "voidDraft").contains("lock(t, siteId, id)"));
        assertTrue(method(s, "approve").contains("AND status = 'DRAFT'"), "the update itself must still require DRAFT");
        assertTrue(method(s, "create").contains("security_sites WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL FOR UPDATE"), "create locks the site so two drafts cannot race");
    }

    @Test void checksRunBeforeAnyWrite() {
        String s = read(INT + "InvoiceDraftService.java");
        String a = method(s, "approve");
        assertTrue(a.indexOf("requireApprovable") < a.indexOf("separationOfDuties.require"));
        assertTrue(a.indexOf("separationOfDuties.require") < a.indexOf("UPDATE security_invoice_drafts"));
        assertTrue(a.indexOf("UPDATE security_invoice_drafts") < a.indexOf("audit("));
        String c = method(s, "create");
        assertTrue(c.indexOf("requireMonth") < c.indexOf("INSERT INTO security_invoice_drafts"));
        assertTrue(c.indexOf("requireNoLiveDraft") < c.indexOf("billing.preview"));
        assertTrue(c.indexOf("billing.preview") < c.indexOf("INSERT INTO security_invoice_drafts"));
        String v = method(s, "voidDraft");
        assertTrue(v.indexOf("requireVoidable") < v.indexOf("UPDATE security_invoice_drafts"));
    }

    @Test void theSecondPersonRuleUsesTheSharedCheckerWithTheSaverAsEarlier() {
        String a = method(read(INT + "InvoiceDraftService.java"), "approve");
        assertTrue(a.contains("separationOfDuties != null"));
        assertTrue(a.contains("APPROVE_PERMISSION, r.createdBy(), actor,"), a);
        assertTrue(read(INT + "InvoiceDraftService.java").contains("APPROVE_PERMISSION = \"SECURITY_ADMIN\""));
    }

    @Test void theSnapshotAndSummaryColumnsComeFromTheSameBillingResult() {
        String c = method(read(INT + "InvoiceDraftService.java"), "create");
        assertTrue(c.contains("p.basis(), p.currency(), p.totalCents(), p.inProgress(), p.unpricedHours(), p.mixedVat(), snapshot"));
        assertTrue(c.contains("mapper.writeValueAsString(p)"));
        assertTrue(c.contains("'DRAFT'"));
    }

    @Test void everyChangeIsAudited() {
        String s = read(INT + "InvoiceDraftService.java");
        assertTrue(method(s, "create").contains("\"DRAFT_CREATED\""));
        assertTrue(method(s, "approve").contains("\"DRAFT_APPROVED\""));
        assertTrue(method(s, "voidDraft").contains("\"DRAFT_VOIDED\""));
        assertTrue(method(s, "audit").contains("'INVOICE_DRAFT'"));
        assertEquals(3, s.split("audit\\(t, actor", -1).length - 1);
    }

    // ---- controller ----

    @Test void everyEndpointIsAdminFeatureGuardedAndScopeChecked() {
        String c = read(API + "InvoiceDraftController.java");
        assertEquals(6, c.split("@(Get|Post)Mapping").length - 1);
        assertEquals(6, c.split("hasAuthority\\('SECURITY_ADMIN'\\)", -1).length - 1);
        for (String name : new String[]{"list", "get", "create", "approve", "voidDraft"}) {
            String m = method(c, name);
            assertTrue(m.contains("featureGuard.requireModule(\"security\")"), name);
            assertTrue(m.contains("site(siteId)"), name);
            assertTrue(m.indexOf("site(siteId)") < m.indexOf("service."), name + " must check scope before the service");
        }
    }

    @Test void controllerPassesTheCallerNotAnyoneElse() {
        String c = read(API + "InvoiceDraftController.java");
        for (String name : new String[]{"create", "approve", "voidDraft"}) {
            assertTrue(method(c, name).contains("TenantContext.getCurrentUserId()"), name);
        }
        assertTrue(method(c, "approve").contains("Boolean.TRUE.equals(r.acknowledgeWarnings())"));
        assertTrue(method(c, "create").contains("HttpStatus.CREATED"));
    }

    // ---- migration ----

    @Test void migrationKeepsDraftsAndLimitsLiveOnesPerMonth() {
        String m = read("src/main/resources/db/migration/V864__security_invoice_drafts.sql");
        assertTrue(m.contains("uq_security_invoice_drafts_live"));
        assertTrue(m.contains("WHERE status <> 'VOID'"));
        assertTrue(m.contains("REFERENCES security_sites"));
        assertFalse(m.contains("ON DELETE CASCADE"));
        assertTrue(m.contains("status IN ('DRAFT', 'APPROVED', 'VOID')") || m.contains("status IN ('DRAFT','APPROVED','VOID')"));
    }
}
