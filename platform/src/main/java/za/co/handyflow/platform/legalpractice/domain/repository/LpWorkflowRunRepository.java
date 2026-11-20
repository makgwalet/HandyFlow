package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpWorkflowRun;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpWorkflowRunRepository extends JpaRepository<LpWorkflowRun, UUID> {

    @Query("SELECT r FROM LpWorkflowRun r WHERE r.tenantId = :tenantId AND r.matterId = :matterId ORDER BY r.appliedAt DESC")
    List<LpWorkflowRun> findForMatter(TenantId tenantId, UUID matterId);

    @Query("SELECT r FROM LpWorkflowRun r WHERE r.tenantId = :tenantId AND r.matterId = :matterId AND r.templateId = :templateId")
    Optional<LpWorkflowRun> findOne(TenantId tenantId, UUID matterId, UUID templateId);
}
