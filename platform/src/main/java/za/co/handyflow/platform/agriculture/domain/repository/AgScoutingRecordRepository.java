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

    @Query("SELECT r FROM AgScoutingRecord r WHERE r.tenantId = :tenantId AND r.cropCycleId = :cropCycleId ORDER BY r.scoutingDate DESC")
    Page<AgScoutingRecord> findByCropCycle(TenantId tenantId, UUID cropCycleId, Pageable pageable);

    // Cross-tenant sweep for AgCropNotificationScheduler's daily 09:45 run —
    // every scouting record whose follow-up date has arrived and hasn't yet
    // been acknowledged, regardless of tenant. Mirrors
    // AgHealthEventRepository.findDueAcrossTenants() exactly.
    @Query("SELECT r FROM AgScoutingRecord r WHERE r.followUpDate IS NOT NULL AND r.followUpDate <= :today AND r.followUpAcknowledged = false")
    List<AgScoutingRecord> findFollowUpDueAcrossTenants(LocalDate today);

    // FIX (Agriculture GAP 5 — mobile gap report, "attention" endpoint):
    // farm-scoped equivalent of the sweep above, mirroring
    // AgHealthEventRepository.findDueForFarm()'s own reasoning exactly —
    // AgScoutingRecord has no farmId of its own, scoped via a subquery
    // through cropCycleId -> AgCropCycle.farmId.
    @Query("""
        SELECT r FROM AgScoutingRecord r
        WHERE r.tenantId = :tenantId
        AND r.followUpDate IS NOT NULL AND r.followUpDate <= :today AND r.followUpAcknowledged = false
        AND r.cropCycleId IN (SELECT c.id FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.farmId = :farmId AND c.deletedAt IS NULL)
        ORDER BY r.followUpDate ASC
        """)
    List<AgScoutingRecord> findFollowUpDueForFarm(TenantId tenantId, UUID farmId, LocalDate today);
}
