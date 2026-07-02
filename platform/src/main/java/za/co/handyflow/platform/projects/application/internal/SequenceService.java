package za.co.handyflow.platform.projects.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Provides monotonically-increasing, race-condition-free integer sequences
 * for PM entity numbering (project numbers, task numbers, CO numbers, etc.).
 *
 * ────────────────────────────────────────────────────────────────────────────
 * WHY NOT SELECT MAX() + 1?
 * ────────────────────────────────────────────────────────────────────────────
 * The original code used:
 *     int seq = repo.findMaxProjectSequence(tenantId) + 1;
 *
 * Two concurrent HTTP requests both read MAX = 5, both compute 6, and both try
 * to insert PRJ0006.  The UNIQUE constraint fires, the caller gets an
 * unhandled DataIntegrityViolationException → HTTP 500.
 *
 * ────────────────────────────────────────────────────────────────────────────
 * WHY THIS APPROACH?
 * ────────────────────────────────────────────────────────────────────────────
 * PostgreSQL's  INSERT … ON CONFLICT DO UPDATE … RETURNING  is a single atomic
 * operation.  No second SELECT is needed, and the row-level lock on the
 * (tenant_id, counter_type) row means two concurrent callers queue up
 * transparently — one gets 6, the other gets 7.
 *
 * PROPAGATION.REQUIRES_NEW is critical: the counter increment commits
 * independently so that if the outer transaction rolls back (e.g. duplicate
 * project name validation fails), the counter is still consumed.  Gaps in
 * sequence numbers are acceptable; duplicate numbers are not.
 *
 * ────────────────────────────────────────────────────────────────────────────
 * COUNTER TYPE CONVENTION
 * ────────────────────────────────────────────────────────────────────────────
 *  - Projects:     "PROJECT"
 *  - Tasks:        "TASK:<projectId>"         (per-project numbering T001, T002...)
 *  - Change orders:"CO:<projectId>"
 *  - Snags:        "SNAG:<projectId>"
 *  - Phases:       "PHASE:<projectId>"
 *  - Budget lines: "BUDGET:<projectId>"
 */
@Slf4j
@Service
public class SequenceService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Returns the next sequence value for the given tenant and counter type.
     * This method runs in its OWN transaction (REQUIRES_NEW) so the increment
     * is committed regardless of whether the outer business transaction succeeds.
     *
     * @param tenantId    the tenant whose counter is being incremented
     * @param counterType a namespaced key (see class Javadoc for conventions)
     * @return            the next integer in the sequence (starts at 1)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int next(UUID tenantId, String counterType) {
        /*
         * Single atomic PostgreSQL statement:
         *   1. Try to insert a new row with current_value = 1
         *   2. If the row exists (ON CONFLICT), increment current_value by 1
         *   3. Return the resulting current_value
         *
         * Because this is an upsert on the PRIMARY KEY (tenant_id, counter_type),
         * PostgreSQL places a row-level lock — concurrent callers serialise
         * cleanly without application-level locking or retries.
         */
        Number result = (Number) em.createNativeQuery("""
                INSERT INTO pm_counters (tenant_id, counter_type, current_value)
                VALUES (:tid, :type, 1)
                ON CONFLICT (tenant_id, counter_type)
                DO UPDATE SET current_value = pm_counters.current_value + 1
                RETURNING current_value
                """)
                .setParameter("tid", tenantId)
                .setParameter("type", counterType)
                .getSingleResult();

        int value = result.intValue();
        log.debug("Sequence next: tenant={} type={} value={}", tenantId, counterType, value);
        return value;
    }

    // ── Convenience factory methods ──────────────────────────────────────────

    /** Formats "PRJ0042" — global per tenant */
    public String nextProjectNumber(UUID tenantId) {
        return "PRJ" + String.format("%04d", next(tenantId, "PROJECT"));
    }

    /** Formats "T042" — scoped per project */
    public String nextTaskNumber(UUID tenantId, UUID projectId) {
        return "T" + String.format("%03d", next(tenantId, "TASK:" + projectId));
    }

    /** Formats "CO-007" — scoped per project */
    public String nextChangeOrderNumber(UUID tenantId, UUID projectId) {
        return "CO-" + String.format("%03d", next(tenantId, "CO:" + projectId));
    }

    /** Formats "SN0021" — scoped per project */
    public String nextSnagNumber(UUID tenantId, UUID projectId) {
        return "SN" + String.format("%04d", next(tenantId, "SNAG:" + projectId));
    }

    /** Returns next sort-order integer for phases or budget lines */
    public int nextSortOrder(UUID tenantId, UUID projectId, String entityType) {
        return next(tenantId, entityType + ":" + projectId);
    }
}
