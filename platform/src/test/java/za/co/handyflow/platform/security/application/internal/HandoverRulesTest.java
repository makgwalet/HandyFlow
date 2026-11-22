package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HandoverRulesTest {
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();

    @Test void anotherGuardsSessionIsClosed() { assertTrue(HandoverRules.shouldClose(A, B)); }
    @Test void aGuardsOwnSessionIsKept() { assertFalse(HandoverRules.shouldClose(A, A)); }
    @Test void nothingToCloseWithoutIds() {
        assertFalse(HandoverRules.shouldClose(null, B));
        assertFalse(HandoverRules.shouldClose(A, null));
    }
    @Test void reasonFitsTheColumn() { assertTrue(HandoverRules.REASON.length() <= 200); }

    private static String svc() throws Exception {
        return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/GuardAuthService.java"));
    }

    @Test void handoverClosesTheOpenSessionOnlyAfterEveryCheckAndOnlyForSharedLogin() throws Exception {
        String s = svc();
        int tokens = s.indexOf("Shared device handover\");");
        int close = s.indexOf("open.forceClose(guard.getId(), HandoverRules.REASON)");
        int block = s.lastIndexOf("if (sharedLogin) {", close);
        assertTrue(block > 0 && tokens > block && close > tokens, "inside the sharedLogin block, after the token revoke");
        assertTrue(close < s.indexOf("// 6. PIN change required?"));
        // after the guard-status check, so a refused login never ends the guard on duty's session
        assertTrue(block > s.indexOf("GUARD_NOT_ACTIVE"));
        assertTrue(s.contains("HandoverRules.shouldClose(open.getGuardId(), guard.getId())"));
        assertTrue(s.contains("if (sessionRepository != null && requestDevice.isPresent()) {"), "no extra condition may switch the close off");
        assertTrue(s.contains("sessionRepository.findOpenByDevice(requestDevice.get().getId())"));
    }

    @Test void theShiftIsNotCompletedByAHandover() throws Exception {
        String s = svc();
        String block = s.substring(s.indexOf("HandoverRules.shouldClose("), s.indexOf("// 6. PIN change required?"));
        assertFalse(block.contains("complete()") || block.contains("shiftRepository"), "handover must not complete the shift");
    }
}
