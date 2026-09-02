package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgInputApplication;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Append-only, cycle-scoped — mirrors AgFeedRecordRepository's own shape. */
public interface AgInputApplicationRepository extends JpaRepository<AgInputApplication, UUID> {

    @Query("SELECT a FROM AgInputApplication a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<AgInputApplication> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT a FROM AgInputApplication a WHERE a.tenantId = :tenantId AND a.cropCycleId = :cropCycleId ORDER BY a.applicationDate DESC")
    Page<AgInputApplication> findByCropCycle(TenantId tenantId, UUID cropCycleId, Pageable pageable);

    // Backs AgCostReportingService — COALESCE so a cycle with zero
    // applications returns 0, not null, mirroring
    // VehicleServiceRepository.sumCostByVehicle().
    @Query("SELECT COALESCE(SUM(a.cost), 0) FROM AgInputApplication a WHERE a.tenantId = :tenantId AND a.cropCycleId = :cropCycleId")
    BigDecimal sumCostByCropCycle(TenantId tenantId, UUID cropCycleId);

    // Labor hours are reported as hours, not converted to a cost — this
    // module has no stored labor rate anywhere (see AgCostReportingService's
    // own Javadoc), so summing hours is as far as this repository can
    // responsibly go without inventing a rate.
    @Query("SELECT COALESCE(SUM(a.laborHours), 0) FROM AgInputApplication a WHERE a.tenantId = :tenantId AND a.cropCycleId = :cropCycleId")
    BigDecimal sumLaborHoursByCropCycle(TenantId tenantId, UUID cropCycleId);
}
