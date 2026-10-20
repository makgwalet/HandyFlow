package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Battery;
import za.co.handyflow.platform.fleet.domain.model.BatteryStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatteryRepository extends JpaRepository<Battery, UUID> {

    @Query("SELECT b FROM Battery b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<Battery> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT b FROM Battery b WHERE b.tenantId = :tenantId ORDER BY b.serialNumber")
    Page<Battery> findAllForTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT b FROM Battery b WHERE b.tenantId = :tenantId AND b.status = :status ORDER BY b.serialNumber")
    Page<Battery> findByStatus(TenantId tenantId, BatteryStatus status, Pageable pageable);

    @Query("SELECT b FROM Battery b WHERE b.tenantId = :tenantId AND b.currentVehicleId = :vehicleId " +
            "AND b.status = za.co.handyflow.platform.fleet.domain.model.BatteryStatus.INSTALLED ORDER BY b.currentPosition")
    List<Battery> findInstalledByVehicle(TenantId tenantId, UUID vehicleId);

    @Query("SELECT b FROM Battery b WHERE b.tenantId = :tenantId AND b.currentVehicleId = :vehicleId " +
            "AND b.currentPosition = :position AND b.status = za.co.handyflow.platform.fleet.domain.model.BatteryStatus.INSTALLED")
    Optional<Battery> findInstalledAt(TenantId tenantId, UUID vehicleId, String position);

    @Query("SELECT COUNT(b) > 0 FROM Battery b WHERE b.tenantId = :tenantId AND LOWER(b.serialNumber) = LOWER(:serial) " +
            "AND b.id <> :excludeId")
    boolean existsOtherWithSerial(TenantId tenantId, String serial, UUID excludeId);

    /** Cross-tenant, for the daily warranty-expiry sweep only — a scheduled job runs once for the whole platform. */
    @Query("SELECT b FROM Battery b WHERE b.status = :status AND b.warrantyExpiry IS NOT NULL")
    List<Battery> findByStatusWithWarranty(BatteryStatus status);
}