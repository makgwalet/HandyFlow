package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScSupplierInvoiceRepository extends JpaRepository<ScSupplierInvoice, UUID> {

    @Query("SELECT i FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId ORDER BY i.createdAt DESC")
    Page<ScSupplierInvoice> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT i FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.status = :status ORDER BY i.createdAt DESC")
    Page<ScSupplierInvoice> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId,
                                                    @Param("status") String status, Pageable pageable);

    @Query("SELECT i FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<ScSupplierInvoice> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, 6) AS int)), 0) FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId")
    int findMaxInvoiceSequence(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(i) FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") String status);

    @Query("SELECT i FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.status NOT IN ('PAID','CANCELLED') AND i.dueDate < CURRENT_DATE")
    List<ScSupplierInvoice> findOverdue(@Param("tenantId") UUID tenantId);
}
