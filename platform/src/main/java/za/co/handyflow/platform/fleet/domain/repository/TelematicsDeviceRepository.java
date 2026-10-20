package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.TelematicsDevice;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TelematicsDeviceRepository extends JpaRepository<TelematicsDevice, UUID> {

    @Query("SELECT d FROM TelematicsDevice d WHERE d.tenantId = :tenantId ORDER BY d.externalId")
    List<TelematicsDevice> findAllForTenant(TenantId tenantId);

    @Query("SELECT d FROM TelematicsDevice d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<TelematicsDevice> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(d) > 0 FROM TelematicsDevice d WHERE d.tenantId = :tenantId " +
            "AND LOWER(d.externalId) = LOWER(:externalId) AND d.id <> :excludeId")
    boolean existsOtherWithExternalId(TenantId tenantId, String externalId, UUID excludeId);

    /** Ids of vehicles that have at least one active device — i.e. whose behaviour we can actually observe. */
    @Query("SELECT DISTINCT d.vehicleId FROM TelematicsDevice d WHERE d.tenantId = :tenantId AND d.active = true")
    List<UUID> findTrackedVehicleIds(TenantId tenantId);
}