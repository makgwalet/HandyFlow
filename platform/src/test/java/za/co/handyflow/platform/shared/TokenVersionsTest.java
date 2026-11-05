package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TokenVersionsTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret-1234";
    private final UUID tenant = UUID.randomUUID();

    private JdbcTemplate jdbcReturning(int version) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doReturn(version).when(jdbc).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
        return jdbc;
    }

    @Test
    void aTokenBelowTheTenantVersionIsStale() {
        assertThat(TokenVersions.isStale(0, 1)).isTrue();
        assertThat(TokenVersions.isStale(1, 1)).isFalse();
        assertThat(TokenVersions.isStale(2, 1)).isFalse();
    }

    @Test
    void staleTokensAreRefusedOnlyWhenEnforcing() {
        assertThat(new TokenVersions(jdbcReturning(3), true).rejects(tenant, 2)).isTrue();
        assertThat(new TokenVersions(jdbcReturning(3), false).rejects(tenant, 2)).isFalse();
        assertThat(new TokenVersions(jdbcReturning(3), true).rejects(tenant, 3)).isFalse();
    }

    @Test
    void anOldTokenWithNoClaimIsRefusedOnceTheVersionHasBeenBumped() {
        assertThat(new TokenVersions(jdbcReturning(1), true).rejects(tenant, 0)).isTrue();
        assertThat(new TokenVersions(jdbcReturning(0), true).rejects(tenant, 0)).isFalse();
    }

    @Test
    void aDatabaseFailureNeverLocksAnyoneOut() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doThrow(new RuntimeException("db down")).when(jdbc).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
        assertThat(new TokenVersions(jdbc, true).rejects(tenant, 0)).isFalse();
    }

    @Test
    void theVersionIsCachedAndBumpClearsIt() {
        JdbcTemplate jdbc = jdbcReturning(1);
        TokenVersions v = new TokenVersions(jdbc, true);
        v.current(tenant);
        v.current(tenant);
        verify(jdbc, times(1)).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
        v.bump(tenant);
        v.current(tenant);
        verify(jdbc, times(2)).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
    }

    @Test
    void newTokensCarryTheTenantVersion() {
        JwtService jwt = new JwtService(SECRET, 3_600_000L);
        jwt.setTokenVersions(new TokenVersions(jdbcReturning(4), false));
        String token = jwt.generateToken(UUID.randomUUID(), tenant, "a@b.co", "A", "B", java.util.Set.of("X"));
        assertThat(jwt.extractTokenVersion(token)).isEqualTo(4);
    }

    @Test
    void tokensWithoutTheClaimReadAsZero() {
        JwtService jwt = new JwtService(SECRET, 3_600_000L);
        String token = jwt.generateToken(UUID.randomUUID(), tenant, "a@b.co", "A", "B", java.util.Set.of("X"));
        assertThat(jwt.extractTokenVersion(token)).isZero();
    }
}
