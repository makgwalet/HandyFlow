package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftAdjustment;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelShiftAdjustmentRepository extends JpaRepository<FuelShiftAdjustment, UUID> {

    @Query("SELECT a FROM FuelShiftAdjustment a WHERE a.tenantId = :tenantId AND a.shiftId = :shiftId ORDER BY a.recordedAt")
    List<FuelShiftAdjustment> findByShift(TenantId tenantId, UUID shiftId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FuelShiftAdjustment a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<FuelShiftAdjustment> findByIdForUpdate(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(a) FROM FuelShiftAdjustment a WHERE a.tenantId = :tenantId AND a.shiftId = :shiftId AND a.status = 'PENDING'")
    long countPending(TenantId tenantId, UUID shiftId);

    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM FuelShiftAdjustment a WHERE a.tenantId = :tenantId AND a.shiftId = :shiftId AND a.status = 'APPROVED'")
    BigDecimal sumApproved(TenantId tenantId, UUID shiftId);
}
