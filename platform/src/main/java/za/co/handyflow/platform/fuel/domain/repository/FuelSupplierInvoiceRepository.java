package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplierInvoice;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelSupplierInvoiceRepository extends JpaRepository<FuelSupplierInvoice, UUID> {
    @Query("SELECT i FROM FuelSupplierInvoice i WHERE i.tenantId = :tenantId ORDER BY i.capturedAt DESC")
    List<FuelSupplierInvoice> findRecent(TenantId tenantId, Pageable page);

    @Query("SELECT i FROM FuelSupplierInvoice i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<FuelSupplierInvoice> findByTenantAndId(TenantId tenantId, UUID id);

    /** True when this supplier already has a live (not rejected) invoice with the number, ignoring case. */
    @Query("SELECT COUNT(i) > 0 FROM FuelSupplierInvoice i WHERE i.tenantId = :tenantId AND i.supplierId = :supplierId AND UPPER(i.invoiceNumber) = UPPER(:invoiceNumber) AND i.status <> 'REJECTED'")
    boolean existsLive(TenantId tenantId, UUID supplierId, String invoiceNumber);
}
