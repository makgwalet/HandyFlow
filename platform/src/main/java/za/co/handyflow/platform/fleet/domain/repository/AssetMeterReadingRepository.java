package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.AssetMeterReading;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AssetMeterReadingRepository extends JpaRepository<AssetMeterReading, UUID> {

    @Query("SELECT r FROM AssetMeterReading r WHERE r.tenantId = :tenantId AND r.vehicleId = :vehicleId ORDER BY r.recordedAt DESC")
    Page<AssetMeterReading> findByVehicle(TenantId tenantId, UUID vehicleId, Pageable pageable);

    /** Readings inside [from, to], oldest first. */
    @Query("SELECT r FROM AssetMeterReading r WHERE r.tenantId = :tenantId AND r.vehicleId = :vehicleId " +
            "AND r.recordedAt >= :from AND r.recordedAt < :to ORDER BY r.recordedAt ASC")
    List<AssetMeterReading> findBetween(TenantId tenantId, UUID vehicleId, Instant from, Instant to);

    /** The most recent readings before {@code before}, newest first; pass a one-row Pageable for just the latest. */
    @Query("SELECT r FROM AssetMeterReading r WHERE r.tenantId = :tenantId AND r.vehicleId = :vehicleId " +
            "AND r.recordedAt < :before ORDER BY r.recordedAt DESC")
    List<AssetMeterReading> findBefore(TenantId tenantId, UUID vehicleId, Instant before, Pageable pageable);
}