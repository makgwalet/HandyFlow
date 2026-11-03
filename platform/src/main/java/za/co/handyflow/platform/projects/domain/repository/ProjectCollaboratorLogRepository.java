package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectCollaboratorLog;

import java.util.List;
import java.util.UUID;

public interface ProjectCollaboratorLogRepository extends JpaRepository<ProjectCollaboratorLog, UUID> {

    @Query("""
            SELECT l FROM ProjectCollaboratorLog l
            WHERE l.tenantId = :tenantId AND l.collaboratorId = :collaboratorId
            ORDER BY l.occurredAt DESC
            """)
    List<ProjectCollaboratorLog> findByCollaborator(@Param("tenantId") UUID tenantId,
                                                    @Param("collaboratorId") UUID collaboratorId);
}
