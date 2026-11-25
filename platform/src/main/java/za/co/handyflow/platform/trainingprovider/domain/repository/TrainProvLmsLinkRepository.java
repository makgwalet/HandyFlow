package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLmsLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLmsLinkRepository extends JpaRepository<TrainProvLmsLink, UUID> {

    @Query("""
        SELECT l FROM TrainProvLmsLink l WHERE l.tenantId = :#{#tenantId.value}
        AND (CAST(:versionId AS uuid) IS NULL OR l.programmeVersionId = :versionId)
        AND (:status IS NULL OR l.status = :status)
        ORDER BY l.createdAt DESC
        """)
    List<TrainProvLmsLink> search(TenantId tenantId, UUID versionId, String status);

    @Query("SELECT l FROM TrainProvLmsLink l WHERE l.tenantId = :#{#tenantId.value} AND l.id = :id")
    Optional<TrainProvLmsLink> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(l) > 0 FROM TrainProvLmsLink l WHERE l.tenantId = :#{#tenantId.value} AND l.programmeVersionId = :versionId AND l.status = 'ACTIVE'")
    boolean activeExists(TenantId tenantId, UUID versionId);
}
