package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpTaskEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface LpTaskEventRepository extends JpaRepository<LpTaskEvent, UUID> {

    @Query("SELECT e FROM LpTaskEvent e WHERE e.tenantId = :tenantId AND e.taskId = :taskId ORDER BY e.occurredAt DESC")
    List<LpTaskEvent> findRecentForTask(TenantId tenantId, UUID taskId, Pageable pageable);
}
