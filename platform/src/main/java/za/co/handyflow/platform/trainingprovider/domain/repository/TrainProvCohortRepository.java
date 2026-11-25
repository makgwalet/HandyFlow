package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCohort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvCohortRepository extends JpaRepository<TrainProvCohort, UUID> {

    @Query("""
        SELECT c FROM TrainProvCohort c WHERE c.tenantId = :#{#tenantId.value}
        AND (:status IS NULL OR c.status = :status)
        AND (CAST(:programmeId AS uuid) IS NULL OR c.programmeId = :programmeId)
        AND (CAST(:siteId AS uuid) IS NULL OR c.siteId = :siteId)
        ORDER BY c.startDate DESC, c.cohortNo DESC
        """)
    List<TrainProvCohort> search(TenantId tenantId, String status, UUID programmeId, UUID siteId);

    @Query("SELECT c FROM TrainProvCohort c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<TrainProvCohort> findForTenant(TenantId tenantId, UUID id);
}
