package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface WorkItemEventRepository extends JpaRepository<WorkItemEvent, UUID> {

    @Query("SELECT e FROM WorkItemEvent e WHERE e.tenantId = :tenantId AND e.workItemId = :workItemId ORDER BY e.createdAt ASC")
    List<WorkItemEvent> findByWorkItemId(TenantId tenantId, UUID workItemId);
}
