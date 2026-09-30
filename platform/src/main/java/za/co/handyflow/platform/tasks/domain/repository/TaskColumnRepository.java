package za.co.handyflow.platform.tasks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.tasks.domain.model.TaskColumn;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskColumnRepository extends JpaRepository<TaskColumn, UUID> {
    List<TaskColumn> findByBoardIdOrderBySortOrderAsc(UUID boardId);
    Optional<TaskColumn> findFirstByBoardIdOrderBySortOrderAsc(UUID boardId);

    /**
     * The only safe way to load a column from a client-supplied id: it must belong to THIS board and
     * THIS tenant. A bare findById(columnId) let a caller point at another tenant's column.
     */
    Optional<TaskColumn> findByIdAndBoardIdAndTenantId(UUID id, UUID boardId, UUID tenantId);
}
