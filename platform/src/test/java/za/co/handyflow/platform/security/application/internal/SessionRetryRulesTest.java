package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.SessionRetryRules.ReturnOutcome.*;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionRetryRulesTest {
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();

    @Test void clockInRepeatOnlyForTheSameGuard() {
        assertTrue(SessionRetryRules.isOpenReplay(A, A));
        assertFalse(SessionRetryRules.isOpenReplay(A, B), "another guard's open session is a conflict, not a replay");
        assertFalse(SessionRetryRules.isOpenReplay(null, A));
    }
    @Test void clockOutRepeatWhenAlreadyClosed() {
        assertTrue(SessionRetryRules.isCloseReplay(true));
        assertFalse(SessionRetryRules.isCloseReplay(false));
    }
    @Test void returnOutcomes() {
        assertEquals(RETURN_NOW, SessionRetryRules.returnOutcome(true, null, "GOOD"));
        assertEquals(REPLAY, SessionRetryRules.returnOutcome(false, "GOOD", "GOOD"));
        assertEquals(CONFLICT, SessionRetryRules.returnOutcome(false, "GOOD", "MISSING"));
        assertEquals(CONFLICT, SessionRetryRules.returnOutcome(false, null, "GOOD"));
    }

    // ---- wiring ----
    @Test void openGuardSessionChecksForReplayFirst() throws Exception {
        String s = read("application/internal/DeviceSessionService.java");
        int m = s.indexOf("public DeviceSessionResponse openGuardSession");
        int replay = s.indexOf("SessionRetryRules.isOpenReplay(", m);
        int open = s.indexOf("return doOpen(", m);
        assertTrue(m > 0 && replay > m && open > replay, "replay check must come before a new session is opened");
    }
    @Test void closeGuardSessionChecksOwnershipBeforeReplay() throws Exception {
        String s = read("application/internal/DeviceSessionService.java");
        int m = s.indexOf("public DeviceSessionResponse closeGuardSession");
        int own = s.indexOf("assertSessionOwnedBy(", m);
        int replay = s.indexOf("SessionRetryRules.isCloseReplay(", m);
        int close = s.indexOf("return doClose(", m);
        assertTrue(own > m && replay > own && close > replay, "ownership, then replay, then the real close");
    }
    @Test void checkoutIsRememberedByClientRef() throws Exception {
        String s = read("application/internal/DeviceSessionService.java");
        int m = s.indexOf("public ResourceCustody checkoutResource");
        int find = s.indexOf("ledger.find(tenantId, GuardClientLedger.Kind.CHECKOUT", m);
        int save = s.indexOf("custodyRepository.save(custody)", m);
        int rec = s.indexOf("ledger.record(tenantId, session.getGuardId(), GuardClientLedger.Kind.CHECKOUT", m);
        assertTrue(find > m && save > find && rec > save, "look up, create, then record");
        assertTrue(s.contains("\"REQUEST_IN_PROGRESS\""));
    }
    @Test void returnUsesTheOutcomeRules() throws Exception {
        String s = read("application/internal/DeviceSessionService.java");
        assertTrue(s.contains("SessionRetryRules.returnOutcome(custody.isCheckedOut()"));
        assertTrue(s.contains("outcome == SessionRetryRules.ReturnOutcome.CONFLICT"));
    }
    @Test void ledgerAndMigrationAndDtoAgree() throws Exception {
        assertTrue(read("application/internal/GuardClientLedger.java").contains("GATE_ENTRY, CHECKOUT }"));
        String m = Files.readString(Path.of("src/main/resources/db/migration/V850__security_client_requests_checkout.sql"));
        for (String k : new String[]{"'SCAN'", "'INCIDENT'", "'DURESS'", "'EVIDENCE'", "'GATE_ENTRY'", "'CHECKOUT'"}) assertTrue(m.contains(k), k);
        String d = read("dto/CheckoutResourceRequest.java");
        assertTrue(d.contains("UUID clientRef"));
        assertTrue(d.contains("public CheckoutResourceRequest(String resourceType, String resourceRef, UUID witnessedBy, String notes)"));
    }
}
