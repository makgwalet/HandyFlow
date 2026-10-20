package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.fleet.domain.model.VehicleInsurance;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface VehicleInsuranceRepository extends JpaRepository<VehicleInsurance, UUID> {

    Optional<VehicleInsurance> findByTenantIdAndVehicleId(TenantId tenantId, UUID vehicleId);
}