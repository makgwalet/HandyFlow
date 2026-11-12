package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelLoadReturn;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelLoadReturnRepository extends JpaRepository<FuelLoadReturn, UUID> {

    @Query("SELECT r FROM FuelLoadReturn r WHERE r.tenantId = :tenantId AND r.deliveryId = :deliveryId ORDER BY r.recordedAt ASC")
    List<FuelLoadReturn> findByDelivery(TenantId tenantId, UUID deliveryId);

    @Query("SELECT r FROM FuelLoadReturn r WHERE r.tenantId = :tenantId AND r.deliveryId IN :deliveryIds")
    List<FuelLoadReturn> findByDeliveries(TenantId tenantId, Collection<UUID> deliveryIds);

    @Query("SELECT r FROM FuelLoadReturn r WHERE r.tenantId = :tenantId AND r.deliveryId = :deliveryId AND r.clientRequestId = :clientRequestId")
    Optional<FuelLoadReturn> findByClientRequest(TenantId tenantId, UUID deliveryId, UUID clientRequestId);
}
