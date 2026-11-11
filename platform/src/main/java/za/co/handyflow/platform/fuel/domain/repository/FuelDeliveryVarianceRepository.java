package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryVariance;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelDeliveryVarianceRepository extends JpaRepository<FuelDeliveryVariance, UUID> {

    @Query("SELECT v FROM FuelDeliveryVariance v WHERE v.tenantId = :tenantId ORDER BY v.detectedAt DESC")
    List<FuelDeliveryVariance> findAllForTenant(TenantId tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM FuelDeliveryVariance v WHERE v.tenantId = :tenantId AND v.id = :id")
    Optional<FuelDeliveryVariance> findByIdForUpdate(TenantId tenantId, UUID id);
}
