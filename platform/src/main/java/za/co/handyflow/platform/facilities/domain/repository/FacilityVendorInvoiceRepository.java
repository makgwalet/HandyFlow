package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendorInvoice;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityVendorInvoiceRepository extends JpaRepository<FacilityVendorInvoice, UUID> {

    @Query("SELECT i FROM FacilityVendorInvoice i WHERE i.tenantId.value = :#{#tenantId.value} AND i.id = :id")
    Optional<FacilityVendorInvoice> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM FacilityVendorInvoice i WHERE i.tenantId.value = :#{#tenantId.value} " +
            "AND i.purchaseOrderId = :purchaseOrderId ORDER BY i.createdAt ASC")
    List<FacilityVendorInvoice> findForPurchaseOrder(@Param("tenantId") TenantId tenantId, @Param("purchaseOrderId") UUID purchaseOrderId);

    @Query("SELECT COUNT(i) > 0 FROM FacilityVendorInvoice i WHERE i.tenantId.value = :#{#tenantId.value} " +
            "AND i.vendorId = :vendorId AND LOWER(i.invoiceNumber) = LOWER(:invoiceNumber)")
    boolean existsForVendor(@Param("tenantId") TenantId tenantId, @Param("vendorId") UUID vendorId, @Param("invoiceNumber") String invoiceNumber);

    /** {@code status} is a status name, or {@code ALL}. */
    @Query("SELECT i FROM FacilityVendorInvoice i WHERE i.tenantId.value = :#{#tenantId.value} " +
            "AND (:status = 'ALL' OR i.status = :status) ORDER BY i.createdAt DESC")
    Page<FacilityVendorInvoice> list(@Param("tenantId") TenantId tenantId, @Param("status") String status, Pageable pageable);
}
