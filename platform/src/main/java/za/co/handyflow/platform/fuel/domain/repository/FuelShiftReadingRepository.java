package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftReading;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FuelShiftReadingRepository extends JpaRepository<FuelShiftReading, UUID> {
    @Query("SELECT r FROM FuelShiftReading r WHERE r.tenantId = :tenantId AND r.shiftId = :shiftId ORDER BY r.pumpId")
    List<FuelShiftReading> findByShift(TenantId tenantId, UUID shiftId);

    @Query("SELECT r FROM FuelShiftReading r WHERE r.tenantId = :tenantId AND r.shiftId IN :shiftIds")
    List<FuelShiftReading> findByShifts(TenantId tenantId, Collection<UUID> shiftIds);

    /** Litres the pump meters recorded for a tank on shifts closed in the window (reversed shifts excluded). */
    @Query("SELECT COALESCE(SUM(r.litresSold), 0) FROM FuelShiftReading r, FuelShift s WHERE r.shiftId = s.id AND r.tenantId = :tenantId AND s.tenantId = :tenantId AND r.tankId = :tankId AND s.status = 'CLOSED' AND s.closedAt > :after AND s.closedAt <= :upTo")
    java.math.BigDecimal sumMeterLitresForTank(TenantId tenantId, UUID tankId, java.time.Instant after, java.time.Instant upTo);

    @Query("SELECT r FROM FuelShiftReading r WHERE r.tenantId = :tenantId AND r.open = true AND r.pumpId IN :pumpIds")
    List<FuelShiftReading> findOpenForPumps(TenantId tenantId, Collection<UUID> pumpIds);
}
