package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Driver;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    @Query("SELECT d FROM Driver d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL ORDER BY d.firstName, d.lastName")
    Page<Driver> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT d FROM Driver d WHERE d.tenantId = :tenantId AND d.id = :id AND d.deletedAt IS NULL")
    Optional<Driver> findActiveById(TenantId tenantId, UUID id);

    // NEW: cross-tenant, backs FleetNotificationScheduler's driver
    // compliance sweep — same reasoning as
    // VehicleRepository.findAllActiveExcludingStatus.
    @Query("SELECT d FROM Driver d WHERE d.deletedAt IS NULL AND d.status = 'ACTIVE'")
    List<Driver> findAllActiveAcrossTenants();
}
