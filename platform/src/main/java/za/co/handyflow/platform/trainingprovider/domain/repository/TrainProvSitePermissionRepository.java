package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSitePermission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvSitePermissionRepository extends JpaRepository<TrainProvSitePermission, UUID> {

    @Query("SELECT p FROM TrainProvSitePermission p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<TrainProvSitePermission> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT p FROM TrainProvSitePermission p WHERE p.tenantId = :#{#tenantId.value}
        AND (CAST(:siteId AS uuid) IS NULL OR p.siteId = :siteId)
        AND (CAST(:practitionerId AS uuid) IS NULL OR p.practitionerId = :practitionerId)
        AND (:status IS NULL OR p.status = :status)
        ORDER BY p.status, p.validFrom DESC
        """)
    List<TrainProvSitePermission> search(TenantId tenantId, UUID siteId, UUID practitionerId, String status);

    @Query("""
        SELECT p FROM TrainProvSitePermission p WHERE p.tenantId = :#{#tenantId.value} AND p.practitionerId = :practitionerId
        AND p.siteId = :siteId AND p.role = :role AND p.status = 'ACTIVE'
        """)
    List<TrainProvSitePermission> liveFor(TenantId tenantId, UUID practitionerId, UUID siteId, String role);

    @Query("""
        SELECT COUNT(p) > 0 FROM TrainProvSitePermission p WHERE p.tenantId = :#{#tenantId.value} AND p.practitionerId = :practitionerId
        AND p.siteId = :siteId AND p.role = :role AND p.status = 'ACTIVE'
        AND ((CAST(:programmeId AS uuid) IS NULL AND p.programmeId IS NULL) OR p.programmeId = :programmeId)
        """)
    boolean liveExists(TenantId tenantId, UUID practitionerId, UUID siteId, String role, UUID programmeId);
}
