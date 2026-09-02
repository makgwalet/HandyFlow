package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvInvoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvInvoiceRepository extends JpaRepository<TrainProvInvoice, UUID> {

    @Query("""
        SELECT i FROM TrainProvInvoice i
        WHERE i.tenantId = :#{#tenantId.value} AND i.clientId = :clientId
        ORDER BY i.issueDate DESC
        """)
    Page<TrainProvInvoice> findAllForClient(TenantId tenantId, UUID clientId, Pageable pageable);

    @Query("SELECT i FROM TrainProvInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.id = :id")
    Optional<TrainProvInvoice> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT i FROM TrainProvInvoice i WHERE i.tenantId = :#{#tenantId.value} AND i.clientId = :clientId ORDER BY i.periodEnd DESC")
    List<TrainProvInvoice> findAllForClientList(TenantId tenantId, UUID clientId);

    /** Cross-tenant sweep — overdue (past dueDate, not yet PAID) invoices for the daily scheduler. */
    @Query("""
        SELECT i FROM TrainProvInvoice i
        WHERE i.status IN ('SENT', 'PARTIAL')
        AND i.dueDate < CURRENT_DATE
        """)
    List<TrainProvInvoice> findOverdueAcrossTenants();
}
