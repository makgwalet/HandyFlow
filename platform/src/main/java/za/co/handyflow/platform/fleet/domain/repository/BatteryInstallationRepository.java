package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.BatteryInstallation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatteryInstallationRepository extends JpaRepository<BatteryInstallation, UUID> {

    @Query("SELECT i FROM BatteryInstallation i WHERE i.tenantId = :tenantId AND i.batteryId = :batteryId ORDER BY i.installedAt DESC")
    List<BatteryInstallation> findByBattery(TenantId tenantId, UUID batteryId);

    @Query("SELECT i FROM BatteryInstallation i WHERE i.tenantId = :tenantId AND i.batteryId = :batteryId AND i.removedAt IS NULL")
    Optional<BatteryInstallation> findOpenByBattery(TenantId tenantId, UUID batteryId);
}