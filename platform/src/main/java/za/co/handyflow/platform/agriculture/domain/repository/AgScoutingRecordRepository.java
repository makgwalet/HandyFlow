package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgScoutingRecord;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Mutable, cycle-scoped — mirrors AgHealthEventRepository's own shape. */
public interface AgScoutingRecordRepository extends JpaRepository<AgScoutingRecord, UUID> {

    @Query("SELECT r FROM AgScoutingRecord r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<AgScoutingRecord> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT r FROM AgScoutingRecord r WHERE r.tenantId = :tenantId AND r.cropCycleId = :cropCycleId ORDER BY r.scoutingDate DESC, r.id DESC")
    Page<AgScoutingRecord> findByCropCycle(TenantId tenantId, UUID cropCycleId, Pageable pageable);

    // Cross-tenant sweep for AgCropNotificationScheduler's daily 09:45 run —
    // every scouting record whose follow-up date has arrived and hasn't yet
    // been acknowledged, regardless of tenant. Mirrors
    // AgHealthEventRepository.findDueAcrossTenants() exactly.
    // Only for crop cycles still running, on a farm that is still live, and not told about in the last REPEAT_DAYS.
    // It reads followUpNotifiedOn (set by the sweep) and never changes followUpAcknowledged (set by a person).
    @Query("""
        SELECT r FROM AgScoutingRecord r
        WHERE r.followUpDate IS NOT NULL AND r.followUpDate <= :today AND r.followUpAcknowledged = false
        AND (r.followUpNotifiedOn IS NULL OR r.followUpNotifiedOn <= :repeatBefore)
        AND r.cropCycleId IN (SELECT c.id FROM AgCropCycle c WHERE c.deletedAt IS NULL
                              AND c.status NOT IN ('HARVESTED', 'FAILED', 'ABANDONED')
                              AND c.farmId IN (SELECT f.id FROM AgFarm f WHERE f.deletedAt IS NULL AND f.status = 'ACTIVE'))
        """)
    List<AgScoutingRecord> findFollowUpDueAcrossTenants(LocalDate today, LocalDate repeatBefore);

    // FIX (Agriculture GAP 5 — mobile gap report, "attention" endpoint):
    // farm-scoped equivalent of the sweep above, mirroring
    // AgHealthEventRepository.findDueForFarm()'s own reasoning exactly —
    // AgScoutingRecord has no farmId of its own, scoped via a subquery
    // through cropCycleId -> AgCropCycle.farmId.
    @Query("""
        SELECT r FROM AgScoutingRecord r
        WHERE r.tenantId = :tenantId
        AND r.followUpDate IS NOT NULL AND r.followUpDate <= :today AND r.followUpAcknowledged = false
        AND r.cropCycleId IN (SELECT c.id FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.farmId = :farmId AND c.deletedAt IS NULL AND c.status NOT IN ('HARVESTED', 'FAILED', 'ABANDONED'))
        ORDER BY r.followUpDate ASC
        """)
    List<AgScoutingRecord> findFollowUpDueForFarm(TenantId tenantId, UUID farmId, LocalDate today);

    // Findings still open that were rated HIGH severity, whether or not a follow-up date was set.
    @Query("""
        SELECT r FROM AgScoutingRecord r
        WHERE r.tenantId = :tenantId
        AND r.status = 'OPEN' AND r.severity = 'HIGH'
        AND r.cropCycleId IN (SELECT c.id FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.farmId = :farmId AND c.deletedAt IS NULL)
        ORDER BY r.scoutingDate ASC
        """)
    List<AgScoutingRecord> findOpenHighSeverityForFarm(TenantId tenantId, UUID farmId);

    // Trends: [scoutingDate, severity, cropCycleId].
    @Query("SELECT r.scoutingDate, r.severity, r.cropCycleId FROM AgScoutingRecord r WHERE r.tenantId = :tenantId AND r.scoutingDate >= :startDate AND r.scoutingDate <= :endDate")
    List<Object[]> findBetween(TenantId tenantId, LocalDate startDate, LocalDate endDate);
}
