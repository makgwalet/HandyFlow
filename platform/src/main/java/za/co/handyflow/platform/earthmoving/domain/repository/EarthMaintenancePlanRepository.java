package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthMaintenancePlan;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthMaintenancePlanRepository extends JpaRepository<EarthMaintenancePlan, UUID> {

    @Query("SELECT p FROM EarthMaintenancePlan p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<EarthMaintenancePlan> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT p FROM EarthMaintenancePlan p WHERE p.tenantId = :tenantId ORDER BY p.name")
    List<EarthMaintenancePlan> findAllForTenant(TenantId tenantId);

    @Query("SELECT p FROM EarthMaintenancePlan p WHERE p.tenantId = :tenantId AND p.assetId = :assetId ORDER BY p.name")
    List<EarthMaintenancePlan> findForAsset(TenantId tenantId, UUID assetId);

    @Query("SELECT p FROM EarthMaintenancePlan p WHERE p.tenantId = :tenantId AND p.active = true")
    List<EarthMaintenancePlan> findActive(TenantId tenantId);

    @Query("SELECT DISTINCT p.tenantId.value FROM EarthMaintenancePlan p WHERE p.active = true")
    List<UUID> findTenantIdsWithActivePlans();
}
