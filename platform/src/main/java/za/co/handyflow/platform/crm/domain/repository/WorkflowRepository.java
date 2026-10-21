package za.co.handyflow.platform.crm.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.Workflow;
import za.co.handyflow.platform.crm.domain.model.WorkflowTrigger;
import za.co.handyflow.platform.shared.TenantId;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
    @Query("SELECT w FROM Workflow w WHERE w.tenantId = :tenantId AND w.deletedAt IS NULL ORDER BY w.createdAt, w.id")
    List<Workflow> findAllActive(@Param("tenantId") TenantId tenantId);

    @Query("SELECT w FROM Workflow w WHERE w.tenantId = :tenantId AND w.deletedAt IS NULL AND w.id = :id")
    Optional<Workflow> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT w FROM Workflow w WHERE w.tenantId = :tenantId AND w.deletedAt IS NULL AND w.active = TRUE AND w.trigger = :trigger ORDER BY w.createdAt, w.id")
    List<Workflow> findActiveFor(@Param("tenantId") TenantId tenantId, @Param("trigger") WorkflowTrigger trigger);
}
