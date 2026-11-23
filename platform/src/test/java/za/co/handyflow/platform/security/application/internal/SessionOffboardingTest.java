package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionOffboardingTest {

    @Test
    void anyStatusButActiveClosesTheSession() {
        assertFalse(SessionOffboarding.mustClose("ACTIVE"));
        assertTrue(SessionOffboarding.mustClose("SUSPENDED"));
        assertTrue(SessionOffboarding.mustClose("ON_LEAVE"));
        assertTrue(SessionOffboarding.mustClose("TERMINATED"));
        assertFalse(SessionOffboarding.mustClose(null));
    }

    @Test
    void theReasonNamesTheStatusAndFitsTheColumn() {
        assertEquals("Guard status set to SUSPENDED: session closed by the system", SessionOffboarding.reasonForStatus("SUSPENDED"));
        assertTrue(SessionOffboarding.reasonForStatus("X".repeat(500)).length() <= SessionOffboarding.REASON_MAX);
        assertTrue(SessionOffboarding.REASON_DELETED.length() <= SessionOffboarding.REASON_MAX);
        assertEquals(200, SessionOffboarding.REASON_MAX);
    }

    private static String service() throws Exception {
        for (String r : new String[]{"src/main", "platform/src/main", "work/src/main"}) {
            Path p = Path.of(r, "java/za/co/handyflow/platform/security/application/internal/GuardService.java");
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing GuardService"); return "";
    }

    private static String method(String s, String signature) {
        int i = s.indexOf(signature);
        assertTrue(i >= 0, "no " + signature);
        int next = s.indexOf("\n    @Transactional", i + signature.length());
        int next2 = s.indexOf("\n    public ", i + signature.length());
        int end = Math.min(next < 0 ? s.length() : next, next2 < 0 ? s.length() : next2);
        return s.substring(i, end);
    }

    @Test
    void aStatusChangeAndADeletionBothCloseTheSession() throws Exception {
        String s = service();
        String status = method(s, "public GuardResponse updateStatus(");
        assertTrue(status.contains("SessionOffboarding.mustClose(req.status())"));
        assertTrue(status.contains("closeOpenSessions(tenantId, id, changedBy,"));
        assertTrue(method(s, "public void deleteGuard(").contains("closeOpenSessions(tenantId, id, deletedBy, SessionOffboarding.REASON_DELETED)"));
    }

    @Test
    void theUpdateIsOnlyForThisCompanyAndGuardAndOnlyOpenSessions() throws Exception {
        String s = service();
        String m = method(s, "private void closeOpenSessions(");
        assertTrue(m.contains("WHERE tenant_id = ? AND guard_id = ? AND ended_at IS NULL"));
        assertTrue(m.contains("forced_close_by = ?, forced_close_reason = ?"));
        assertFalse(m.contains("DELETE"));
    }

    @Test
    void earlierBehaviourIsKept() throws Exception {
        String s = service();
        assertTrue(s.contains("tokenRepository.revokeAllForGuard(id, java.time.Instant.now(),"));
        assertTrue(s.contains("offboardAppAccess(tenantId, id);"));
        assertTrue(s.contains("FACE_DATA_REMOVED"));
        assertTrue(s.contains("PiiMask.keepIfMasked("));
    }
}
