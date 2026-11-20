package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustReconItem;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpTrustReconItemRepository extends JpaRepository<LpTrustReconItem, UUID> {

    @Query("SELECT i FROM LpTrustReconItem i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<LpTrustReconItem> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT i FROM LpTrustReconItem i WHERE i.tenantId = :tenantId AND i.reconciliationId = :reconciliationId ORDER BY i.itemDate ASC, i.createdAt ASC")
    List<LpTrustReconItem> findForReconciliation(TenantId tenantId, UUID reconciliationId);
}
