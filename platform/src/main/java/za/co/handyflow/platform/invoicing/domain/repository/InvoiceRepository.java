package za.co.handyflow.platform.invoicing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.shared.TenantId;

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
}