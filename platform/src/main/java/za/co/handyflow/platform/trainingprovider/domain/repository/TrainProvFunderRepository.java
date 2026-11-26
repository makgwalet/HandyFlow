package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvFunder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvFunderRepository extends JpaRepository<TrainProvFunder, UUID> {

    @Query("""
        SELECT f FROM TrainProvFunder f WHERE f.tenantId = :#{#tenantId.value}
        AND (:type IS NULL OR f.funderType = :type)
        AND (:status IS NULL OR f.status = :status)
        ORDER BY f.name
        """)
    List<TrainProvFunder> search(TenantId tenantId, String type, String status);

    @Query("SELECT f FROM TrainProvFunder f WHERE f.tenantId = :#{#tenantId.value} AND f.id = :id")
    Optional<TrainProvFunder> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(f) > 0 FROM TrainProvFunder f WHERE f.tenantId = :#{#tenantId.value} AND f.status = 'ACTIVE'
        AND LOWER(f.name) = LOWER(:name) AND (CAST(:exceptId AS uuid) IS NULL OR f.id <> :exceptId)
        """)
    boolean nameTaken(TenantId tenantId, String name, UUID exceptId);
}
