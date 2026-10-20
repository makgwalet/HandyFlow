package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.VehicleFuelSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleFuelSettingsRepository extends JpaRepository<VehicleFuelSettings, UUID> {

    Optional<VehicleFuelSettings> findByTenantIdAndVehicleId(TenantId tenantId, UUID vehicleId);

    @Query("SELECT s FROM VehicleFuelSettings s WHERE s.tenantId = :tenantId")
    List<VehicleFuelSettings> findAllForTenant(TenantId tenantId);
}