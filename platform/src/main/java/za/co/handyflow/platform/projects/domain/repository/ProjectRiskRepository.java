package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.ProjectRisk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRiskRepository extends JpaRepository<ProjectRisk, UUID> {

    @Query("SELECT r FROM ProjectRisk r WHERE r.projectId = :projectId ORDER BY r.probability * r.impact DESC")
    List<ProjectRisk> findByProject(UUID projectId);

    @Query("SELECT r FROM ProjectRisk r WHERE r.tenantId = :tenantId AND r.rating = 'RED' AND r.status = 'OPEN' ORDER BY r.updatedAt DESC")
    List<ProjectRisk> findOpenRedRisks(UUID tenantId);

    @Query("SELECT r FROM ProjectRisk r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<ProjectRisk> findByTenantAndId(UUID tenantId, UUID id);
}
