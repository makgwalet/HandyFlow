package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.RecoveryStatus;
import za.co.handyflow.platform.fleet.domain.model.VehicleTheftReport;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface VehicleTheftReportRepository extends JpaRepository<VehicleTheftReport, UUID> {

    @Query("SELECT r FROM VehicleTheftReport r WHERE r.tenantId = :tenantId AND r.vehicleId = :vehicleId " +
            "ORDER BY r.occurredAt DESC")
    List<VehicleTheftReport> findByVehicle(TenantId tenantId, UUID vehicleId);

    /** Unrecovered reports, newest first. The service uses the first; callers pass NOT_RECOVERED. */
    @Query("SELECT r FROM VehicleTheftReport r WHERE r.tenantId = :tenantId AND r.vehicleId = :vehicleId " +
            "AND r.recoveryStatus = :status ORDER BY r.occurredAt DESC")
    List<VehicleTheftReport> findByVehicleAndRecoveryStatus(TenantId tenantId, UUID vehicleId, RecoveryStatus status);
}