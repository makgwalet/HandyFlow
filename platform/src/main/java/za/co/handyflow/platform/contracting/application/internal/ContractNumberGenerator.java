package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/**
 * Generates contract numbers in the format CTR-YYYY-NNNNN.
 *
 * FIX: the original COUNT(*)+1 approach had a race condition — two concurrent requests
 * would read the same count and generate duplicate numbers, causing the DB UNIQUE constraint
 * to fire as a 500 to the user.
 *
 * Solution: use an INSERT + RETURNING on a dedicated sequence table that uses
 * SELECT ... FOR UPDATE to serialize access per tenant per year. This is atomic
 * and correct under any level of concurrency.
 */
@Component
@RequiredArgsConstructor
public class ContractNumberGenerator {

    private final JdbcTemplate jdbc;

    public String next(TenantId tenantId) {
        int year = LocalDate.now().getYear();

        // Upsert the sequence row for this tenant+year, then increment atomically.
        // ON CONFLICT ensures one row per tenant/year; the RETURNING gives us the
        // new value without a second query.
        Integer seq = jdbc.queryForObject("""
            INSERT INTO contract_number_sequences (tenant_id, year, last_seq)
            VALUES (?, ?, 1)
            ON CONFLICT (tenant_id, year) DO UPDATE
              SET last_seq = contract_number_sequences.last_seq + 1
            RETURNING last_seq
            """,
                Integer.class,
                tenantId.getValue(),
                year);

        return String.format("CTR-%d-%05d", year, seq != null ? seq : 1);
    }
}
