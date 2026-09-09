// fuel/application/internal/ReceiptNumberGenerator.java

package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/**
 * Generates unique fuel delivery receipt numbers in FDR-YYYY-NNNNN format.
 *
 * FIX (P0 backlog item 1.6): this used to be SELECT COUNT(*) + 1 with no
 * tenant_id filter at all — the same race-condition bug class already
 * diagnosed and fixed for journal entries (JournalNumberGenerator) and
 * fee notes (FeeNoteNumberGenerator), PLUS a second, separate bug: the
 * COUNT query counted deliveries across every tenant on the platform
 * combined, not per-tenant, because generate() never took a tenantId at
 * all. Fixed both at once: a tenant-scoped sequences table with an
 * atomic UPDATE … RETURNING, same pattern as the other two generators.
 *
 * REQUIRES migration V270__fee_note_and_fuel_receipt_sequences.sql.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    // WHY sequential? Mine sites file receipts numerically.
    // Format: FDR-2026-00001
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generate(TenantId tenantId) {
        int year = LocalDate.now().getYear();

        jdbcTemplate.update("""
            INSERT INTO fuel_receipt_sequences (tenant_id, year, last_seq)
            VALUES (?, ?, 0)
            ON CONFLICT (tenant_id, year) DO NOTHING
            """, tenantId.getValue(), year);

        Integer seq = jdbcTemplate.queryForObject("""
            UPDATE fuel_receipt_sequences
            SET last_seq = last_seq + 1
            WHERE tenant_id = ? AND year = ?
            RETURNING last_seq
            """, Integer.class, tenantId.getValue(), year);

        String number = String.format("FDR-%d-%05d", year, seq != null ? seq : 1);
        log.debug("Generated fuel receipt number={} tenant={}", number, tenantId);
        return number;
    }
}
