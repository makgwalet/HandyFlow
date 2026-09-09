package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/**
 * Generates unique fee note numbers in FN-YYYY-NNNNN format.
 *
 * FIX (P0 backlog item 1.5): this used to be SELECT COUNT(*) + 1 —
 * the exact bug JournalNumberGenerator's own doc comment already
 * diagnosed and fixed elsewhere in this codebase ("two requests in the
 * same millisecond both read the same count and produce the same
 * sequence number"). Same fix here: a dedicated sequences table with
 * an atomic UPDATE … RETURNING, which holds a row lock and makes a
 * second concurrent request block until the first commits.
 *
 * REQUIRES migration V270__fee_note_and_fuel_receipt_sequences.sql.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeeNoteNumberGenerator {

    private final JdbcTemplate jdbc;

    /** Generates: FN-2026-00001 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(TenantId tenantId) {
        int year = LocalDate.now().getYear();

        jdbc.update("""
            INSERT INTO acc_fee_note_sequences (tenant_id, year, last_seq)
            VALUES (?, ?, 0)
            ON CONFLICT (tenant_id, year) DO NOTHING
            """, tenantId.getValue(), year);

        Integer seq = jdbc.queryForObject("""
            UPDATE acc_fee_note_sequences
            SET last_seq = last_seq + 1
            WHERE tenant_id = ? AND year = ?
            RETURNING last_seq
            """, Integer.class, tenantId.getValue(), year);

        String number = String.format("FN-%d-%05d", year, seq != null ? seq : 1);
        log.debug("Generated fee note number={} tenant={}", number, tenantId);
        return number;
    }
}
