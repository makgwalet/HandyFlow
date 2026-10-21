package za.co.handyflow.platform.crm.domain.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.WorkflowRun;
import za.co.handyflow.platform.shared.TenantId;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {
    @Query("SELECT r FROM WorkflowRun r WHERE r.tenantId = :tenantId ORDER BY r.ranAt DESC, r.id")
    List<WorkflowRun> findRecent(@Param("tenantId") TenantId tenantId, Pageable pageable);
}
