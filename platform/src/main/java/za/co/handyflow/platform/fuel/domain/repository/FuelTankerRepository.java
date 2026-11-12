package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTanker;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTankerRepository extends JpaRepository<FuelTanker, UUID> {

    @Query("SELECT t FROM FuelTanker t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL ORDER BY t.registration")
    List<FuelTanker> findAllActive(TenantId tenantId);

    @Query("SELECT t FROM FuelTanker t WHERE t.tenantId = :tenantId AND t.id = :id AND t.deletedAt IS NULL")
    Optional<FuelTanker> findActiveById(TenantId tenantId, UUID id);

    /** True when another live tanker is already linked to this Fleet vehicle. */
    @Query("SELECT COUNT(t) > 0 FROM FuelTanker t WHERE t.tenantId = :tenantId AND t.fleetVehicleId = :vehicleId AND t.deletedAt IS NULL AND t.id <> :excludeId")
    boolean fleetVehicleTaken(TenantId tenantId, UUID vehicleId, UUID excludeId);

    @Query("SELECT COUNT(t) > 0 FROM FuelTanker t WHERE t.tenantId = :tenantId AND UPPER(t.registration) = UPPER(:registration) AND t.deletedAt IS NULL AND t.id <> :excludeId")
    boolean registrationTaken(TenantId tenantId, String registration, UUID excludeId);
}
