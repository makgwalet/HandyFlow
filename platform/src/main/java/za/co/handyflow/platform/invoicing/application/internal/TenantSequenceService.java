package za.co.handyflow.platform.invoicing.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

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
@Component
public class TenantSequenceService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long nextValue(TenantId tenantId, String sequenceName) {
        Object result = em.createNativeQuery("""
                INSERT INTO tenant_number_sequences (tenant_id, sequence_name, last_value, updated_at)
                VALUES (CAST(:tenantId AS uuid), :sequenceName, 1, now())
                ON CONFLICT (tenant_id, sequence_name)
                DO UPDATE SET last_value = tenant_number_sequences.last_value + 1,
                              updated_at  = now()
                RETURNING last_value
                """)
                .setParameter("tenantId", tenantId.getValue().toString())
                .setParameter("sequenceName", sequenceName)
                .getSingleResult();

        return ((Number) result).longValue();
    }
}