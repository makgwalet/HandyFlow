package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthDeployment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface EarthDeploymentRepository extends JpaRepository<EarthDeployment, UUID> {

    @Query("SELECT d FROM EarthDeployment d WHERE d.tenantId = :tenantId AND d.assetId = :assetId " +
            "ORDER BY d.deployedAt DESC")
    Page<EarthDeployment> findByAsset(TenantId tenantId, UUID assetId, Pageable pageable);

    // At most one of these should ever exist per asset at a time — deploy()
    // can only be called from AVAILABLE (see AssetStatus), so a second
    // deployment can't start while one is still open. Still written as a
    // list-then-take-first rather than assuming exactly one row, since
    // "should never happen" and "database guarantees it never happens" are
    // different claims — nothing stops a manual SQL edit from creating two.
    @Query("SELECT d FROM EarthDeployment d WHERE d.tenantId = :tenantId AND d.assetId = :assetId " +
            "AND d.returnedAt IS NULL ORDER BY d.deployedAt DESC")
    Optional<EarthDeployment> findOpenForAsset(TenantId tenantId, UUID assetId);
}
