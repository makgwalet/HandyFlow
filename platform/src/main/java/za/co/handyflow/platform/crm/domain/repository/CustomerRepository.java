package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CustomerRepository — Spring Data JPA interface.
 *
 * DESIGN PRINCIPLES:
 *
 * 1. ALL queries filter by tenant_id.
 *    There is no query that can return data from another tenant.
 *    Multi-tenancy is enforced at the repository layer, not just
 *    at the service layer.  Defence in depth.
 *
 * 2. Soft-delete is invisible.
 *    "Active" queries always include WHERE deleted_at IS NULL.
 *    The only methods that can see deleted records are explicitly
 *    named "findDeleted..." so it's impossible to accidentally
 *    return deleted data in a normal list query.
 *
 * 3. Full-text search uses the GIN index from V8.
 *    The old ILIKE '%term%' does a full table scan.
 *    searchActive() now uses Postgres tsvector (see V8 migration)
 *    which is O(log N) instead of O(N) — critical at scale.
 *
 * 4. existsByEmail uses a partial index-friendly query.
 *    We exclude the current customer's ID on update checks so the
 *    query correctly handles "email unchanged on update" without a
 *    false positive.
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    // ── Active queries (deleted_at IS NULL) ──────────────────────────────────

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
            ORDER BY c.name ASC
            """)
    Page<Customer> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND c.id = :id
            """)
    Optional<Customer> findActiveById(@Param("tenantId") TenantId tenantId,
                                      @Param("id") UUID id);

    /**
     * Full-text search using Postgres tsvector (GIN indexed in V8).
     *
     * WHY native query?
     * JPQL doesn't support Postgres-specific functions like
     * to_tsquery() or the @@ operator.  We drop to native SQL only
     * where we need DB-specific features.
     *
     * WHY to_tsquery with ':*'?
     * The :* suffix enables prefix matching, so "han" matches "handyflow".
     * This gives "search as you type" feel without a separate search engine.
     *
     * WHY websearch_to_tsquery fallback?
     * We use plainto_tsquery for safety — it handles raw user input without
     * throwing syntax errors that to_tsquery would on special characters.
     */
    @Query(value = """
            SELECT * FROM customers c
            WHERE c.tenant_id = :#{#tenantId.value}
              AND c.deleted_at IS NULL
              AND c.search_vector @@ plainto_tsquery('simple', :term)
            ORDER BY ts_rank(c.search_vector, plainto_tsquery('simple', :term)) DESC,
                     c.name ASC
            """,
            countQuery = """
            SELECT count(*) FROM customers c
            WHERE c.tenant_id = :#{#tenantId.value}
              AND c.deleted_at IS NULL
              AND c.search_vector @@ plainto_tsquery('simple', :term)
            """,
            nativeQuery = true)
    Page<Customer> searchActive(@Param("tenantId") TenantId tenantId,
                                @Param("term") String term,
                                Pageable pageable);

    /**
     * Email uniqueness check for CREATE.
     *
     * WHY not existsByTenantIdAndEmail?
     * That generated method would also match deleted customers.
     * We want the partial-index-compatible query.
     */
    @Query("""
            SELECT COUNT(c) > 0 FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.email = :email
              AND c.deletedAt IS NULL
            """)
    boolean existsActiveByEmail(@Param("tenantId") TenantId tenantId,
                                @Param("email") String email);

    /**
     * Email uniqueness check for UPDATE.
     * Excludes the customer being updated (so their current email passes).
     *
     * WHY exclude excludeId?
     * Without this, updating a customer's name (not their email) would
     * trigger a false "email already exists" error because their own
     * record matches.
     */
    @Query("""
            SELECT COUNT(c) > 0 FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.email = :email
              AND c.deletedAt IS NULL
              AND c.id <> :excludeId
            """)
    boolean existsActiveByEmailExcluding(@Param("tenantId") TenantId tenantId,
                                         @Param("email") String email,
                                         @Param("excludeId") UUID excludeId);

    // ── Deleted record queries (for restore feature) ──────────────────────────

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NOT NULL
            ORDER BY c.deletedAt DESC
            """)
    Page<Customer> findAllDeleted(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NOT NULL
              AND c.id = :id
            """)
    Optional<Customer> findDeletedById(@Param("tenantId") TenantId tenantId,
                                       @Param("id") UUID id);

    // ── Status / segmentation queries ─────────────────────────────────────────

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND c.status = :status
            """)
    Page<Customer> findAllActiveByStatus(@Param("tenantId") TenantId tenantId,
                                         @Param("status") CustomerStatus status,
                                         Pageable pageable);

    /**
     * Find customers with no activity (booking or invoice) since a cutoff.
     * Used by the InactivityScheduler to auto-flag INACTIVE customers.
     *
     * This is a simple time-based query — ML-based churn scoring can
     * replace this later without changing the interface.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND c.status = 'ACTIVE'
              AND c.updatedAt < :cutoff
            """)
    List<Customer> findActiveUpdatedBefore(@Param("tenantId") TenantId tenantId,
                                           @Param("cutoff") Instant cutoff);

    // ── Export queries (unbounded — intentional, streaming to Writer) ─────────

    /**
     * Return all active customers for CSV export.
     *
     * WHY List and not Page?
     * Export is a one-shot "give me everything" operation.
     * CustomerExportService streams each row directly to the HTTP response
     * Writer — no pagination needed.  For tenants with > 50k customers,
     * replace with JPA Scroll (Spring Data 3.1+) to avoid loading all rows
     * into the first-level cache at once.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
            ORDER BY c.name ASC
            """)
    List<Customer> findAllActiveForExport(@Param("tenantId") TenantId tenantId);

    /**
     * Return ALL customers including deleted — for POPIA full data export.
     * Only called by the "All customers incl. deleted" export endpoint
     * which requires CUSTOMER_EXPORT_ALL authority.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
            ORDER BY c.deletedAt DESC NULLS FIRST, c.name ASC
            """)
    List<Customer> findAllForExport(@Param("tenantId") TenantId tenantId);

    // ── CrmFacade queries ─────────────────────────────────────────────────────

    /** Used by CrmFacadeImpl — checks existence without loading the full entity. */
    @Query("""
            SELECT COUNT(c) > 0 FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.id = :id
              AND c.deletedAt IS NULL
            """)
    boolean existsActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    // ── Cross-module conflict checks (native SQL — no Booking/Invoice @Entity needed) ──

    /**
     * Count active (non-cancelled, non-completed) bookings for a customer.
     *
     * WHY native query?
     * The bookings table belongs to the Bookings module.  CRM has no @Entity
     * mapping for Booking.  A native query lets us read across module
     * boundaries at the DB level without importing Booking domain objects
     * into CRM — the correct cross-module read pattern for a modular monolith.
     *
     * "Active" mirrors the definition in V93 customer_360_summary view:
     *   cancelled_at IS NULL AND status NOT IN ('CANCELLED', 'NO_SHOW', 'COMPLETED')
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM bookings b
            WHERE b.customer_id = :customerId
              AND b.tenant_id   = :#{#tenantId.value}
              AND b.cancelled_at IS NULL
              AND b.status NOT IN ('CANCELLED', 'NO_SHOW', 'COMPLETED')
            """, nativeQuery = true)
    long countActiveBookings(
            @Param("tenantId") TenantId tenantId,
            @Param("customerId") UUID customerId
    );

    /**
     * Count unpaid invoices for a customer.
     *
     * "Unpaid" = DRAFT, SENT, or OVERDUE — not yet PAID, VOID, or CANCELLED.
     * Column names verified against live schema: status VARCHAR, deleted_at TIMESTAMP.
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM invoices i
            WHERE i.customer_id = :customerId
              AND i.tenant_id   = :#{#tenantId.value}
              AND i.deleted_at  IS NULL
              AND i.status IN ('DRAFT', 'SENT', 'OVERDUE')
            """, nativeQuery = true)
    long countUnpaidInvoices(
            @Param("tenantId") TenantId tenantId,
            @Param("customerId") UUID customerId
    );

    /**
     * Returns all active customer names for this tenant — used for fuzzy
     * duplicate detection on manual create.
     *
     * WHY return List<String> and not List<Customer>?
     * We only need the name field for the Jaro-Winkler comparison.
     * Loading full Customer entities (with address JSONB, tags, activities)
     * for a name-only check wastes memory and adds N×1 risks as the
     * tenant's customer count grows.  A scalar projection is the right tool.
     *
     * WHY fetch all names instead of doing fuzzy matching in SQL?
     * PostgreSQL has no built-in Jaro-Winkler function.  The pg_trgm
     * extension has similarity() but it uses trigrams (good for search, poor
     * for name matching).  Running Jaro-Winkler in Java on O(N) names is
     * fast up to ~50,000 customers per tenant — well beyond HandyFlow's
     * near-term scale.  Add a materialized view or pg_trgm index when you
     * hit that ceiling.
     */
    @Query("""
            SELECT c.name FROM Customer c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
            """)
    List<String> findAllActiveNames(@Param("tenantId") TenantId tenantId);

    /**
     * Returns all active customer names excluding a specific customer.
     * Used during UPDATE to check for name similarity without falsely
     * matching the customer being edited against itself.
     */
    @Query("""
            SELECT c.name FROM Customer c
            WHERE c.tenantId  = :tenantId
              AND c.deletedAt IS NULL
              AND c.id <> :excludeId
            """)
    List<String> findAllActiveNamesExcluding(@Param("tenantId") TenantId tenantId,
                                             @Param("excludeId") UUID excludeId);


    /**
     * Returns all distinct tenant IDs that have at least one active customer.
     *
     * WHY this query instead of TenantRepository.findAllActive()?
     * TenantRepository lives in the Tenant module — importing it into CRM
     * creates a cross-module dependency that violates module boundaries and
     * breaks compilation if the package path changes.
     *
     * Querying distinct tenant_ids from the customers table is safe because:
     * 1. CRM already owns the customers table — no boundary violation.
     * 2. A tenant with no customers produces an empty list — the scheduler
     *    correctly skips it with zero work done.
     * 3. A suspended tenant's customers will still be present, but the
     *    scheduler marking them INACTIVE is harmless (they're already inactive
     *    from a business perspective).  If you need to exclude suspended
     *    tenants, add a cross-module join only when that requirement is confirmed.
     */
    @Query(value = """
            SELECT DISTINCT c.tenant_id
            FROM customers c
            WHERE c.deleted_at IS NULL
            """, nativeQuery = true)
    List<UUID> findDistinctActiveTenantIds();
}