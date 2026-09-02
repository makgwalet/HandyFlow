package za.co.handyflow.platform.collectionsagency.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyCommissionInvoice;

import java.util.Optional;
import java.util.UUID;

public interface CollAgencyCommissionInvoiceRepository extends JpaRepository<CollAgencyCommissionInvoice, UUID> {

    @Query("SELECT i FROM CollAgencyCommissionInvoice i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<CollAgencyCommissionInvoice> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM CollAgencyCommissionInvoice i WHERE i.tenantId = :tenantId AND i.clientId = :clientId ORDER BY i.invoiceDate DESC")
    Page<CollAgencyCommissionInvoice> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, Pageable pageable);
}
