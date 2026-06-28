package za.co.handyflow.platform.crm.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Customer360Repository — queries the customer_360_summary view.
 *
 * WHY not extend JpaRepository?
 * JpaRepository<T, ID> requires T to be a @Entity managed by Hibernate.
 * The customer_360_summary VIEW is not a JPA entity — it is a read-only
 * DB view.  Extending JpaRepository<Object, UUID> compiles but blows up
 * at startup with "Not a managed type: class java.lang.Object" because
 * Hibernate cannot find an @Entity mapping for Object.
 *
 * The correct approach for view-backed reads is:
 *   - Plain @Repository (no Spring Data magic)
 *   - Inject EntityManager directly
 *   - Run a native query via createNativeQuery()
 *   - Map the Object[] result row manually
 *
 * This is more code than a Spring Data interface, but it's the only
 * correct approach when there is no @Entity backing the query.
 *
 * WHY not @Immutable @Entity on the view?
 * We could map the view as a @Entity with @Immutable.  But that requires
 * a dummy @Id field and tricks Hibernate into thinking it can INSERT/UPDATE
 * the view — a footgun.  Manual EntityManager mapping is explicit and safe.
 */
@Repository
public class Customer360Repository {

    @PersistenceContext
    private EntityManager em;

    /**
     * Fetch the 360 summary for a single customer from the view.
     * Returns Optional.empty() if the customer has no view row
     * (shouldn't happen for active customers, but handled gracefully).
     */
    @SuppressWarnings("unchecked")
    public Optional<Customer360Summary> find360Summary(TenantId tenantId, UUID customerId) {
        var sql = """
                SELECT
                    customer_id,
                    total_bookings,
                    bookings_last_90_days,
                    last_booking_at,
                    total_invoices,
                    total_invoiced_amount,
                    overdue_invoices,
                    outstanding_amount
                FROM customer_360_summary
                WHERE customer_id = :customerId
                  AND tenant_id   = :tenantId
                """;

        var results = em.createNativeQuery(sql)
                .setParameter("customerId", customerId)
                .setParameter("tenantId",   tenantId.getValue())
                .getResultList();

        if (results.isEmpty()) return Optional.empty();

        return Optional.of(mapRow((Object[]) results.get(0)));
    }

    /**
     * Map a raw Object[] result row to Customer360Summary.
     *
     * WHY manual mapping and not SqlResultSetMapping?
     * SqlResultSetMapping requires the result to be associated with a
     * @Entity — which we deliberately don't have.  Manual index-based
     * mapping is more code but has no hidden coupling to JPA annotations.
     *
     * Column order must exactly match the SELECT column order above.
     * Any reordering of the SELECT must be reflected here.
     *
     * WHY the Timestamp cast for last_booking_at?
     * PostgreSQL TIMESTAMP columns come back as java.sql.Timestamp when
     * accessed via JDBC/Hibernate native queries, not as Instant.
     * We convert to Instant immediately so the rest of the code stays
     * clean and timezone-independent.
     */
    private Customer360Summary mapRow(Object[] row) {
        return new Customer360Summary(
                toUuid(row[0]),                  // customer_id
                toLong(row[1]),                  // total_bookings
                toLong(row[2]),                  // bookings_last_90_days
                toInstant(row[3]),               // last_booking_at  (nullable)
                toLong(row[4]),                  // total_invoices
                toDecimal(row[5]),               // total_invoiced_amount
                toLong(row[6]),                  // overdue_invoices
                toDecimal(row[7])                // outstanding_amount
        );
    }

    // ── Type conversion helpers ───────────────────────────────────────────────

    private static UUID toUuid(Object o) {
        if (o == null) return null;
        if (o instanceof UUID u) return u;
        return UUID.fromString(o.toString());
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }

    private static Instant toInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp ts) return ts.toInstant();
        if (o instanceof Instant i) return i;
        return null;
    }

    // ── Inner DTO — lives here since it is only used by this repository ───────

    /**
     * Customer360Summary — the 360 view projection.
     *
     * Kept as an inner record because it has no meaning outside this
     * repository.  Customer360Service maps this to its own DTO before
     * returning it to callers, so the inner record never leaks out.
     */
    public record Customer360Summary(
            UUID       customerId,
            long       totalBookings,
            long       bookingsLast90Days,
            Instant    lastBookingAt,
            long       totalInvoices,
            BigDecimal totalInvoicedAmount,
            long       overdueInvoices,
            BigDecimal outstandingAmount
    ) {
        public boolean hasOverdueInvoices()  { return overdueInvoices > 0; }
        public boolean isRecentlyActive()    { return bookingsLast90Days > 0; }
    }
}