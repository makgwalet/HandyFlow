package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvVenue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvVenueRepository extends JpaRepository<TrainProvVenue, UUID> {

    @Query("""
        SELECT v FROM TrainProvVenue v WHERE v.tenantId = :#{#tenantId.value}
        AND (CAST(:siteId AS uuid) IS NULL OR v.siteId = :siteId)
        AND (:status IS NULL OR v.status = :status)
        ORDER BY v.status, v.name
        """)
    List<TrainProvVenue> search(TenantId tenantId, UUID siteId, String status);

    @Query("SELECT v FROM TrainProvVenue v WHERE v.tenantId = :#{#tenantId.value} AND v.id = :id")
    Optional<TrainProvVenue> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(v) > 0 FROM TrainProvVenue v WHERE v.tenantId = :#{#tenantId.value} AND v.siteId = :siteId AND v.name = :name")
    boolean nameTaken(TenantId tenantId, UUID siteId, String name);
}
