package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.ProjectTask;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, UUID> {

    @Query("SELECT t FROM ProjectTask t WHERE t.projectId = :projectId AND t.parentTaskId IS NULL ORDER BY t.sortOrder, t.plannedStart")
    List<ProjectTask> findRootTasks(UUID projectId);

    @Query("SELECT t FROM ProjectTask t WHERE t.projectId = :projectId ORDER BY t.sortOrder, t.plannedStart")
    List<ProjectTask> findByProject(UUID projectId);

    @Query("SELECT t FROM ProjectTask t WHERE t.phaseId = :phaseId ORDER BY t.sortOrder")
    List<ProjectTask> findByPhase(UUID phaseId);

    @Query("SELECT t FROM ProjectTask t WHERE t.tenantId = :tenantId AND t.assigneeId = :assigneeId AND t.status NOT IN ('COMPLETED','CANCELLED') ORDER BY t.plannedEnd")
    List<ProjectTask> findOpenByAssignee(UUID tenantId, UUID assigneeId);

    @Query("SELECT t FROM ProjectTask t WHERE t.projectId = :projectId AND t.isCritical = true ORDER BY t.plannedStart")
    List<ProjectTask> findCriticalPath(UUID projectId);

    @Query("SELECT t FROM ProjectTask t WHERE t.projectId = :projectId AND t.isMilestone = true ORDER BY t.plannedEnd")
    List<ProjectTask> findMilestones(UUID projectId);

    @Query("SELECT t FROM ProjectTask t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<ProjectTask> findByTenantAndId(UUID tenantId, UUID id);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(t.taskNumber, 2) AS int)), 0) FROM ProjectTask t WHERE t.projectId = :projectId AND t.taskNumber LIKE 'T%'")
    int findMaxTaskSequence(UUID projectId);
}
