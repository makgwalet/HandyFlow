package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectRfiEvent;

import java.util.List;
import java.util.UUID;

public interface ProjectRfiEventRepository extends JpaRepository<ProjectRfiEvent, UUID> {

    @Query("SELECT e FROM ProjectRfiEvent e WHERE e.tenantId = :tenantId AND e.rfiId = :rfiId ORDER BY e.occurredAt")
    List<ProjectRfiEvent> findByRfi(@Param("tenantId") UUID tenantId, @Param("rfiId") UUID rfiId);
}