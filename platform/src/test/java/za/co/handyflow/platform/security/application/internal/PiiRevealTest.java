package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class PiiRevealTest {
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }

    @Test void fieldAndReasonAreValidated() {
        assertNull(PiiReveal.problem("ID_NUMBER", "PSIRA renewal paperwork"));
        assertNull(PiiReveal.problem("bank_account", "Payroll query from guard"));
        assertEquals("INVALID_PII_FIELD", PiiReveal.problem("PHONE", "long enough reason"));
        assertEquals("INVALID_PII_FIELD", PiiReveal.problem(null, "long enough reason"));
        assertEquals("REASON_REQUIRED", PiiReveal.problem("ID_NUMBER", "short"));
        assertEquals("REASON_REQUIRED", PiiReveal.problem("ID_NUMBER", "         x   "));
        assertEquals("REASON_REQUIRED", PiiReveal.problem("ID_NUMBER", null));
        assertEquals("REASON_TOO_LONG", PiiReveal.problem("ID_NUMBER", "x".repeat(501)));
    }
    @Test void bankAccountNeedsTheBankingPermission() {
        assertEquals("SECURITY_GUARD_BANKING_MANAGE", PiiReveal.authorityFor(PiiReveal.Field.BANK_ACCOUNT));
        assertEquals("SECURITY_MANAGE", PiiReveal.authorityFor(PiiReveal.Field.ID_NUMBER));
    }
    @Test void auditRecordsFieldAndEscapedReasonButNoValue() {
        String j = PiiReveal.auditJson(PiiReveal.Field.ID_NUMBER, "said \"hi\"\nnew line");
        assertTrue(j.contains("\"field\":\"ID_NUMBER\""));
        assertTrue(j.contains("\\\"hi\\\"") && j.contains("\\n"));
        assertFalse(j.contains("value"));
    }

    // ---- source checks on the wiring ----
    @Test void responsesMaskBothFields() throws Exception {
        String s = read("application/internal/GuardService.java");
        assertTrue(s.contains("PiiMask.maskId(g.getIdNumber())"));
        assertTrue(s.contains("PiiMask.maskAccount(g.getBankAccountNumber())"));
        assertFalse(s.contains("g.getBankAccountNumber(), g.getBankBranchCode()"), "raw account must not be returned");
    }
    @Test void updatesIgnoreMaskedValues() throws Exception {
        String s = read("application/internal/GuardService.java");
        assertTrue(s.contains("PiiMask.keepIfMasked(req.idNumber(), guard.getIdNumber())"));
        assertTrue(s.contains("PiiMask.keepIfMasked(bankAccountNumber, guard.getBankAccountNumber())"));
    }
    @Test void auditIsWrittenBeforeTheValueIsRead() throws Exception {
        String s = read("application/internal/GuardService.java");
        int m = s.indexOf("revealPii(");
        int audit = s.indexOf("writeAudit(tenantId, actorId, guardId, \"PII_REVEALED\"", m);
        int read = s.indexOf("guard.getBankAccountNumber() : guard.getIdNumber()", m);
        assertTrue(m > 0 && audit > m && read > audit, "audit row must come before the value is read");
        assertTrue(s.substring(Math.max(0, m - 80), m).contains("public"));
        assertTrue(s.substring(m - 160, m).contains("@Transactional"));
    }
    @Test void controllerChecksPermissionPerField() throws Exception {
        String s = read("api/GuardController.java");
        assertTrue(s.contains("@PostMapping(\"/{id}/reveal\")"));
        assertTrue(s.contains("\"BANK_ACCOUNT\".equalsIgnoreCase"));
        assertTrue(s.contains("\"SECURITY_GUARD_BANKING_MANAGE\" : \"SECURITY_MANAGE\""));
        assertTrue(s.contains("AccessDeniedException"));
    }
    @Test void apiKeysCannotReveal() throws Exception {
        String s = read("infrastructure/ApiKeyAccess.java");
        assertTrue(s.contains("revoke-tokens|reveal"));
    }
}
