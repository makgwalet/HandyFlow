package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.SnagItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SnagItemRepository extends JpaRepository<SnagItem, UUID> {

    @Query("SELECT s FROM SnagItem s WHERE s.projectId = :projectId ORDER BY s.severity DESC, s.createdAt DESC")
    List<SnagItem> findByProject(UUID projectId);

    @Query("SELECT s FROM SnagItem s WHERE s.projectId = :projectId AND s.status IN ('OPEN','IN_PROGRESS') ORDER BY s.severity DESC")
    List<SnagItem> findOpenByProject(UUID projectId);

    @Query("SELECT s FROM SnagItem s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<SnagItem> findByTenantAndId(UUID tenantId, UUID id);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(s.snagNumber, 3) AS int)), 0) FROM SnagItem s WHERE s.projectId = :projectId AND s.snagNumber LIKE 'SN%'")
    int findMaxSequence(UUID projectId);
}
