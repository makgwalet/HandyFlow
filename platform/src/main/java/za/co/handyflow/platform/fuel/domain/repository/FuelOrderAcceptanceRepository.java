package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelOrderAcceptance;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FuelOrderAcceptanceRepository extends JpaRepository<FuelOrderAcceptance, UUID> {
    @Query("SELECT a FROM FuelOrderAcceptance a WHERE a.tenantId = :tenantId AND a.orderId = :orderId")
    Optional<FuelOrderAcceptance> findByOrder(TenantId tenantId, UUID orderId);
}
