package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectTransmittalRepository extends JpaRepository<ProjectTransmittal, UUID> {

    @Query("SELECT t FROM ProjectTransmittal t WHERE t.tenantId = :tenantId AND t.projectId = :projectId ORDER BY t.sentAt DESC")
    List<ProjectTransmittal> findByProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

    @Query("SELECT t FROM ProjectTransmittal t WHERE t.tenantId = :tenantId AND t.projectId = :projectId AND t.id = :id")
    Optional<ProjectTransmittal> findByProjectAndId(@Param("tenantId") UUID tenantId,
                                                    @Param("projectId") UUID projectId,
                                                    @Param("id") UUID id);
}