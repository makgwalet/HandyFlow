package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthTelematicsDevice;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthTelematicsDeviceRepository extends JpaRepository<EarthTelematicsDevice, UUID> {

    @Query("SELECT d FROM EarthTelematicsDevice d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<EarthTelematicsDevice> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT d FROM EarthTelematicsDevice d WHERE d.tenantId = :tenantId ORDER BY d.mappedAt DESC")
    List<EarthTelematicsDevice> findAllForTenant(TenantId tenantId);

    @Query("SELECT d FROM EarthTelematicsDevice d WHERE d.tenantId = :tenantId AND d.active = true ORDER BY d.providerCode, d.externalId")
    List<EarthTelematicsDevice> findActive(TenantId tenantId);

    @Query("SELECT d FROM EarthTelematicsDevice d WHERE d.tenantId = :tenantId AND d.assetId = :assetId ORDER BY d.mappedAt DESC")
    List<EarthTelematicsDevice> findByAsset(TenantId tenantId, UUID assetId);

    @Query("SELECT d FROM EarthTelematicsDevice d WHERE d.tenantId = :tenantId AND d.providerCode = :providerCode AND d.externalId = :externalId AND d.active = true")
    Optional<EarthTelematicsDevice> findActiveDevice(TenantId tenantId, String providerCode, String externalId);

    @Query("SELECT d FROM EarthTelematicsDevice d WHERE d.tenantId = :tenantId AND d.assetId = :assetId AND d.providerCode = :providerCode AND d.active = true")
    Optional<EarthTelematicsDevice> findActiveForAsset(TenantId tenantId, UUID assetId, String providerCode);
}
