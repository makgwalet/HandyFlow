package za.co.handyflow.platform.shared;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tenant's token version (the "tcv" claim). A token whose version is lower than
 * the tenant's current one is stale: permissions or entitlements changed after it was
 * issued. The refresh endpoint always mints with the current version, so a stale token
 * costs the user one transparent refresh, not a login.
 *
 * Reads are cached for a few seconds so the filter does not hit the database on every
 * request. Failures never block anyone: an unreadable version is treated as "not stale".
 */
@Slf4j
@Component
public class TokenVersions {

    static final long TTL_MS = 10_000L;

    private record Entry(int version, long expiresAt) { }

    private final JdbcTemplate jdbc;
    private final boolean enforce;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public TokenVersions(JdbcTemplate jdbc,
                         @Value("${handyflow.token-version.enforce:false}") boolean enforce) {
        this.jdbc = jdbc;
        this.enforce = enforce;
    }

    public boolean enforcing() {
        return enforce;
    }

    /** Current version for the tenant; 0 when unknown. */
    public int current(UUID tenantId) {
        if (tenantId == null || jdbc == null) return 0;
        long now = System.currentTimeMillis();
        Entry e = cache.get(tenantId);
        if (e != null && e.expiresAt() > now) return e.version();
        try {
            Integer v = jdbc.queryForObject("SELECT token_version FROM tenants WHERE id = ?", Integer.class, tenantId);
            int version = v == null ? 0 : v;
            cache.put(tenantId, new Entry(version, now + TTL_MS));
            return version;
        } catch (Exception ex) {
            log.warn("TOKEN-VERSION could not read version for tenant {}: {}", tenantId, ex.getMessage());
            return e != null ? e.version() : 0;
        }
    }

    /** Invalidates every token issued so far for the tenant. */
    public void bump(UUID tenantId) {
        if (tenantId == null || jdbc == null) return;
        try {
            jdbc.update("UPDATE tenants SET token_version = token_version + 1 WHERE id = ?", tenantId);
        } catch (Exception ex) {
            log.warn("TOKEN-VERSION could not bump tenant {}: {}", tenantId, ex.getMessage());
        } finally {
            cache.remove(tenantId);
        }
    }

    /** Pure rule: a token is stale when its version is below the tenant's. */
    public static boolean isStale(int tokenVersion, int currentVersion) {
        return tokenVersion < currentVersion;
    }

    /** True when this token must be refused (stale and enforcement is on). Reports otherwise. */
    public boolean rejects(UUID tenantId, int tokenVersion) {
        int cur = current(tenantId);
        if (!isStale(tokenVersion, cur)) return false;
        if (enforce) return true;
        log.info("TOKEN-VERSION-REPORT tenant={} token={} current={} (would be refused when enforced)",
                tenantId, tokenVersion, cur);
        return false;
    }
}
