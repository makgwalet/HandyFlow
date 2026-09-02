package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkInvoice;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link BkInvoice} has no {@code deletedAt} — a financial record, never deleted. Direct mirror of {@code FmInvoiceRepository}. */
public interface BkInvoiceRepository extends JpaRepository<BkInvoice, UUID> {

    @Query("SELECT i FROM BkInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.id = :id")
    Optional<BkInvoice> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM BkInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.clientId = :clientId ORDER BY i.issueDate DESC")
    Page<BkInvoice> findAllActiveForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT i FROM BkInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.clientId = :clientId ORDER BY i.issueDate DESC")
    List<BkInvoice> findAllForClientList(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    /** Cross-tenant sweep — overdue (past dueDate, not yet PAID) invoices for the daily scheduler. */
    @Query("SELECT i FROM BkInvoice i WHERE i.status IN ('SENT','PARTIAL') AND i.dueDate < CURRENT_DATE")
    List<BkInvoice> findOverdueAcrossTenants();
}
