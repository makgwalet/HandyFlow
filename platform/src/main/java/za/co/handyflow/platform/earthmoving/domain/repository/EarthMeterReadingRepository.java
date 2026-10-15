package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthMeterReading;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface EarthMeterReadingRepository extends JpaRepository<EarthMeterReading, UUID> {

    /** Newest first. */
    @Query("SELECT r FROM EarthMeterReading r WHERE r.tenantId = :tenantId AND r.assetId = :assetId ORDER BY r.recordedAt DESC")
    List<EarthMeterReading> findByAsset(TenantId tenantId, UUID assetId, Pageable pageable);

    /** The newest reading recorded before a moment, newest first (take the first). */
    @Query("SELECT r FROM EarthMeterReading r WHERE r.tenantId = :tenantId AND r.assetId = :assetId AND r.recordedAt < :before ORDER BY r.recordedAt DESC")
    List<EarthMeterReading> findBefore(TenantId tenantId, UUID assetId, java.time.Instant before, Pageable pageable);
}
