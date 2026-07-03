// earthmoving/domain/repository/EarthAssetRepository.java

package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.AssetStatus;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface EarthAssetRepository extends JpaRepository<EarthAsset, UUID> {

    @Query("SELECT a FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.deletedAt IS NULL ORDER BY a.fleetNumber, a.name")
    Page<EarthAsset> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT a FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.id = :id AND a.deletedAt IS NULL")
    Optional<EarthAsset> findActiveById(TenantId tenantId, UUID id);

    // FIX: status is now the AssetStatus enum (was a raw String) — see
    // AssetStatus.java for why. Spring Data binds the enum via
    // @Enumerated(EnumType.STRING) automatically; the service layer parses
    // the incoming request string into AssetStatus once, at the boundary,
    // instead of passing raw strings all the way down into JPQL.
    @Query("SELECT a FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.status = :status AND a.deletedAt IS NULL ORDER BY a.fleetNumber, a.name")
    Page<EarthAsset> findByStatus(TenantId tenantId, AssetStatus status, Pageable pageable);

    @Query("SELECT a FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.assetType = :assetType AND a.deletedAt IS NULL ORDER BY a.fleetNumber, a.name")
    Page<EarthAsset> findByType(TenantId tenantId, String assetType, Pageable pageable);

    @Query("SELECT a FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.status = :status AND a.assetType = :assetType AND a.deletedAt IS NULL ORDER BY a.fleetNumber, a.name")
    Page<EarthAsset> findByStatusAndType(TenantId tenantId, AssetStatus status, String assetType, Pageable pageable);

    // Backs the pre-insert uniqueness check in EarthAssetService.createAsset()
    // — see uq_earthmoving_assets_tenant_fleet_number for the DB-level
    // guarantee this is a friendly, fast-failing check in front of.
    @Query("SELECT COUNT(a) > 0 FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.fleetNumber = :fleetNumber AND a.deletedAt IS NULL")
    boolean existsActiveByFleetNumber(TenantId tenantId, String fleetNumber);
}