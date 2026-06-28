package za.co.handyflow.platform.bookings.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/**
 * BookingNumberGenerator — generates sequential booking numbers per tenant per year.
 *
 * Format: BK-2026-00001
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WHY we replaced COUNT(*) + 1
 *
 * The original implementation did:
 *   1. SELECT COUNT(*) FROM bookings WHERE tenant_id = ? AND year = ?
 *   2. return count + 1
 *
 * This is a classic TOCTOU (Time-Of-Check-Time-Of-Use) race condition.
 * If two POST /bookings requests arrive simultaneously for the same
 * tenant in the same year, both read COUNT = 42, both compute 43, and
 * both insert BK-2026-00043.  The UNIQUE (tenant_id, booking_number)
 * constraint catches this — but as a constraint violation at commit time,
 * not as a friendly "slot taken" error.  The second request gets a 500.
 *
 * WHY INSERT ... ON CONFLICT DO UPDATE RETURNING is correct
 *
 * This is a single atomic SQL statement.  Postgres holds a row-level
 * lock for the duration of the statement.  Two concurrent calls for the
 * same (tenant_id, year) will serialize — one gets seq=43, the other
 * gets seq=44.  No duplicate, no 500, no retry needed.
 *
 * The SQL does:
 *   - If (tenant_id, year) doesn't exist: INSERT with last_seq=1, return 1
 *   - If it exists: increment last_seq by 1, return the new value
 * Both paths return the newly allocated sequence number in one round-trip.
 *
 * WHY not use a Postgres SEQUENCE (CREATE SEQUENCE)?
 * A global Postgres sequence would give tenant A BK-2026-00001 and
 * tenant B BK-2026-00002, then tenant A BK-2026-00003.  Customers of
 * tenant A see gaps — it looks like their business is slow.  The
 * counter table gives each tenant their own contiguous sequence.
 * ═══════════════════════════════════════════════════════════════════════
 */
@Component
@RequiredArgsConstructor
public class BookingNumberGenerator {

    private final JdbcTemplate jdbc;

    /**
     * Atomically allocate the next booking number for this tenant and year.
     *
     * @param tenantId  The tenant requesting a new booking number
     * @return          A unique booking number, e.g. "BK-2026-00043"
     */
    public String next(TenantId tenantId) {
        int year = LocalDate.now().getYear();

        // INSERT ... ON CONFLICT DO UPDATE is atomic.
        // The RETURNING clause gives us the new value without a second round-trip.
        Integer seq = jdbc.queryForObject("""
                INSERT INTO booking_number_seq (tenant_id, year, last_seq)
                VALUES (?, ?, 1)
                ON CONFLICT (tenant_id, year)
                DO UPDATE SET last_seq = booking_number_seq.last_seq + 1
                RETURNING last_seq
                """,
                Integer.class,
                tenantId.getValue(),
                year
        );

        return String.format("BK-%d-%05d", year, seq != null ? seq : 1);
    }
}
