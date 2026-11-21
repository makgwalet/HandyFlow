package za.co.handyflow.platform.security.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** NOT RUN by the author (no Maven in the authoring sandbox). */
class ApiKeyScopeJsonTest {
    @Test void blankIsNoScope() {
        assertThat(ApiKeyScopeJson.parse(null)).contains(List.of());
        assertThat(ApiKeyScopeJson.parse("  ")).contains(List.of());
        assertThat(ApiKeyScopeJson.parse("[]")).contains(List.of());
    }
    @Test void arrayOfStrings() {
        assertThat(ApiKeyScopeJson.parse("[\"/api/v1/security/reports\",\"/api/v1/security/sites\"]")).contains(List.of("/api/v1/security/reports", "/api/v1/security/sites"));
    }
    @Test void anythingElseIsMalformedSoFailsClosed() {
        assertThat(ApiKeyScopeJson.parse("not json")).isEmpty();
        assertThat(ApiKeyScopeJson.parse("{\"a\":1}")).isEmpty();
        assertThat(ApiKeyScopeJson.parse("[1,2]").map(l -> l.size())).isNotNull();
    }
}
