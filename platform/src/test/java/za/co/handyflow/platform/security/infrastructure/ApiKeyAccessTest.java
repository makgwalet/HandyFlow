package za.co.handyflow.platform.security.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyAccessTest {

    @Test @DisplayName("a read-only key gets SECURITY_READ only; a write key adds SECURITY_MANAGE; nobody gets USER_* any more")
    void authorities() {
        assertEquals(Set.of("SECURITY_READ"), ApiKeyAccess.authorities(true));
        assertEquals(Set.of("SECURITY_READ", "SECURITY_MANAGE"), ApiKeyAccess.authorities(false));
        for (boolean ro : new boolean[] { true, false }) for (String a : ApiKeyAccess.authorities(ro)) assertFalse(a.startsWith("USER_"));
    }

    @Test @DisplayName("no scope means every security endpoint, and nothing outside the security module")
    void defaultScope() {
        for (List<String> none : java.util.Arrays.asList(null, List.<String>of())) {
            assertTrue(ApiKeyAccess.inScope("/api/v1/security/sites", none));
            assertTrue(ApiKeyAccess.inScope("/api/v1/security/reports/monthly", none));
            assertFalse(ApiKeyAccess.inScope("/api/v1/users", none), "identity users");
            assertFalse(ApiKeyAccess.inScope("/api/v1/tenants/me", none), "tenant");
            assertFalse(ApiKeyAccess.inScope("/api/v1/hr/employees", none), "HR employees");
            assertFalse(ApiKeyAccess.inScope("/api/v1/guard/sessions/open", none), "guard app");
            assertFalse(ApiKeyAccess.inScope("/api/v1/security", none), "the bare root is not an endpoint");
            assertFalse(ApiKeyAccess.inScope(null, none));
        }
    }

    @Test @DisplayName("a scope narrows to its prefixes, and cannot widen past the security module")
    void scopeNarrows() {
        List<String> scope = List.of("/api/v1/security/reports", "/api/v1/security/sites");
        assertTrue(ApiKeyAccess.inScope("/api/v1/security/reports/monthly", scope));
        assertTrue(ApiKeyAccess.inScope("/api/v1/security/sites/abc", scope));
        assertFalse(ApiKeyAccess.inScope("/api/v1/security/incidents", scope));
        assertFalse(ApiKeyAccess.inScope("/api/v1/hr/employees", List.of("/api/v1/hr")), "a scope naming another module does not grant it");
        assertFalse(ApiKeyAccess.inScope("/api/v1/users", List.of("/api/v1/")));
        assertFalse(ApiKeyAccess.inScope("/api/v1/security/sites", java.util.Arrays.asList((String) null, " ")), "blank entries grant nothing");
    }

    @Test @DisplayName("key and webhook management, payroll and guard credential actions are closed to every key, even with a matching scope")
    void blockedPaths() {
        String[] blocked = {
            "/api/v1/security/public-api/keys", "/api/v1/security/public-api/webhooks/123/reactivate", "/api/v1/security/payroll/periods", "/api/v1/security/payroll",
            "/api/v1/security/guards/6f1c/enrol", "/api/v1/security/guards/6f1c/initiate-device-replacement", "/api/v1/security/guards/6f1c/revoke-tokens" };
        for (String p : blocked) {
            assertTrue(ApiKeyAccess.blocked(p), p);
            assertFalse(ApiKeyAccess.inScope(p, null), p);
            assertFalse(ApiKeyAccess.inScope(p, List.of("/api/v1/security/")), p + " with the widest scope");
        }
        assertTrue(ApiKeyAccess.blocked(null));
    }

    @Test @DisplayName("ordinary guard reads are still open")
    void guardReadsOpen() {
        assertFalse(ApiKeyAccess.blocked("/api/v1/security/guards"));
        assertFalse(ApiKeyAccess.blocked("/api/v1/security/guards/6f1c"));
        assertFalse(ApiKeyAccess.blocked("/api/v1/security/guards/6f1c/devices"));
        assertTrue(ApiKeyAccess.inScope("/api/v1/security/guards/6f1c", null));
    }

    @Test @DisplayName("a scope someone wants to save must stay inside the security module")
    void scopeProblem() {
        assertNull(ApiKeyAccess.scopeProblem(null));
        assertNull(ApiKeyAccess.scopeProblem(List.of()));
        assertNull(ApiKeyAccess.scopeProblem(List.of("/api/v1/security/reports")));
        assertNotNull(ApiKeyAccess.scopeProblem(List.of("/api/v1/hr")));
        assertNotNull(ApiKeyAccess.scopeProblem(List.of("/api/v1/security/reports", "/api/v1/users")));
        assertNotNull(ApiKeyAccess.scopeProblem(java.util.Arrays.asList((String) null)));
        assertNotNull(ApiKeyAccess.scopeProblem(List.of("")));
    }
}
