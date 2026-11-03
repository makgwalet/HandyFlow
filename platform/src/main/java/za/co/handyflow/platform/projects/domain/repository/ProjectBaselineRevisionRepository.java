package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectBaselineRevision;

import java.util.List;
import java.util.UUID;

public interface ProjectBaselineRevisionRepository extends JpaRepository<ProjectBaselineRevision, UUID> {

    @Query("""
            SELECT r FROM ProjectBaselineRevision r
            WHERE r.tenantId = :tenantId AND r.projectId = :projectId
            ORDER BY r.version DESC
            """)
    List<ProjectBaselineRevision> findByProject(@Param("tenantId") UUID tenantId,
                                                @Param("projectId") UUID projectId);

    @Query("SELECT COUNT(r) > 0 FROM ProjectBaselineRevision r WHERE r.projectId = :projectId AND r.version = :version")
    boolean existsByProjectAndVersion(@Param("projectId") UUID projectId, @Param("version") int version);
}