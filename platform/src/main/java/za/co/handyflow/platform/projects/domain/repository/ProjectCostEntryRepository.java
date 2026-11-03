package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.enums.CostEntryType;
import za.co.handyflow.platform.projects.domain.model.ProjectCostEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectCostEntryRepository extends JpaRepository<ProjectCostEntry, UUID> {

    @Query("SELECT e FROM ProjectCostEntry e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<ProjectCostEntry> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT e FROM ProjectCostEntry e WHERE e.tenantId = :tenantId AND e.idempotencyKey = :key")
    Optional<ProjectCostEntry> findByKey(@Param("tenantId") UUID tenantId, @Param("key") String key);

    @Query("SELECT COUNT(e) > 0 FROM ProjectCostEntry e WHERE e.reversalOf = :entryId")
    boolean isReversed(@Param("entryId") UUID entryId);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM ProjectCostEntry e
            WHERE e.budgetLineId = :lineId AND e.entryType = :type
            """)
    BigDecimal sumByLineAndType(@Param("lineId") UUID lineId, @Param("type") CostEntryType type);

    @Query("SELECT COUNT(e) FROM ProjectCostEntry e WHERE e.budgetLineId = :lineId")
    long countByLine(@Param("lineId") UUID lineId);

    @Query("""
            SELECT e FROM ProjectCostEntry e
            WHERE e.tenantId = :tenantId AND e.projectId = :projectId
            ORDER BY e.createdAt DESC
            """)
    List<ProjectCostEntry> findByProject(@Param("tenantId") UUID tenantId,
                                         @Param("projectId") UUID projectId, Pageable pageable);

    @Query("""
            SELECT e FROM ProjectCostEntry e
            WHERE e.tenantId = :tenantId AND e.projectId = :projectId AND e.budgetLineId = :lineId
            ORDER BY e.createdAt DESC
            """)
    List<ProjectCostEntry> findByProjectAndLine(@Param("tenantId") UUID tenantId,
                                                @Param("projectId") UUID projectId,
                                                @Param("lineId") UUID lineId, Pageable pageable);
}