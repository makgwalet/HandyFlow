package za.co.handyflow.platform.agriculture.api;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Performs a MockMvc request as a tenant user. The tenant is seeded immediately BEFORE EACH request, not once per test, because JwtAuthFilter
 * clears TenantContext when a request finishes: a test that seeds once and sends two requests would find the second one has no tenant and
 * fails with 409 "No tenant in context". Pair it with {@link #clear()} in an @AfterEach so nothing leaks into other test classes.
 */
final class TenantRequests {

    private TenantRequests() {}

    static ResultActions asTenant(MockMvc mvc, RequestBuilder request) throws Exception {
        TenantContext.setTenantId(UUID.randomUUID().toString());
        TenantContext.setUserId(UUID.randomUUID().toString());
        return mvc.perform(request);
    }

    static void clear() {
        TenantContext.clear();
    }
}
