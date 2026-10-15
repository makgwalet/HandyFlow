package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

/** Per-tenant running numbers (WO-0001, WO-0002, ...). One atomic upsert, so two requests never get the same number. */
@Component
@RequiredArgsConstructor
public class EarthmovingNumbering {
    private final JdbcTemplate jdbc;

    public String next(TenantId tenantId, String name, String prefix) {
        Long n = jdbc.queryForObject(
                "INSERT INTO earthmoving_counters (tenant_id, name, next_value) VALUES (?, ?, 1) "
                        + "ON CONFLICT (tenant_id, name) DO UPDATE SET next_value = earthmoving_counters.next_value + 1 "
                        + "RETURNING next_value", Long.class, tenantId.getValue(), name);
        return format(prefix, n == null ? 1 : n);
    }

    public static String format(String prefix, long n) { return prefix + "-" + String.format("%04d", n); }
}
