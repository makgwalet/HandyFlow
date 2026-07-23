package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/**
 * Generates unique journal entry numbers in JE-YYYY-NNNNN format.
 *
 * WHY NOT SELECT COUNT(*) + 1?
 * The original implementation uses SELECT COUNT(*) + 1. Under concurrent load, two
 * requests in the same millisecond both read the same count and produce the same
 * sequence number, crashing one transaction with a UNIQUE constraint violation.
 *
 * FIX: Use a dedicated sequences table with SELECT … FOR UPDATE.
 * The FOR UPDATE acquires a row-level lock. The second concurrent request will
 * block until the first transaction commits, guaranteeing strictly incrementing
 * sequence numbers with no gaps and no duplicates.
 *
 * REQUIRES migration V74__journal_sequences.sql (provided separately).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JournalNumberGenerator {

    private final JdbcTemplate jdbc;

    // Format: JE-2026-00001
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(TenantId tenantId) {
        int year = LocalDate.now().getYear();

        // FIX: was tenantId.getValue().toString() — bound as a plain
        // String, which Postgres rejected against the uuid-typed
        // tenant_id column ("column tenant_id is of type uuid but
        // expression is of type character varying"). Pass the raw UUID
        // directly instead, matching every other JDBC call in this
        // codebase (jdbc.update(..., tenantId.getValue(), ...), never
        // .toString()'d). ON CONFLICT ... DO NOTHING means this INSERT
        // only actually runs the first time a given tenant+year needs a
        // sequence row — likely why this sat latent until now.

        // Upsert the sequence row if it doesn't exist yet
        jdbc.update("""
            INSERT INTO acc_journal_sequences (tenant_id, year, last_seq)
            VALUES (?, ?, 0)
            ON CONFLICT (tenant_id, year) DO NOTHING
            """, tenantId.getValue(), year);

        // Atomic increment with row-level lock — no race condition possible
        Integer seq = jdbc.queryForObject("""
            UPDATE acc_journal_sequences
            SET last_seq = last_seq + 1
            WHERE tenant_id = ? AND year = ?
            RETURNING last_seq
            """, Integer.class, tenantId.getValue(), year);

        String number = String.format("JE-%d-%05d", year, seq != null ? seq : 1);
        log.debug("Generated journal number={} tenant={}", number, tenantId);
        return number;
    }
}