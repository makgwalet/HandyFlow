package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpTask;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpTaskRepository extends JpaRepository<LpTask, UUID> {

    @Query("SELECT t FROM LpTask t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<LpTask> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT t FROM LpTask t WHERE t.tenantId = :tenantId AND t.matterId = :matterId ORDER BY CASE t.status WHEN 'OPEN' THEN 0 WHEN 'IN_PROGRESS' THEN 0 ELSE 1 END, t.dueDate ASC NULLS LAST, t.createdAt ASC")
    List<LpTask> findForMatter(TenantId tenantId, UUID matterId);

    /**
     * Tasks across the firm, leaving out matters and clients a wall bars the caller from. {@code onlyAssignee} limits it to one person's tasks
     * ({@code assigneeId} is then used, otherwise it is a placeholder). An empty status means any live status (or any status with {@code includeClosed}).
     * Pattern is already %text%.
     */
    @Query("SELECT t FROM LpTask t WHERE t.tenantId = :tenantId AND t.matterId NOT IN :barredMatterIds AND t.clientId NOT IN :barredClientIds "
            + "AND (:onlyAssignee = false OR t.assigneeId = :assigneeId) "
            + "AND (:includeClosed = true OR t.status IN ('OPEN','IN_PROGRESS')) AND (:status = '' OR t.status = :status) "
            + "AND (:overdueOnly = false OR (t.status IN ('OPEN','IN_PROGRESS') AND t.dueDate < :today)) "
            + "AND LOWER(t.title) LIKE :pattern "
            + "ORDER BY t.dueDate ASC NULLS LAST, t.createdAt DESC")
    Page<LpTask> findFirmExcluding(TenantId tenantId, List<UUID> barredMatterIds, List<UUID> barredClientIds, boolean onlyAssignee, UUID assigneeId, boolean includeClosed,
                                   String status, boolean overdueOnly, LocalDate today, String pattern, Pageable pageable);

    @Query("SELECT COUNT(t) FROM LpTask t WHERE t.tenantId = :tenantId AND t.matterId NOT IN :barredMatterIds AND t.clientId NOT IN :barredClientIds "
            + "AND t.assigneeId = :assigneeId AND t.status IN ('OPEN','IN_PROGRESS') AND t.dueDate < :today")
    long countOverdueFor(TenantId tenantId, List<UUID> barredMatterIds, List<UUID> barredClientIds, UUID assigneeId, LocalDate today);
}
