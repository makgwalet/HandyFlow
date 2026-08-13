package za.co.handyflow.platform.recruitmentagency.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecAgencyInvoice;

import java.util.Optional;
import java.util.UUID;

public interface RecAgencyInvoiceRepository extends JpaRepository<RecAgencyInvoice, UUID> {

    @Query("SELECT i FROM RecAgencyInvoice i WHERE i.clientId = :clientId ORDER BY i.invoiceDate DESC")
    Page<RecAgencyInvoice> findByClient(@Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT i FROM RecAgencyInvoice i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<RecAgencyInvoice> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    boolean existsByPlacementId(UUID placementId);
}