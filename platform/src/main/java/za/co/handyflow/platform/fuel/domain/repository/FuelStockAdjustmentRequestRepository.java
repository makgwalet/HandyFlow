package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelStockAdjustmentRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelStockAdjustmentRequestRepository extends JpaRepository<FuelStockAdjustmentRequest, UUID> {

    @Query("SELECT r FROM FuelStockAdjustmentRequest r WHERE r.tenantId = :tenantId ORDER BY r.requestedAt DESC")
    List<FuelStockAdjustmentRequest> findAllForTenant(TenantId tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FuelStockAdjustmentRequest r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<FuelStockAdjustmentRequest> findByIdForUpdate(TenantId tenantId, UUID id);
}
