package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopBay;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTruckStopBayRepository extends JpaRepository<FuelTruckStopBay, UUID> {

    @Query("SELECT b FROM FuelTruckStopBay b WHERE b.tenantId = :tenantId ORDER BY b.name")
    List<FuelTruckStopBay> findAllForTenant(TenantId tenantId);

    @Query("SELECT b FROM FuelTruckStopBay b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<FuelTruckStopBay> findOwned(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(b) > 0 FROM FuelTruckStopBay b WHERE b.tenantId = :tenantId AND LOWER(b.name) = LOWER(:name)")
    boolean nameInUse(TenantId tenantId, String name);
}
