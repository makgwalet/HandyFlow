package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.VehicleDisposal;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface VehicleDisposalRepository extends JpaRepository<VehicleDisposal, UUID> {

    @Query("SELECT d FROM VehicleDisposal d WHERE d.tenantId = :tenantId AND d.vehicleId = :vehicleId")
    List<VehicleDisposal> findByVehicle(TenantId tenantId, UUID vehicleId);
}