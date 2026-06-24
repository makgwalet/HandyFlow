package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.ProjectPhase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPhaseRepository extends JpaRepository<ProjectPhase, UUID> {

    @Query("SELECT p FROM ProjectPhase p WHERE p.projectId = :projectId ORDER BY p.sortOrder, p.createdAt")
    List<ProjectPhase> findByProject(UUID projectId);

    @Query("SELECT p FROM ProjectPhase p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<ProjectPhase> findByTenantAndId(UUID tenantId, UUID id);

    @Query("SELECT COALESCE(MAX(p.sortOrder), 0) FROM ProjectPhase p WHERE p.projectId = :projectId")
    int findMaxSortOrder(UUID projectId);
}
