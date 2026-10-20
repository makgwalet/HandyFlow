// fleet/domain/repository/VehicleRepository.java

package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    @Query("SELECT v FROM Vehicle v WHERE v.tenantId = :tenantId AND v.deletedAt IS NULL ORDER BY v.make, v.model")
    Page<Vehicle> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT v FROM Vehicle v WHERE v.tenantId = :tenantId AND v.id = :id AND v.deletedAt IS NULL")
    Optional<Vehicle> findActiveById(TenantId tenantId, UUID id);

    // FIX: status is now the VehicleStatus enum (was a raw String) — see
    // VehicleStatus.java. Same fix as earthmoving's EarthAssetRepository.
    @Query("SELECT v FROM Vehicle v WHERE v.tenantId = :tenantId AND v.status = :status AND v.deletedAt IS NULL ORDER BY v.registration")
    Page<Vehicle> findByStatus(TenantId tenantId, VehicleStatus status, Pageable pageable);

    boolean existsByTenantIdAndRegistrationAndDeletedAtIsNull(TenantId tenantId, String registration);

    @Query("SELECT v FROM Vehicle v WHERE v.tenantId = :tenantId AND v.vehicleType = :vehicleType AND v.deletedAt IS NULL ORDER BY v.registration")
    Page<Vehicle> findByType(TenantId tenantId, String vehicleType, Pageable pageable);

    @Query("SELECT v FROM Vehicle v WHERE v.tenantId = :tenantId AND v.status = :status AND v.vehicleType = :vehicleType AND v.deletedAt IS NULL ORDER BY v.registration")
    Page<Vehicle> findByStatusAndType(TenantId tenantId, VehicleStatus status, String vehicleType, Pageable pageable);

    // NEW: cross-tenant, used only by FleetNotificationScheduler's daily
    // compliance-expiry sweep — deliberately has no :tenantId filter since a
    // scheduled job runs once for the whole platform, not per-request like
    // everything else in this repository.
    @Query("SELECT v FROM Vehicle v WHERE v.deletedAt IS NULL AND v.status <> :excludedStatus")
    List<Vehicle> findAllActiveExcludingStatus(VehicleStatus excludedStatus);

    // ── NEW: uniqueness checks that exclude the vehicle being edited ─────────
    // On create, callers pass a fresh random UUID as excludeId (matches nothing),
    // which avoids a nullable-UUID parameter, a known source of "could not
    // determine data type" errors on PostgreSQL.

    @Query("SELECT COUNT(v) > 0 FROM Vehicle v WHERE v.tenantId = :tenantId AND v.registration = :registration " +
            "AND v.id <> :excludeId AND v.deletedAt IS NULL")
    boolean existsOtherWithRegistration(TenantId tenantId, String registration, UUID excludeId);

    @Query("SELECT COUNT(v) > 0 FROM Vehicle v WHERE v.tenantId = :tenantId AND v.vin = :vin " +
            "AND v.id <> :excludeId AND v.deletedAt IS NULL")
    boolean existsOtherWithVin(TenantId tenantId, String vin, UUID excludeId);

    @Query("SELECT COUNT(v) > 0 FROM Vehicle v WHERE v.tenantId = :tenantId AND v.engineNumber = :engineNumber " +
            "AND v.id <> :excludeId AND v.deletedAt IS NULL")
    boolean existsOtherWithEngineNumber(TenantId tenantId, String engineNumber, UUID excludeId);
}