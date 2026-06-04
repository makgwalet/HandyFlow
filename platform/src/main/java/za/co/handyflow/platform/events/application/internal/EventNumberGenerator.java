package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/**
 * Race-condition-free event number generator.
 *
 * The original used SELECT COUNT(*) + 1, which is not atomic.
 * Two concurrent createEvent calls in the same year could both read the same
 * count, produce the same number, and violate the UNIQUE (tenant_id, event_number)
 * constraint under load.
 *
 * Fix: use a PostgreSQL advisory lock keyed on the tenant UUID to serialise
 * concurrent number generation for the same tenant. The lock is session-scoped
 * and released automatically at transaction end.
 */
@Component
@RequiredArgsConstructor
public class EventNumberGenerator {

    private final JdbcTemplate jdbc;

    /** Format: EVT-2026-00001 */
    public String next(TenantId tenantId) {
        int year = LocalDate.now().getYear();

        // Acquire a PostgreSQL advisory lock exclusive to this tenant.
        // hashtext() produces a stable int8 from the UUID string.
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtext('" + tenantId.getValue() + "'))");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE tenant_id = ? AND EXTRACT(YEAR FROM created_at) = ?",
                Integer.class, tenantId.getValue(), year);
        int seq = (count != null ? count : 0) + 1;
        return String.format("EVT-%d-%05d", year, seq);
    }

    /** Ticket number: EVT-2026-00001-0042 */
    public String nextTicket(String eventNumber, long guestSeq) {
        return eventNumber + "-" + String.format("%04d", guestSeq);
    }
}
