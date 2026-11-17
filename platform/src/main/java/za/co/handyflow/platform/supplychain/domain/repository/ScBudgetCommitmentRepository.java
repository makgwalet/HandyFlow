package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.PoStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScBudgetCommitment;
import za.co.handyflow.platform.supplychain.domain.model.ScPurchaseOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScBudgetCommitmentRepository extends JpaRepository<ScBudgetCommitment, UUID> {

    @Query("SELECT c FROM ScBudgetCommitment c WHERE c.tenantId = :tenantId AND c.purchaseOrderId = :poId")
    Optional<ScBudgetCommitment> findByPo(@Param("tenantId") UUID tenantId, @Param("poId") UUID poId);

    /** Approved-and-beyond POs that predate budgets and have no commitment row yet. */
    @Query("""
            SELECT p FROM ScPurchaseOrder p
            WHERE p.tenantId = :tenantId AND p.status IN :statuses
              AND NOT EXISTS (SELECT 1 FROM ScBudgetCommitment c WHERE c.purchaseOrderId = p.id)
            """)
    List<ScPurchaseOrder> findPosWithoutCommitment(@Param("tenantId") UUID tenantId,
                                                   @Param("statuses") List<PoStatus> statuses);
}
