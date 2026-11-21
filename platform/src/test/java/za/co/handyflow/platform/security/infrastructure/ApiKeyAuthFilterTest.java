package za.co.handyflow.platform.security.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.handyflow.platform.security.application.internal.PublicApiService;
import za.co.handyflow.platform.security.domain.model.ApiKey;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Security audit 1.5 through the real ApiKeyAuthFilter. NOT RUN by the author (no Maven in the authoring sandbox): `mvn -q test -Dtest=ApiKeyAuthFilterTest`.
 * If ApiKey.create's signature differs from what is used here, adjust key(...) only.
 */
class ApiKeyAuthFilterTest {

    private final PublicApiService api = mock(PublicApiService.class);
    private final ApiKeyAuthFilter filter = new ApiKeyAuthFilter(api, new ObjectMapper());

    @AfterEach void clean() { TenantContext.clear(); SecurityContextHolder.clearContext(); }

    private ApiKey key(String scope, UUID branch, boolean readOnly) {
        ApiKey k = ApiKey.create(TenantId.generate(), "BI tool", "hash", "hf_live_abcd", scope, branch, readOnly, null, UUID.randomUUID());
        when(api.validateKey("hf_live_x")).thenReturn(Optional.of(k));
        return k;
    }
    private MockHttpServletResponse call(String method, String uri, FilterChain chain) throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRequestURI(uri);
        r.addHeader("Authorization", "ApiKey hf_live_x");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(r, res, chain);
        return res;
    }

    @Test @DisplayName("a read-only key with no scope reads security data with SECURITY_READ and nothing else")
    void readKeyReads() throws Exception {
        key(null, null, true);
        AtomicReference<Authentication> seen = new AtomicReference<>();
        MockHttpServletResponse res = call("GET", "/api/v1/security/sites", (rq, rs) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(seen.get().getAuthorities()).extracting(Object::toString).containsExactly("SECURITY_READ");
    }

    @Test @DisplayName("a write key also gets SECURITY_MANAGE, and no USER_ authority")
    void writeKey() throws Exception {
        key(null, null, false);
        AtomicReference<Authentication> seen = new AtomicReference<>();
        call("POST", "/api/v1/security/incidents", (rq, rs) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));
        assertThat(seen.get().getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("SECURITY_READ", "SECURITY_MANAGE");
    }

    @Test @DisplayName("a key cannot reach other modules, even with no scope or a scope that names them")
    void otherModules() throws Exception {
        for (String scope : new String[] { null, "[\"/api/v1/\"]", "[\"/api/v1/hr\"]" }) {
            key(scope, null, false);
            FilterChain chain = mock(FilterChain.class);
            for (String uri : new String[] { "/api/v1/users", "/api/v1/tenants/me", "/api/v1/hr/employees" }) {
                assertThat(call("GET", uri, chain).getStatus()).as(uri + " scope " + scope).isEqualTo(403);
            }
            verifyNoInteractions(chain);
        }
    }

    @Test @DisplayName("keys cannot mint keys, change webhooks, touch payroll or enrol guards")
    void blocked() throws Exception {
        key("[\"/api/v1/security/\"]", null, false);
        FilterChain chain = mock(FilterChain.class);
        assertThat(call("POST", "/api/v1/security/public-api/keys", chain).getStatus()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/security/payroll/periods", chain).getStatus()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/security/guards/" + UUID.randomUUID() + "/enrol", chain).getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test @DisplayName("a branch-limited key is refused with a clear message (the limit is not enforced anywhere yet)")
    void branchKeyRefused() throws Exception {
        key(null, UUID.randomUUID(), true);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = call("GET", "/api/v1/security/sites", chain);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("Branch-limited");
        verifyNoInteractions(chain);
    }

    @Test @DisplayName("a malformed scope fails closed; a read-only key cannot write")
    void malformedAndReadOnly() throws Exception {
        key("not json", null, true);
        FilterChain chain = mock(FilterChain.class);
        assertThat(call("GET", "/api/v1/security/sites", chain).getStatus()).isEqualTo(403);
        key(null, null, true);
        assertThat(call("POST", "/api/v1/security/incidents", chain).getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test @DisplayName("an unknown key is 401 and context is cleared afterwards")
    void unknownKey() throws Exception {
        when(api.validateKey("hf_live_x")).thenReturn(Optional.empty());
        assertThat(call("GET", "/api/v1/security/sites", mock(FilterChain.class)).getStatus()).isEqualTo(401);
        assertThat(TenantContext.hasTenant()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
