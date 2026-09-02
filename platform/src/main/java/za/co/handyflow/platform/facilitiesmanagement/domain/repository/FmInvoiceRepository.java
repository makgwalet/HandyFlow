package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmInvoiceRepository extends JpaRepository<FmInvoice, UUID> {

    @Query("SELECT i FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.id = :id")
    Optional<FmInvoice> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.clientId = :clientId ORDER BY i.issueDate DESC")
    Page<FmInvoice> findAllActiveForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT i FROM FmInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.clientId = :clientId ORDER BY i.issueDate DESC")
    List<FmInvoice> findAllForClientList(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    /** Cross-tenant sweep — overdue (past dueDate, not yet PAID) invoices for the daily scheduler. */
    @Query("SELECT i FROM FmInvoice i WHERE i.status IN ('SENT','PARTIAL') AND i.dueDate < CURRENT_DATE")
    List<FmInvoice> findOverdueAcrossTenants();
}
