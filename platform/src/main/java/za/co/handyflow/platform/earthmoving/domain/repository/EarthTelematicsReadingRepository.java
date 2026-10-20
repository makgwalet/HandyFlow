package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthTelematicsReading;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EarthTelematicsReadingRepository extends JpaRepository<EarthTelematicsReading, UUID> {

    /** Newest first by when the reading was taken. */
    @Query("SELECT r FROM EarthTelematicsReading r WHERE r.tenantId = :tenantId AND r.assetId = :assetId ORDER BY r.recordedAt DESC")
    List<EarthTelematicsReading> findForAsset(TenantId tenantId, UUID assetId, Pageable pageable);

    /** The newest reading with a position, of one source, for each machine. Ties are possible; the caller keeps one per machine. */
    @Query("SELECT r FROM EarthTelematicsReading r WHERE r.tenantId = :tenantId AND r.source = :source AND r.latitude IS NOT NULL AND r.recordedAt = " +
            "(SELECT MAX(x.recordedAt) FROM EarthTelematicsReading x WHERE x.tenantId = :tenantId AND x.assetId = r.assetId AND x.source = :source AND x.latitude IS NOT NULL)")
    List<EarthTelematicsReading> findLatestPositionBySource(TenantId tenantId, String source);

    /** The newest reading with a meter value, of one source, for each machine. */
    @Query("SELECT r FROM EarthTelematicsReading r WHERE r.tenantId = :tenantId AND r.source = :source AND r.meterValue IS NOT NULL AND r.recordedAt = " +
            "(SELECT MAX(x.recordedAt) FROM EarthTelematicsReading x WHERE x.tenantId = :tenantId AND x.assetId = r.assetId AND x.source = :source AND x.meterValue IS NOT NULL)")
    List<EarthTelematicsReading> findLatestMeterBySource(TenantId tenantId, String source);

    @Query("SELECT COUNT(r) FROM EarthTelematicsReading r WHERE r.deviceId = :deviceId AND r.recordedAt = :recordedAt")
    long countDuplicate(UUID deviceId, Instant recordedAt);
}
