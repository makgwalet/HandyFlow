package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryEvidence;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelDeliveryEvidenceRepository extends JpaRepository<FuelDeliveryEvidence, UUID> {

    @Query("SELECT e FROM FuelDeliveryEvidence e WHERE e.tenantId = :tenantId AND e.deliveryId = :deliveryId ORDER BY e.capturedAt ASC")
    List<FuelDeliveryEvidence> findByDelivery(TenantId tenantId, UUID deliveryId);

    @Query("SELECT e FROM FuelDeliveryEvidence e WHERE e.tenantId = :tenantId AND e.deliveryId = :deliveryId AND e.clientRequestId = :clientRequestId")
    Optional<FuelDeliveryEvidence> findByClientRequest(TenantId tenantId, UUID deliveryId, UUID clientRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM FuelDeliveryEvidence e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<FuelDeliveryEvidence> findByIdForUpdate(TenantId tenantId, UUID id);
}
