package za.co.handyflow.platform.invoicing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.invoicing.domain.model.CreditNote;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    @Query("SELECT c FROM CreditNote c WHERE c.tenantId = :tenantId ORDER BY c.createdAt DESC")
    Page<CreditNote> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT c FROM CreditNote c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<CreditNote> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT c FROM CreditNote c WHERE c.tenantId = :tenantId AND c.invoiceId = :invoiceId ORDER BY c.createdAt DESC")
    List<CreditNote> findByInvoice(TenantId tenantId, UUID invoiceId);

    // Credit notes netted per invoice, excluding VAT: rows are [invoiceId, sum of subtotals]. Callers must not pass an empty collection.
    @Query("SELECT c.invoiceId, SUM(c.subtotal) FROM CreditNote c WHERE c.tenantId = :tenantId AND c.invoiceId IN :invoiceIds GROUP BY c.invoiceId")
    List<Object[]> sumSubtotalByInvoice(TenantId tenantId, Collection<UUID> invoiceIds);
}
