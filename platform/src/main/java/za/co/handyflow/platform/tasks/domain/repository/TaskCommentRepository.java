package za.co.handyflow.platform.tasks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.tasks.domain.model.TaskComment;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    /**
     * FIX: single count query per task — replaces .findByTaskId().size() in toTaskResponse().
     */
    @Query("SELECT COUNT(c) FROM TaskComment c WHERE c.taskId = :taskId")
    int countByTask(UUID taskId);

    /**
     * Batch comment count — avoids N+1 when loading a board's task list.
     * Returns a list of [taskId, count] pairs that the service maps into a UUID→Integer map.
     */
    @Query("SELECT c.taskId, COUNT(c) FROM TaskComment c WHERE c.taskId IN :taskIds GROUP BY c.taskId")
    List<Object[]> countByTaskIdsRaw(List<UUID> taskIds);

    default Map<UUID, Integer> countByTaskIds(List<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return Map.of();
        return countByTaskIdsRaw(taskIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).intValue()));
    }
}
