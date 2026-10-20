package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Year;
import java.time.ZoneId;

/**
 * Issues work order references like {@code WO-2026-00042}: sequential per tenant per year, from an
 * atomic upsert-and-return that runs inside the caller's transaction (no gaps if a creation rolls
 * back). Same approach as AccidentReferenceGenerator; uses PostgreSQL's
 * {@code INSERT ... ON CONFLICT ... RETURNING}.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderReferenceGenerator {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final JdbcTemplate jdbc;

    public String next(TenantId tenantId) {
        int year = Year.now(SAST).getValue();
        Integer n = jdbc.queryForObject(
                "INSERT INTO fleet_work_order_counters (tenant_id, year, last_number) VALUES (?, ?, 1) "
                        + "ON CONFLICT (tenant_id, year) DO UPDATE "
                        + "SET last_number = fleet_work_order_counters.last_number + 1 "
                        + "RETURNING last_number",
                Integer.class, tenantId.getValue(), year);
        if (n == null) {
            throw new IllegalStateException("Could not allocate a work order reference");
        }
        return String.format("WO-%d-%05d", year, n);
    }
}