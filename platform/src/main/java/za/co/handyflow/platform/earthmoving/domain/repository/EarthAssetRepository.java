// earthmoving/domain/repository/EarthAssetRepository.java

package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface EarthAssetRepository extends JpaRepository<EarthAsset, UUID> {

    @Query("SELECT a FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.deletedAt IS NULL ORDER BY a.fleetNumber, a.name")
    Page<EarthAsset> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT a FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.id = :id AND a.deletedAt IS NULL")
    Optional<EarthAsset> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT a FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.status = :status AND a.deletedAt IS NULL ORDER BY a.fleetNumber, a.name")
    Page<EarthAsset> findByStatus(TenantId tenantId, String status, Pageable pageable);

    @Query("SELECT a FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.assetType = :assetType AND a.deletedAt IS NULL ORDER BY a.fleetNumber, a.name")
    Page<EarthAsset> findByType(TenantId tenantId, String assetType, Pageable pageable);

    @Query("SELECT a FROM EarthAsset a WHERE a.tenantId = :tenantId AND a.status = :status AND a.assetType = :assetType AND a.deletedAt IS NULL ORDER BY a.fleetNumber, a.name")
    Page<EarthAsset> findByStatusAndType(TenantId tenantId, String status, String assetType, Pageable pageable);
}
