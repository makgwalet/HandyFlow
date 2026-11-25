package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeOffering;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvProgrammeOfferingRepository extends JpaRepository<TrainProvProgrammeOffering, UUID> {

    @Query("SELECT o FROM TrainProvProgrammeOffering o WHERE o.tenantId = :#{#tenantId.value} AND o.programmeId = :programmeId ORDER BY o.status, o.offeredFrom DESC")
    List<TrainProvProgrammeOffering> findForProgramme(TenantId tenantId, UUID programmeId);

    @Query("SELECT o FROM TrainProvProgrammeOffering o WHERE o.tenantId = :#{#tenantId.value} AND o.id = :id")
    Optional<TrainProvProgrammeOffering> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(o) > 0 FROM TrainProvProgrammeOffering o WHERE o.tenantId = :#{#tenantId.value} AND o.programmeId = :programmeId AND o.siteId = :siteId AND o.status = 'ACTIVE'")
    boolean liveExists(TenantId tenantId, UUID programmeId, UUID siteId);

    @Query("SELECT COUNT(o) FROM TrainProvProgrammeOffering o WHERE o.tenantId = :#{#tenantId.value} AND o.programmeId = :programmeId AND o.status = 'ACTIVE'")
    long countLive(TenantId tenantId, UUID programmeId);

    @Query("SELECT COUNT(o) FROM TrainProvProgrammeOffering o WHERE o.tenantId = :#{#tenantId.value} AND o.siteId = :siteId AND o.status = 'ACTIVE'")
    long countLiveForSite(TenantId tenantId, UUID siteId);
}
