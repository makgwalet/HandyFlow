package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvWorkplacePlacement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvWorkplacePlacementRepository extends JpaRepository<TrainProvWorkplacePlacement, UUID> {

    @Query("""
        SELECT p FROM TrainProvWorkplacePlacement p WHERE p.tenantId = :#{#tenantId.value}
        AND (CAST(:enrolmentId AS uuid) IS NULL OR p.enrolmentId = :enrolmentId)
        AND (:status IS NULL OR p.status = :status)
        ORDER BY p.placementNo DESC
        """)
    List<TrainProvWorkplacePlacement> search(TenantId tenantId, UUID enrolmentId, String status);

    @Query("SELECT p FROM TrainProvWorkplacePlacement p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<TrainProvWorkplacePlacement> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(p) > 0 FROM TrainProvWorkplacePlacement p WHERE p.tenantId = :#{#tenantId.value} AND p.enrolmentId = :enrolmentId AND p.status = 'ACTIVE'")
    boolean activeExists(TenantId tenantId, UUID enrolmentId);
}
