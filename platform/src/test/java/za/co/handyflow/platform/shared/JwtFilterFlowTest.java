package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtFilterFlowTest {

    private final List<String> events = new ArrayList<>();

    private void run(Runnable auth, JwtFilterFlow.Chain chain) throws Exception {
        events.clear(); // each test starts from nothing, whether or not the runner builds a new instance
        JwtFilterFlow.run(auth, e -> events.add("authFailed"), () -> events.add("clear"), chain);
    }

    @Test
    void aGoodTokenAuthenticatesThenRunsTheChainOnceThenClears() throws Exception {
        run(() -> events.add("auth"), () -> events.add("chain"));
        assertEquals(List.of("auth", "chain", "clear"), events);
    }

    @Test
    void aBrokenTokenContinuesUnauthenticatedWithTheChainOnce() throws Exception {
        run(() -> { throw new IllegalStateException("bad token"); }, () -> events.add("chain"));
        assertEquals(List.of("authFailed", "clear", "chain", "clear"), events);
    }

    @Test
    void aFailureInTheChainIsNotRetried() throws Exception {
        int[] calls = {0};
        RuntimeException boom = new RuntimeException("controller blew up");
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> run(() -> events.add("auth"), () -> { calls[0]++; throw boom; }));
        assertSame(boom, thrown);
        assertEquals(1, calls[0]);
        assertEquals(List.of("auth", "clear"), events);
    }

    @Test
    void anIoFailureInTheChainPropagatesAndStillClears() throws Exception {
        assertThrows(IOException.class, () -> run(() -> {}, () -> { throw new IOException("closed"); }));
        assertEquals(List.of("clear"), events);
    }

    @Test
    void aFailureInTheChainAfterABrokenTokenIsStillNotRetried() throws Exception {
        int[] calls = {0};
        assertThrows(RuntimeException.class, () -> run(() -> { throw new RuntimeException("x"); }, () -> { calls[0]++; throw new RuntimeException("y"); }));
        assertEquals(1, calls[0]);
    }

    // ---- wiring, read from the source ----

    private static String filter() throws Exception {
        for (String r : new String[]{"src/main", "platform/src/main", "work/src/main"}) {
            Path p = Path.of(r, "java/za/co/handyflow/platform/shared/JwtAuthFilter.java");
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing JwtAuthFilter"); return "";
    }

    @Test
    void theFilterGoesThroughTheFlowAndCallsTheChainOnce() throws Exception {
        String s = filter();
        assertTrue(s.contains("JwtFilterFlow.run("));
        assertEquals(1, s.split("filterChain\\.doFilter\\(", -1).length - 1);
        assertFalse(s.contains("catch (Exception e) {\n            log.error(\"JWT authentication failed: {}\""));
        assertTrue(s.contains("TenantContext.clear(); SecurityContextHolder.clearContext();"));
    }

    @Test
    void theFilterStillSkipsAdminAndGuardPathsAndChecksTokenVersions() throws Exception {
        String s = filter();
        assertTrue(s.contains("GuardPaths.isGuardPath(uri)"));
        assertTrue(s.contains("staleForTenant(jwt)"));
        assertTrue(s.contains("TenantContext.setImpersonation(impersonation);"));
        assertTrue(s.contains("TenantContext.setUserName(displayName);"));
    }
}
