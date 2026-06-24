package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.ChangeOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChangeOrderRepository extends JpaRepository<ChangeOrder, UUID> {

    @Query("SELECT c FROM ChangeOrder c WHERE c.projectId = :projectId ORDER BY c.createdAt DESC")
    List<ChangeOrder> findByProject(UUID projectId);

    @Query("SELECT c FROM ChangeOrder c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<ChangeOrder> findByTenantAndId(UUID tenantId, UUID id);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(c.changeNumber, 4) AS int)), 0) FROM ChangeOrder c WHERE c.projectId = :projectId AND c.changeNumber LIKE 'CO-%'")
    int findMaxSequence(UUID projectId);

    @Query("SELECT COALESCE(SUM(c.costImpact), 0) FROM ChangeOrder c WHERE c.projectId = :projectId AND c.status = 'APPROVED'")
    BigDecimal sumApprovedCostImpact(UUID projectId);
}
