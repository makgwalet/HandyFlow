package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustReconciliation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpTrustReconciliationRepository extends JpaRepository<LpTrustReconciliation, UUID> {

    @Query("SELECT r FROM LpTrustReconciliation r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<LpTrustReconciliation> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT r FROM LpTrustReconciliation r WHERE r.tenantId = :tenantId ORDER BY r.periodEnd DESC, r.preparedAt DESC")
    Page<LpTrustReconciliation> list(TenantId tenantId, Pageable pageable);

    /** The draft or submitted reconciliation, if there is one: only one is open at a time. */
    @Query("SELECT r FROM LpTrustReconciliation r WHERE r.tenantId = :tenantId AND r.status IN ('DRAFT','SUBMITTED')")
    List<LpTrustReconciliation> findOpen(TenantId tenantId);

    /** Approved reconciliations, latest period first. */
    @Query("SELECT r FROM LpTrustReconciliation r WHERE r.tenantId = :tenantId AND r.status = 'APPROVED' ORDER BY r.periodEnd DESC")
    List<LpTrustReconciliation> findApproved(TenantId tenantId);
}
