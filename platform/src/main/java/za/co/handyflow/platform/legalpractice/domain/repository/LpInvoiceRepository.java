package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpInvoice;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface LpInvoiceRepository extends JpaRepository<LpInvoice, UUID> {

    @Query("SELECT i FROM LpInvoice i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<LpInvoice> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT i FROM LpInvoice i
        WHERE i.tenantId = :tenantId AND i.clientId = :clientId
        ORDER BY i.issueDate DESC
        """)
    Page<LpInvoice> findAllForClient(TenantId tenantId, UUID clientId, Pageable pageable);

    @Query("SELECT i FROM LpInvoice i WHERE i.tenantId = :tenantId ORDER BY i.issueDate DESC")
    Page<LpInvoice> findAllForFirm(TenantId tenantId, Pageable pageable);

    /**
     * Backs {@code LpBillingService}'s own sequential invoice numbering.
     * No dedicated {@code NumberGenerator} component (the pattern
     * {@code AccountantService.feeNoteNumberGen}/{@code JournalNumberGenerator}
     * use) was found or confirmed for this module — flagged as a
     * simplification rather than silently reimplementing an unconfirmed
     * shared generator.
     */
    @Query("SELECT COUNT(i) FROM LpInvoice i WHERE i.tenantId = :tenantId")
    long countForTenant(TenantId tenantId);
}
