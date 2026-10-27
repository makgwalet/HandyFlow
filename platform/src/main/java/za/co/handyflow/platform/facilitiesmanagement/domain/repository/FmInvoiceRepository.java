package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmInvoiceRepository extends JpaRepository<FmInvoice, UUID> {

    @Query("SELECT i FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.id = :id")
    Optional<FmInvoice> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.clientId = :clientId ORDER BY i.issueDate DESC")
    Page<FmInvoice> findAllActiveForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT i FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.clientId = :clientId ORDER BY i.issueDate DESC")
    List<FmInvoice> findAllForClientList(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    /**
     * Whether the retainer already has a LIVE (non-cancelled) invoice whose billing period overlaps
     * [periodStart, periodEnd] — stops one retainer being invoiced twice for the same period. A cancelled draft
     * frees the period again; invoices of OTHER retainers, or of chargeable work, do not interfere.
     */
    @Query("SELECT COUNT(i) > 0 FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.agreementId = :agreementId " +
            "AND i.status <> 'CANCELLED' AND i.periodStart <= :periodEnd AND i.periodEnd >= :periodStart")
    boolean existsOverlappingForAgreement(@Param("tenantId") TenantId tenantId, @Param("agreementId") UUID agreementId,
                                          @Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);

    /** Cross-tenant sweep — overdue (past dueDate, sent, not yet PAID) invoices for the daily scheduler. */
    @Query("SELECT i FROM FmInvoice i WHERE i.status IN ('SENT','PARTIAL') AND i.dueDate < CURRENT_DATE")
    List<FmInvoice> findOverdueAcrossTenants();

    /** Cross-tenant: approved invoices whose ledger posting is still owed and due for another attempt. */
    @Query("SELECT i FROM FmInvoice i WHERE i.glStatus IN ('PENDING','FAILED') AND i.glAttempts < :maxAttempts " +
            "AND ((i.glLastAttemptAt IS NULL AND i.approvedAt < :cutoff) OR i.glLastAttemptAt < :cutoff)")
    List<FmInvoice> findDueForGlRetry(@Param("cutoff") Instant cutoff, @Param("maxAttempts") int maxAttempts);

    /** Ledger posting exceptions for one tenant (failed, including those that exhausted their automatic retries). */
    @Query("SELECT i FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.glStatus = 'FAILED' ORDER BY i.approvedAt ASC")
    List<FmInvoice> findGlExceptions(@Param("tenantId") TenantId tenantId);

    /** Recognised revenue for a client in a period: approved-or-later invoices by issue date, excluding VAT. Null when none. */
    @Query("SELECT SUM(i.subtotal) FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.clientId = :clientId " +
            "AND i.status NOT IN ('DRAFT','CANCELLED') AND i.issueDate >= :from AND i.issueDate <= :to")
    BigDecimal sumRevenueForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to);


    // ── Reporting (client-scope aware) ──

    /** Invoices in the given statuses within client scope (receivables, drafts). */
    @Query("SELECT i FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.status IN :statuses " +
            "AND (:allClients = true OR i.clientId IN :clientIds)")
    List<FmInvoice> findByStatusesInScope(@Param("tenantId") TenantId tenantId, @Param("statuses") Collection<String> statuses,
                                          @Param("allClients") boolean allClients, @Param("clientIds") Collection<UUID> clientIds);

    /** Approved-or-later invoices issued in the period within client scope. */
    @Query("SELECT i FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.status NOT IN ('DRAFT','CANCELLED') " +
            "AND i.issueDate >= :from AND i.issueDate <= :to AND (:allClients = true OR i.clientId IN :clientIds)")
    List<FmInvoice> findIssuedBetweenInScope(@Param("tenantId") TenantId tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to,
                                             @Param("allClients") boolean allClients, @Param("clientIds") Collection<UUID> clientIds);
}
