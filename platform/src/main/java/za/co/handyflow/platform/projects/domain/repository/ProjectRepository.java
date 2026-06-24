package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.Project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId AND p.status != 'CANCELLED' ORDER BY p.createdAt DESC")
    Page<Project> findActive(UUID tenantId, Pageable pageable);

    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId AND p.status = :status ORDER BY p.createdAt DESC")
    Page<Project> findByStatus(UUID tenantId, String status, Pageable pageable);

    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId AND p.clientId = :clientId AND p.status != 'CANCELLED' ORDER BY p.createdAt DESC")
    List<Project> findByClient(UUID tenantId, UUID clientId);

    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<Project> findByTenantAndId(UUID tenantId, UUID id);

    @Query("SELECT p FROM Project p WHERE p.clientPortalToken = :token")
    Optional<Project> findByPortalToken(String token);

    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId AND p.projectManagerId = :managerId AND p.status = 'ACTIVE' ORDER BY p.createdAt DESC")
    List<Project> findActiveByManager(UUID tenantId, UUID managerId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(p.projectNumber, 4) AS int)), 0) FROM Project p WHERE p.tenantId = :tenantId AND p.projectNumber LIKE 'PRJ%'")
    int findMaxProjectSequence(UUID tenantId);

    // Summary counts for dashboard
    @Query("SELECT COUNT(p) FROM Project p WHERE p.tenantId = :tenantId AND p.status = 'ACTIVE'")
    long countActive(UUID tenantId);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.tenantId = :tenantId AND p.health = :health AND p.status = 'ACTIVE'")
    long countByHealth(UUID tenantId, String health);
}
