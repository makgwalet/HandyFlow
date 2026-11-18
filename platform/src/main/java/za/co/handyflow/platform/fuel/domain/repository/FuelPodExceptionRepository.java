package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelPodException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelPodExceptionRepository extends JpaRepository<FuelPodException, UUID> {

    @Query("SELECT e FROM FuelPodException e WHERE e.tenantId = :tenantId AND e.deliveryId = :deliveryId ORDER BY e.createdAt DESC")
    List<FuelPodException> forDelivery(TenantId tenantId, UUID deliveryId);
}
