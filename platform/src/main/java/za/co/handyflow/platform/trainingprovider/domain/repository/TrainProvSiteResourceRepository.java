package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSiteResource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvSiteResourceRepository extends JpaRepository<TrainProvSiteResource, UUID> {

    @Query("""
        SELECT r FROM TrainProvSiteResource r WHERE r.tenantId = :#{#tenantId.value}
        AND (CAST(:siteId AS uuid) IS NULL OR r.siteId = :siteId)
        AND (:status IS NULL OR r.status = :status)
        AND (:type IS NULL OR r.resourceType = :type)
        ORDER BY r.status, r.name
        """)
    List<TrainProvSiteResource> search(TenantId tenantId, UUID siteId, String status, String type);

    @Query("SELECT r FROM TrainProvSiteResource r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<TrainProvSiteResource> findForTenant(TenantId tenantId, UUID id);

    /** True when another resource that is not retired already uses this identifier (serial number, asset tag, registration). */
    @Query("""
        SELECT COUNT(r) > 0 FROM TrainProvSiteResource r WHERE r.tenantId = :#{#tenantId.value} AND r.status <> 'RETIRED'
        AND LOWER(r.identifier) = LOWER(:identifier) AND (CAST(:excludeId AS uuid) IS NULL OR r.id <> :excludeId)
        """)
    boolean identifierTaken(TenantId tenantId, String identifier, UUID excludeId);
}
