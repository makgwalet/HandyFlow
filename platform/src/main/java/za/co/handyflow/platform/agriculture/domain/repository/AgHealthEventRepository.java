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

    @Query("SELECT e FROM AgHealthEvent e WHERE e.tenantId = :tenantId AND e.animalId = :animalId ORDER BY e.eventDate DESC, e.id DESC")
    Page<AgHealthEvent> findByAnimal(TenantId tenantId, UUID animalId, Pageable pageable);

    @Query("SELECT e FROM AgHealthEvent e WHERE e.tenantId = :tenantId AND e.groupId = :groupId ORDER BY e.eventDate DESC, e.id DESC")
    Page<AgHealthEvent> findByGroup(TenantId tenantId, UUID groupId, Pageable pageable);

    // Cross-tenant sweep for AgNotificationScheduler's daily 09:30 run — every
    // health event whose next-due reminder has arrived and hasn't yet been
    // acknowledged, regardless of tenant. See AgHealthEvent's own Javadoc for
    // why the sweep once alerted only once per due date (it now repeats weekly; see AgReminderRules).
    // Only for animals and groups still on the farm, on a farm that is still live, and not told about in the last REPEAT_DAYS.
    // It reads reminderNotifiedOn (set by the sweep) and never changes reminderAcknowledged (set by a person).
    @Query("""
        SELECT e FROM AgHealthEvent e
        WHERE e.nextDueDate IS NOT NULL AND e.nextDueDate <= :today AND e.reminderAcknowledged = false
        AND (e.reminderNotifiedOn IS NULL OR e.reminderNotifiedOn <= :repeatBefore)
        AND (
            e.animalId IN (SELECT a.id FROM AgAnimal a WHERE a.deletedAt IS NULL AND a.status = 'ACTIVE'
                           AND a.farmId IN (SELECT f.id FROM AgFarm f WHERE f.deletedAt IS NULL AND f.status = 'ACTIVE'))
            OR e.groupId IN (SELECT g.id FROM AgGroup g WHERE g.deletedAt IS NULL AND g.status = 'ACTIVE'
                             AND g.farmId IN (SELECT f.id FROM AgFarm f WHERE f.deletedAt IS NULL AND f.status = 'ACTIVE'))
        )
        """)
    List<AgHealthEvent> findDueAcrossTenants(LocalDate today, LocalDate repeatBefore);

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
            e.animalId IN (SELECT a.id FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.deletedAt IS NULL AND a.status = 'ACTIVE')
            OR e.groupId IN (SELECT g.id FROM AgGroup g WHERE g.tenantId = :tenantId AND g.farmId = :farmId AND g.deletedAt IS NULL AND g.status = 'ACTIVE')
        )
        ORDER BY e.nextDueDate ASC
        """)
    List<AgHealthEvent> findDueForFarm(TenantId tenantId, UUID farmId, LocalDate today);

    // Treatments that can hold an animal or group: those with a withdrawal period, given on or before asOf (and not before
    // since, which bounds the read). Whether one is still holding, and whether it is only scheduled, is decided by AgWithdrawalRules.
    @Query("""
        SELECT e FROM AgHealthEvent e
        WHERE e.tenantId = :tenantId AND e.animalId = :animalId
        AND e.withdrawalPeriodDays IS NOT NULL AND e.withdrawalPeriodDays > 0
        AND e.eventDate >= :since AND e.eventDate <= :asOf
        """)
    List<AgHealthEvent> findWithdrawalTreatmentsForAnimal(TenantId tenantId, UUID animalId, LocalDate since, LocalDate asOf);

    @Query("""
        SELECT e FROM AgHealthEvent e
        WHERE e.tenantId = :tenantId AND e.groupId = :groupId
        AND e.withdrawalPeriodDays IS NOT NULL AND e.withdrawalPeriodDays > 0
        AND e.eventDate >= :since AND e.eventDate <= :asOf
        """)
    List<AgHealthEvent> findWithdrawalTreatmentsForGroup(TenantId tenantId, UUID groupId, LocalDate since, LocalDate asOf);

    // Treatments with a milk or egg withdrawal period, given on or before asOf. Which of the two holds, and whether it is
    // still running, is decided by AgWithdrawalRules.
    @Query("""
        SELECT e FROM AgHealthEvent e
        WHERE e.tenantId = :tenantId AND e.animalId = :animalId
        AND ((e.milkWithdrawalDays IS NOT NULL AND e.milkWithdrawalDays > 0) OR (e.eggWithdrawalDays IS NOT NULL AND e.eggWithdrawalDays > 0))
        AND e.eventDate >= :since AND e.eventDate <= :asOf
        """)
    List<AgHealthEvent> findProduceTreatmentsForAnimal(TenantId tenantId, UUID animalId, LocalDate since, LocalDate asOf);

    @Query("""
        SELECT e FROM AgHealthEvent e
        WHERE e.tenantId = :tenantId AND e.groupId = :groupId
        AND ((e.milkWithdrawalDays IS NOT NULL AND e.milkWithdrawalDays > 0) OR (e.eggWithdrawalDays IS NOT NULL AND e.eggWithdrawalDays > 0))
        AND e.eventDate >= :since AND e.eventDate <= :asOf
        """)
    List<AgHealthEvent> findProduceTreatmentsForGroup(TenantId tenantId, UUID groupId, LocalDate since, LocalDate asOf);

    // The treatment register: every treatment on the farm's animals and groups in the period, oldest first. Deleted animals and groups
    // are included, because a register must keep the history. The page size is the row cap.
    @Query("""
        SELECT e FROM AgHealthEvent e
        WHERE e.tenantId = :tenantId AND e.eventDate >= :from AND e.eventDate <= :to
        AND (e.animalId IN (SELECT a.id FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId)
             OR e.groupId IN (SELECT g.id FROM AgGroup g WHERE g.tenantId = :tenantId AND g.farmId = :farmId))
        ORDER BY e.eventDate ASC, e.createdAt ASC
        """)
    List<AgHealthEvent> findForFarmBetween(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to, Pageable pageable);

    // Backs AgCostReportingService — COALESCE so an animal/group with zero
    // health events returns 0, not null, keeping the arithmetic in the cost
    // service simple, mirroring VehicleServiceRepository.sumCostByVehicle().
    @Query("SELECT COALESCE(SUM(e.cost), 0) FROM AgHealthEvent e WHERE e.tenantId = :tenantId AND e.animalId = :animalId")
    BigDecimal sumCostByAnimal(TenantId tenantId, UUID animalId);

    @Query("SELECT COALESCE(SUM(e.cost), 0) FROM AgHealthEvent e WHERE e.tenantId = :tenantId AND e.groupId = :groupId")
    BigDecimal sumCostByGroup(TenantId tenantId, UUID groupId);

    // Trends: [eventDate, cost, animalId, groupId]. Like the cost reports, every event with a cost counts, whatever its status.
    @Query("SELECT e.eventDate, e.cost, e.animalId, e.groupId FROM AgHealthEvent e WHERE e.tenantId = :tenantId AND e.eventDate >= :startDate AND e.eventDate <= :endDate AND e.cost IS NOT NULL")
    List<Object[]> findCostsBetween(TenantId tenantId, LocalDate startDate, LocalDate endDate);

    // The farm calendar: reminders falling due in the window (not yet acknowledged) and treatments scheduled in it, for the farm's
    // animals and groups that are still on it. Overdue items are the attention list's job, so they only show here when the window reaches back.
    @Query("""
        SELECT e FROM AgHealthEvent e
        WHERE e.tenantId = :tenantId
        AND ((e.nextDueDate IS NOT NULL AND e.nextDueDate >= :from AND e.nextDueDate <= :to AND e.reminderAcknowledged = false)
             OR (e.status = 'SCHEDULED' AND e.eventDate >= :from AND e.eventDate <= :to))
        AND (e.animalId IN (SELECT a.id FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.deletedAt IS NULL AND a.status = 'ACTIVE')
             OR e.groupId IN (SELECT g.id FROM AgGroup g WHERE g.tenantId = :tenantId AND g.farmId = :farmId AND g.deletedAt IS NULL AND g.status = 'ACTIVE'))
        ORDER BY e.eventDate ASC
        """)
    List<AgHealthEvent> findCalendarForFarm(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to);

    // The farm calendar: given (not merely scheduled) treatments with any withdrawal period, from `since` to `to`. The day each period
    // ends is worked out by AgWithdrawalRules. Newest first, and the page size is the row cap.
    @Query("""
        SELECT e FROM AgHealthEvent e
        WHERE e.tenantId = :tenantId AND e.status <> 'SCHEDULED'
        AND e.eventDate >= :since AND e.eventDate <= :to
        AND ((e.withdrawalPeriodDays IS NOT NULL AND e.withdrawalPeriodDays > 0)
             OR (e.milkWithdrawalDays IS NOT NULL AND e.milkWithdrawalDays > 0)
             OR (e.eggWithdrawalDays IS NOT NULL AND e.eggWithdrawalDays > 0))
        AND (e.animalId IN (SELECT a.id FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.deletedAt IS NULL AND a.status = 'ACTIVE')
             OR e.groupId IN (SELECT g.id FROM AgGroup g WHERE g.tenantId = :tenantId AND g.farmId = :farmId AND g.deletedAt IS NULL AND g.status = 'ACTIVE'))
        ORDER BY e.eventDate DESC, e.id DESC
        """)
    List<AgHealthEvent> findWithdrawalsForCalendar(TenantId tenantId, UUID farmId, LocalDate since, LocalDate to, Pageable pageable);
}
