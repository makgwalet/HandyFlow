package za.co.handyflow.platform.shared;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates atomic, gap-tolerant, duplicate-proof sequence numbers scoped to
 * a tenant and a named sequence (e.g. "INVOICE", "QUOTE", and in future
 * "CREDIT_NOTE" or "RECEIPT" — generic so new document types don't need
 * their own copy of this logic).
 *
 * WHY this fixes the race that count()+1 could not:
 * The old approach did SELECT COUNT(*) in Java, then computed count+1, then
 * saved a new row with that number. Two concurrent requests could both read
 * the same count before either commits, producing the same "next" number
 * for both. The fix here is a single atomic SQL statement that reads AND
 * increments in one indivisible step — Postgres itself serializes concurrent
 * UPSERTs against the same (tenant_id, sequence_name) row, so there is no
 * window where two callers can observe the same "current" value.
 *
 * WHY REQUIRES_NEW?
 * The sequence bump must commit — and release its row lock — independently
 * of whatever the calling transaction does next. Picture InvoicingScheduler
 * looping over 40 due schedules in one long-running batch: if the sequence
 * increment shared that transaction, every schedule after the first would
 * queue up behind a single lock held for the whole batch's duration instead
 * of just the moment of number generation.
 *
 * It also means: if invoice creation fails validation *after* the number was
 * issued, that number is burned — it will never be reused, but there will be
 * a gap. This mirrors real accounting systems: gaps in a sequence are normal
 * and explainable (a voided draft); a REUSED number is the actual compliance
 * problem, because two different documents can no longer be told apart. We
 * are explicitly choosing "occasional gap" over "any chance of a duplicate."
 */
@Slf4j
@Component
public class TenantSequenceService {

    @PersistenceContext
    private EntityManager em;

    // Postgres tenant_number_sequences.sequence_name is VARCHAR(50).
    // Confirmed via two separate real crashes tonight — payroll bureau
    // employee numbers, then booking agency booking numbers — that
    // composed sequence names can exceed this. Both had the identical
    // shape: "<verbose module prefix>:<36-char client UUID>", which
    // blows past 50 once the prefix passes roughly 13 characters.
    // Rather than keep chasing down and patching each new module's
    // generator individually as this recurs, this guards the shared
    // entry point itself so nothing can hit this wall again.
    private static final int MAX_SEQUENCE_NAME_LENGTH = 50;
    private static final int HASH_SUFFIX_LENGTH = 8; // fixed-width %08x
    private static final int PREFIX_BUDGET =
            +            MAX_SEQUENCE_NAME_LENGTH - HASH_SUFFIX_LENGTH - 1; // 1 for the "-" separator


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long nextValue(TenantId tenantId, String sequenceName) {
        String safeName = safeSequenceName(sequenceName);

        Object result = em.createNativeQuery("""
                INSERT INTO tenant_number_sequences (tenant_id, sequence_name, last_value, updated_at)
                VALUES (CAST(:tenantId AS uuid), :sequenceName, 1, now())
                ON CONFLICT (tenant_id, sequence_name)
                DO UPDATE SET last_value = tenant_number_sequences.last_value + 1,
                              updated_at  = now()
                RETURNING last_value
                """)
                .setParameter("tenantId", tenantId.getValue().toString())
                .setParameter("sequenceName", safeName)
                .getSingleResult();

        return ((Number) result).longValue();
    }

    /**
     +     * Guarantees the stored key fits VARCHAR(50) without silently
     +     * colliding two different long names into the same counter.
     +     * <p>
     +     * Names that already fit are returned completely unchanged — every
     +     * existing sequence_name row (PROJECT, TASK:&lt;uuid&gt;,
     +     * CO:&lt;uuid&gt;, CREDIT_NOTE, GR, ...) keeps exactly the value it
     +     * already has; only a genuinely overlong name gets rewritten.
     +     * <p>
     +     * For an overlong name: keep the first PREFIX_BUDGET characters (so
     +     * it stays human-readable in the DB for debugging) and append a
     +     * fixed-width 8-hex-digit hash of the FULL original string — not
     +     * the truncated prefix — so two long names sharing the same first
     +     * PREFIX_BUDGET characters but differing further along (e.g. two
     +     * client UUIDs whose first few digits happen to match) still land
     +     * on different counters. A hash collision between two names is
     +     * possible in theory but not a practical risk at any cardinality a
     +     * single tenant will realistically reach for one sequence family.
     +     */
    private String safeSequenceName(String sequenceName) {
        if (sequenceName == null) {
            throw new IllegalArgumentException("sequenceName must not be null");
        }
        if (sequenceName.length() <= MAX_SEQUENCE_NAME_LENGTH) {
            return sequenceName;
        }
        String prefix = sequenceName.substring(0, PREFIX_BUDGET);
        String hash = String.format("%08x", sequenceName.hashCode());
        String safeName = prefix + "-" + hash;
        log.warn("Sequence name '{}' ({} chars) exceeds varchar(50) — using '{}' instead",
                sequenceName, sequenceName.length(), safeName);
        return safeName;
    }
}