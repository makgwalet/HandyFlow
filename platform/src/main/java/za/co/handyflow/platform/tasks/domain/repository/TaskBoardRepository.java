package za.co.handyflow.platform.tasks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.tasks.domain.model.TaskBoard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskBoardRepository extends JpaRepository<TaskBoard, UUID> {
    List<TaskBoard> findByTenantIdAndArchivedFalseOrderByIsDefaultDescCreatedAtAsc(TenantId tenantId);
    Optional<TaskBoard> findByIdAndTenantId(UUID id, TenantId tenantId);
    Optional<TaskBoard> findByTenantIdAndIsDefaultTrue(TenantId tenantId);
}
