package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeResourceNeed;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvProgrammeResourceNeedRepository extends JpaRepository<TrainProvProgrammeResourceNeed, UUID> {

    @Query("SELECT n FROM TrainProvProgrammeResourceNeed n WHERE n.tenantId = :#{#tenantId.value} AND n.id = :id")
    Optional<TrainProvProgrammeResourceNeed> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT n FROM TrainProvProgrammeResourceNeed n WHERE n.tenantId = :#{#tenantId.value} AND n.programmeVersionId = :versionId ORDER BY n.status, n.createdAt")
    List<TrainProvProgrammeResourceNeed> forVersion(TenantId tenantId, UUID versionId);

    @Query("SELECT n FROM TrainProvProgrammeResourceNeed n WHERE n.tenantId = :#{#tenantId.value} AND n.programmeVersionId = :versionId AND n.status = 'ACTIVE' ORDER BY n.createdAt")
    List<TrainProvProgrammeResourceNeed> live(TenantId tenantId, UUID versionId);
}
