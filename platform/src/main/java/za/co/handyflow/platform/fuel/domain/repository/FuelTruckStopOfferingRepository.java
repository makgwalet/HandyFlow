package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopOffering;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTruckStopOfferingRepository extends JpaRepository<FuelTruckStopOffering, UUID> {
    @Query("SELECT o FROM FuelTruckStopOffering o WHERE o.tenantId = :tenantId ORDER BY o.kind, o.createdAt")
    List<FuelTruckStopOffering> findAllForTenant(TenantId tenantId);

    @Query("SELECT o FROM FuelTruckStopOffering o WHERE o.tenantId = :tenantId AND o.id = :id")
    Optional<FuelTruckStopOffering> findOwned(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(o) > 0 FROM FuelTruckStopOffering o WHERE o.tenantId = :tenantId AND o.bookingsServiceId = :serviceId")
    boolean serviceTaken(TenantId tenantId, UUID serviceId);
}
