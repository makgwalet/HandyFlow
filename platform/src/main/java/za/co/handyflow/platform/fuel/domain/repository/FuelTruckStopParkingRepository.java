package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopParking;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FuelTruckStopParkingRepository extends JpaRepository<FuelTruckStopParking, UUID> {

    @Query("SELECT p FROM FuelTruckStopParking p WHERE p.tenantId = :tenantId")
    Optional<FuelTruckStopParking> findForTenant(TenantId tenantId);
}
