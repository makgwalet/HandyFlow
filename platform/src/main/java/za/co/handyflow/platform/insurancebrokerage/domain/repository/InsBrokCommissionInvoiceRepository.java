package za.co.handyflow.platform.insurancebrokerage.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokCommissionInvoice;

import java.util.Optional;
import java.util.UUID;

public interface InsBrokCommissionInvoiceRepository extends JpaRepository<InsBrokCommissionInvoice, UUID> {

    @Query("SELECT i FROM InsBrokCommissionInvoice i WHERE i.tenantId = :tenantId AND i.clientId = :clientId ORDER BY i.invoiceDate DESC")
    Page<InsBrokCommissionInvoice> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT i FROM InsBrokCommissionInvoice i WHERE i.tenantId = :tenantId ORDER BY i.invoiceDate DESC")
    Page<InsBrokCommissionInvoice> findAllForTenant(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT i FROM InsBrokCommissionInvoice i WHERE i.tenantId = :tenantId AND i.policyId = :policyId")
    Optional<InsBrokCommissionInvoice> findByPolicy(@Param("tenantId") UUID tenantId, @Param("policyId") UUID policyId);

    @Query("SELECT i FROM InsBrokCommissionInvoice i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<InsBrokCommissionInvoice> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
