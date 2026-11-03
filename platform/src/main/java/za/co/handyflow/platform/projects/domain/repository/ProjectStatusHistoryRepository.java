package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectStatusHistory;

import java.util.List;
import java.util.UUID;

public interface ProjectStatusHistoryRepository extends JpaRepository<ProjectStatusHistory, UUID> {

    @Query("""
            SELECT h FROM ProjectStatusHistory h
            WHERE h.tenantId = :tenantId AND h.projectId = :projectId
            ORDER BY h.changedAt DESC
            """)
    List<ProjectStatusHistory> findByProject(@Param("tenantId") UUID tenantId,
                                             @Param("projectId") UUID projectId);
}