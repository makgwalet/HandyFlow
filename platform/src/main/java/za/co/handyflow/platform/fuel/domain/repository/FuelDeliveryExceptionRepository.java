package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelDeliveryExceptionRepository extends JpaRepository<FuelDeliveryException, UUID> {

    @Query("SELECT e FROM FuelDeliveryException e WHERE e.tenantId = :tenantId AND e.deliveryId = :deliveryId ORDER BY e.recordedAt ASC")
    List<FuelDeliveryException> findByDelivery(TenantId tenantId, UUID deliveryId);

    @Query("SELECT e FROM FuelDeliveryException e WHERE e.tenantId = :tenantId AND e.deliveryId = :deliveryId AND e.clientRequestId = :clientRequestId")
    Optional<FuelDeliveryException> findByClientRequest(TenantId tenantId, UUID deliveryId, UUID clientRequestId);
}
