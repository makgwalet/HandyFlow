package za.co.handyflow.platform.tasks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.tasks.domain.model.TaskColumn;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskColumnRepository extends JpaRepository<TaskColumn, UUID> {
    List<TaskColumn> findByBoardIdOrderBySortOrderAsc(UUID boardId);
    Optional<TaskColumn> findFirstByBoardIdOrderBySortOrderAsc(UUID boardId);
}
