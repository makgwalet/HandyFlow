package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectMemberEvent;

import java.util.List;
import java.util.UUID;

public interface ProjectMemberEventRepository extends JpaRepository<ProjectMemberEvent, UUID> {

    @Query("""
            SELECT e FROM ProjectMemberEvent e
            WHERE e.tenantId = :tenantId AND e.projectId = :projectId
            ORDER BY e.occurredAt DESC
            """)
    List<ProjectMemberEvent> findByProject(@Param("tenantId") UUID tenantId,
                                           @Param("projectId") UUID projectId);
}