package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseRequisition;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelPurchaseRequisitionRepository extends JpaRepository<FuelPurchaseRequisition, UUID> {

    @Query("SELECT r FROM FuelPurchaseRequisition r WHERE r.tenantId = :tenantId ORDER BY r.createdAt DESC")
    List<FuelPurchaseRequisition> findAllForTenant(TenantId tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FuelPurchaseRequisition r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<FuelPurchaseRequisition> findByIdForUpdate(TenantId tenantId, UUID id);
}
