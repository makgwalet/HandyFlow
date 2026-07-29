package za.co.handyflow.platform.tasks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.tasks.domain.model.TaskTimeLog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public interface TaskTimeLogRepository extends JpaRepository<TaskTimeLog, UUID> {

    List<TaskTimeLog> findByTaskIdOrderByLoggedDateDesc(UUID taskId);

    @Query("SELECT COALESCE(SUM(l.hours), 0) FROM TaskTimeLog l WHERE l.taskId = :taskId")
    BigDecimal sumHoursByTask(UUID taskId);

    /**
     * Batch hours sum — avoids N+1 when loading a board's task list.
     * Returns a list of [taskId, totalHours] pairs.
     */
    @Query("SELECT l.taskId, COALESCE(SUM(l.hours), 0) FROM TaskTimeLog l WHERE l.taskId IN :taskIds GROUP BY l.taskId")
    List<Object[]> sumHoursByTaskIdsRaw(List<UUID> taskIds);

    default Map<UUID, BigDecimal> sumHoursByTaskIds(List<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return Map.of();
        return sumHoursByTaskIdsRaw(taskIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (BigDecimal) row[1]));
    }

    /**
     * Batch fetch — backs the timesheet CSV export. Replaces what would
     * otherwise be one findByTaskIdOrderByLoggedDateDesc() call per task in
     * the board (same N+1 shape the board-load fix elsewhere in this
     * repository already eliminated for comment counts and hour sums).
     */
    List<TaskTimeLog> findByTaskIdInOrderByLoggedDateDesc(List<UUID> taskIds);
}