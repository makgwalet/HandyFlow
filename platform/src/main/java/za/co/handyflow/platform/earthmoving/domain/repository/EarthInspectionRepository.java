package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthInspection;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface EarthInspectionRepository extends JpaRepository<EarthInspection, UUID> {

    /** Newest first; assetId null means every machine. */
    @Query("SELECT i FROM EarthInspection i WHERE i.tenantId = :tenantId AND (:assetId IS NULL OR i.assetId = :assetId) ORDER BY i.performedAt DESC")
    List<EarthInspection> findRecent(TenantId tenantId, UUID assetId, Pageable pageable);

    /** The most recent inspection of each machine. */
    @Query("SELECT i FROM EarthInspection i WHERE i.tenantId = :tenantId AND i.performedAt = "
            + "(SELECT MAX(j.performedAt) FROM EarthInspection j WHERE j.tenantId = :tenantId AND j.assetId = i.assetId)")
    List<EarthInspection> findLatestPerAsset(TenantId tenantId);
}
