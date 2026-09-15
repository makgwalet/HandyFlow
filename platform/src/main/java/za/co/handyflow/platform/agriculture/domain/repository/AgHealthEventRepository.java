package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgHealthEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgHealthEventRepository extends JpaRepository<AgHealthEvent, UUID> {

    @Query("SELECT e FROM AgHealthEvent e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<AgHealthEvent> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT e FROM AgHealthEvent e WHERE e.tenantId = :tenantId AND e.animalId = :animalId ORDER BY e.eventDate DESC")
    Page<AgHealthEvent> findByAnimal(TenantId tenantId, UUID animalId, Pageable pageable);

    @Query("SELECT e FROM AgHealthEvent e WHERE e.tenantId = :tenantId AND e.groupId = :groupId ORDER BY e.eventDate DESC")
    Page<AgHealthEvent> findByGroup(TenantId tenantId, UUID groupId, Pageable pageable);

    // Cross-tenant sweep for AgNotificationScheduler's daily 09:30 run — every
    // health event whose next-due reminder has arrived and hasn't yet been
    // acknowledged, regardless of tenant. See AgHealthEvent's own Javadoc for
    // why this is a once-per-due-date alert rather than a re-alerting one.
    @Query("SELECT e FROM AgHealthEvent e WHERE e.nextDueDate IS NOT NULL AND e.nextDueDate <= :today AND e.reminderAcknowledged = false")
    List<AgHealthEvent> findDueAcrossTenants(LocalDate today);

    // FIX (Agriculture GAP 5 — mobile gap report, "attention" endpoint):
    // the farm-scoped read equivalent of the sweep above, backing the
    // real GET /farms/{id}/attention endpoint the gap report asked for
    // instead of the client-side "fetch every animal/group's health
    // events and filter locally" workaround it documented. AgHealthEvent
    // has no farmId of its own — scoped via a subquery through whichever
    // of animalId/groupId is set, matching how every animal-or-group
    // entity in this module is queried elsewhere.
    @Query("""
        SELECT e FROM AgHealthEvent e
        WHERE e.tenantId = :tenantId
        AND e.nextDueDate IS NOT NULL AND e.nextDueDate <= :today AND e.reminderAcknowledged = false
        AND (
            e.animalId IN (SELECT a.id FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.deletedAt IS NULL)
            OR e.groupId IN (SELECT g.id FROM AgGroup g WHERE g.tenantId = :tenantId AND g.farmId = :farmId AND g.deletedAt IS NULL)
        )
        ORDER BY e.nextDueDate ASC
        """)
    List<AgHealthEvent> findDueForFarm(TenantId tenantId, UUID farmId, LocalDate today);

    // Backs AgCostReportingService — COALESCE so an animal/group with zero
    // health events returns 0, not null, keeping the arithmetic in the cost
    // service simple, mirroring VehicleServiceRepository.sumCostByVehicle().
    @Query("SELECT COALESCE(SUM(e.cost), 0) FROM AgHealthEvent e WHERE e.tenantId = :tenantId AND e.animalId = :animalId")
    BigDecimal sumCostByAnimal(TenantId tenantId, UUID animalId);

    @Query("SELECT COALESCE(SUM(e.cost), 0) FROM AgHealthEvent e WHERE e.tenantId = :tenantId AND e.groupId = :groupId")
    BigDecimal sumCostByGroup(TenantId tenantId, UUID groupId);
}
