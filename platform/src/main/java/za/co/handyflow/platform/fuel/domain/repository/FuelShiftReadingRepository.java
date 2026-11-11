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

    @Query("SELECT r FROM FuelShiftReading r WHERE r.tenantId = :tenantId AND r.open = true AND r.pumpId IN :pumpIds")
    List<FuelShiftReading> findOpenForPumps(TenantId tenantId, Collection<UUID> pumpIds);
}
