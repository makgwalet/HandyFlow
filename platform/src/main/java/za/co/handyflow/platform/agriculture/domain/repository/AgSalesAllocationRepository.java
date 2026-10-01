package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgSalesAllocation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgSalesAllocationRepository extends JpaRepository<AgSalesAllocation, UUID> {

    @Query("SELECT a FROM AgSalesAllocation a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.status = 'ACTIVE' ORDER BY a.soldOn DESC, a.createdAt DESC")
    Page<AgSalesAllocation> findActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    @Query("SELECT a FROM AgSalesAllocation a WHERE a.tenantId = :tenantId AND a.targetType = :targetType AND a.targetId = :targetId AND a.status = 'ACTIVE' ORDER BY a.soldOn DESC, a.createdAt DESC")
    Page<AgSalesAllocation> findActiveForTarget(TenantId tenantId, String targetType, UUID targetId, Pageable pageable);

    @Query("SELECT a FROM AgSalesAllocation a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<AgSalesAllocation> findForTenantById(TenantId tenantId, UUID id);

    // Every active allocation of these invoice lines, whichever farm it is on: a line's revenue is shared across ALL of them, so apportioning
    // it correctly needs the whole set. Ordered so the cent that rounding leaves over always lands the same way.
    @Query("SELECT a FROM AgSalesAllocation a WHERE a.tenantId = :tenantId AND a.invoiceLineId IN :lineIds AND a.status = 'ACTIVE' ORDER BY a.createdAt ASC, a.id ASC")
    List<AgSalesAllocation> findActiveByInvoiceLines(TenantId tenantId, Collection<UUID> lineIds);

    // Quantity already allocated per invoice line, across all farms: rows are [invoiceLineId, quantity]. Callers must not pass an empty collection.
    @Query("SELECT a.invoiceLineId, SUM(a.quantity) FROM AgSalesAllocation a WHERE a.tenantId = :tenantId AND a.invoiceLineId IN :lineIds AND a.status = 'ACTIVE' GROUP BY a.invoiceLineId")
    List<Object[]> sumActiveQuantityByInvoiceLine(TenantId tenantId, Collection<UUID> lineIds);
}
