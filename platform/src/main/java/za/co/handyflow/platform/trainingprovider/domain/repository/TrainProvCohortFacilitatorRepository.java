package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCohortFacilitator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvCohortFacilitatorRepository extends JpaRepository<TrainProvCohortFacilitator, UUID> {

    @Query("SELECT f FROM TrainProvCohortFacilitator f WHERE f.tenantId = :#{#tenantId.value} AND f.cohortId = :cohortId ORDER BY f.status, f.addedAt")
    List<TrainProvCohortFacilitator> findForCohort(TenantId tenantId, UUID cohortId);

    @Query("SELECT f FROM TrainProvCohortFacilitator f WHERE f.tenantId = :#{#tenantId.value} AND f.id = :id")
    Optional<TrainProvCohortFacilitator> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(f) > 0 FROM TrainProvCohortFacilitator f WHERE f.tenantId = :#{#tenantId.value} AND f.cohortId = :cohortId AND f.practitionerId = :practitionerId AND f.status = 'ACTIVE'")
    boolean liveExists(TenantId tenantId, UUID cohortId, UUID practitionerId);

    @Query("SELECT COUNT(f) FROM TrainProvCohortFacilitator f WHERE f.tenantId = :#{#tenantId.value} AND f.cohortId = :cohortId AND f.status = 'ACTIVE'")
    long countActive(TenantId tenantId, UUID cohortId);

    @Query("SELECT f FROM TrainProvCohortFacilitator f WHERE f.tenantId = :#{#tenantId.value} AND f.cohortId = :cohortId AND f.practitionerId = :practitionerId AND f.status = 'ACTIVE'")
    Optional<TrainProvCohortFacilitator> findLive(TenantId tenantId, UUID cohortId, UUID practitionerId);
}
