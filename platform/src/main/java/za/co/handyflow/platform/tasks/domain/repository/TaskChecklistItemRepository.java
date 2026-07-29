package za.co.handyflow.platform.tasks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.tasks.domain.model.TaskChecklistItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, UUID> {

    List<TaskChecklistItem> findByTaskIdOrderBySortOrderAsc(UUID taskId);

    /** Scoped by taskId too, not just id — prevents toggling/deleting an item via a mismatched task path. */
    Optional<TaskChecklistItem> findByIdAndTaskId(UUID id, UUID taskId);

    /**
     * Batch progress counts — same N+1 avoidance as TaskCommentRepository.countByTaskIds(),
     * used when loading a board's task list so each card can show "3/5" without a
     * per-task query.
     */
    @Query("""
        SELECT i.taskId, COUNT(i), SUM(CASE WHEN i.completed = true THEN 1 ELSE 0 END)
        FROM TaskChecklistItem i WHERE i.taskId IN :taskIds GROUP BY i.taskId
        """)
    List<Object[]> countProgressByTaskIdsRaw(List<UUID> taskIds);

    default Map<UUID, ChecklistProgress> countProgressByTaskIds(List<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return Map.of();
        return countProgressByTaskIdsRaw(taskIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> new ChecklistProgress(
                                ((Number) row[1]).intValue(),
                                row[2] != null ? ((Number) row[2]).intValue() : 0)));
    }

    record ChecklistProgress(int total, int completed) {
    }
}