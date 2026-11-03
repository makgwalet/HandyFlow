package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.TaskDependency;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

    @Query("SELECT d FROM TaskDependency d WHERE d.predecessorId = :taskId OR d.successorId = :taskId")
    List<TaskDependency> findByTask(UUID taskId);

    @Query("SELECT d FROM TaskDependency d WHERE d.successorId = :taskId")
    List<TaskDependency> findPredecessors(UUID taskId);

    @Query("SELECT d FROM TaskDependency d WHERE d.predecessorId = :taskId")
    List<TaskDependency> findSuccessors(UUID taskId);

    @Query("SELECT COUNT(d) > 0 FROM TaskDependency d WHERE d.predecessorId = :predId AND d.successorId = :succId")
    boolean existsDependency(UUID predId, UUID succId);

    // ── CHUNK 3 ──────────────────────────────────────────────────────────────

    /** Every dependency whose tasks belong to the project (a dependency never crosses projects). */
    @Query("""
            SELECT d FROM TaskDependency d
            WHERE d.tenantId = :tenantId
              AND d.predecessorId IN (SELECT t.id FROM ProjectTask t WHERE t.projectId = :projectId)
            """)
    List<TaskDependency> findByProject(@Param("tenantId") UUID tenantId,
                                       @Param("projectId") UUID projectId);

    @Query("SELECT d FROM TaskDependency d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<TaskDependency> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                               @Param("id") UUID id);
}