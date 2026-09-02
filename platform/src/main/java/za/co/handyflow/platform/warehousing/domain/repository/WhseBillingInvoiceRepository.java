package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhseBillingInvoiceRepository extends JpaRepository<WhseBillingInvoice, UUID> {

    @Query("SELECT i FROM WhseBillingInvoice i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<WhseBillingInvoice> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM WhseBillingInvoice i WHERE i.tenantId = :tenantId AND i.clientId = :clientId ORDER BY i.invoiceDate DESC")
    Page<WhseBillingInvoice> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    /** The most recently issued invoice for a client — its periodEnd is the start of the next billing period. */
    @Query("SELECT i FROM WhseBillingInvoice i WHERE i.tenantId = :tenantId AND i.clientId = :clientId ORDER BY i.periodEnd DESC LIMIT 1")
    Optional<WhseBillingInvoice> findMostRecentForClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

    /** Cross-tenant sweep for the overdue-invoice notification scheduler. */
    @Query("SELECT i FROM WhseBillingInvoice i WHERE i.status IN ('SENT', 'PARTIAL') AND i.dueDate < :today")
    List<WhseBillingInvoice> findOverdueAcrossTenants(@Param("today") LocalDate today);
}
