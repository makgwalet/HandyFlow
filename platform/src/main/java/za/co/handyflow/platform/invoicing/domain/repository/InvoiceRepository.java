package za.co.handyflow.platform.invoicing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("""
        SELECT i FROM Invoice i
        WHERE i.tenantId = :tenantId
        AND i.deletedAt IS NULL
        ORDER BY i.createdAt DESC
        """)
    Page<Invoice> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT i FROM Invoice i
        LEFT JOIN FETCH i.lineItems
        WHERE i.tenantId = :tenantId
        AND i.id = :id
        AND i.deletedAt IS NULL
        """)
    Optional<Invoice> findActiveByIdWithLineItems(TenantId tenantId, UUID id);

    // JPQL with SpEL — TenantId works here via :#{#tenantId.value} unwrapping
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.tenantId = :#{#tenantId.value} AND i.deletedAt IS NULL")
    long countAllByTenantId(TenantId tenantId);

    /**
     * FIX: "no statement of account PDF" gap — no existing query filtered
     * invoices by customer at all. Deliberately no lineItems JOIN FETCH: a
     * statement shows per-invoice totals, not line-item detail, so this
     * avoids pulling data the statement never renders.
     */
    @Query("""
        SELECT i FROM Invoice i
        WHERE i.tenantId = :tenantId
        AND i.customerId = :customerId
        AND i.deletedAt IS NULL
        ORDER BY i.createdAt DESC
        """)
    List<Invoice> findByCustomer(TenantId tenantId, UUID customerId);

    // WHY CAST(:tenantId AS uuid)?
    // Neither :tenantId::uuid (named param) nor ?1::uuid (positional param) work in
    // Hibernate 6 native queries — the :: suffix is parsed as part of the parameter label.
    // CAST(:tenantId AS uuid) is ANSI SQL that PostgreSQL supports and Hibernate parses
    // the parameter as just :tenantId (a plain string), then wraps it in a SQL CAST at
    // the DB level — no colon collision, no label parsing issue.
    @Query(value = """
        SELECT * FROM invoices
        WHERE tenant_id = CAST(:tenantId AS uuid)
        AND deleted_at IS NULL
        AND status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERPAID')
        AND (
            (issued_at IS NOT NULL AND issued_at::date >= :from AND issued_at::date <= :to)
            OR
            (issued_at IS NULL AND created_at::date >= :from AND created_at::date <= :to)
        )
        ORDER BY created_at DESC
        """, nativeQuery = true)
    List<Invoice> findAllForVat(
            @Param("tenantId") String tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(value = """
        SELECT * FROM invoices
        WHERE tenant_id = CAST(:tenantId AS uuid)
        AND deleted_at IS NULL
        AND status IN ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE')
        ORDER BY due_date ASC NULLS LAST
        """, nativeQuery = true)
    List<Invoice> findOutstandingForTenant(@Param("tenantId") String tenantId);

    // Cross-tenant by design — same pattern as QuoteRepository.findExpiredQuotes
    // and RecurringScheduleRepository.findDueSchedules. This is a nightly
    // batch job's query, not a tenant-scoped read, so no tenantId filter.
    @Query("""
        SELECT i FROM Invoice i
        WHERE i.status IN (za.co.handyflow.platform.invoicing.domain.model.InvoiceStatus.ISSUED,
                            za.co.handyflow.platform.invoicing.domain.model.InvoiceStatus.PARTIALLY_PAID,
                            za.co.handyflow.platform.invoicing.domain.model.InvoiceStatus.OVERDUE)
        AND i.dueDate IS NOT NULL
        AND i.dueDate < :today
        AND i.deletedAt IS NULL
        """)
    List<Invoice> findOverdueInvoices(@Param("today") LocalDate today);
}