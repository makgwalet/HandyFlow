// security/domain/repository/ShiftRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.shared.TenantId;

import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.deletedAt IS NULL
        ORDER BY s.startAt DESC
        """)
    Page<Shift> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.id = :id
        AND s.deletedAt IS NULL
        """)
    Optional<Shift> findActiveById(TenantId tenantId, UUID id);

    /**
     * Overlap detection — finds conflicting shifts for the same guard.
     *
     * WHY add tenantId? (fixes bug #15)
     * The original query filtered only on guardId.  Guard UUIDs are globally
     * unique so cross-tenant collision is essentially impossible, but the
     * tenantId filter is defence-in-depth: if a UUID ever aliased across
     * tenants (e.g. via a DB restore or a data migration), overlap detection
     * would block/allow shifts based on another tenant's schedule.
     * One extra index lookup costs nothing and is always correct.
     *
     * WHY exclude CANCELLED and MISSED?
     * A CANCELLED shift no longer occupies the time slot.
     * A MISSED shift was never started, so it logically frees the slot.
     * Only SCHEDULED, ACTIVE, and COMPLETED shifts block rescheduling.
     */
    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId  = :tenantId
        AND s.guardId     = :guardId
        AND s.deletedAt   IS NULL
        AND s.status NOT IN ('CANCELLED', 'MISSED')
        AND s.startAt     < :endAt
        AND s.endAt       > :startAt
        """)
    List<Shift> findOverlapping(TenantId tenantId, UUID guardId, Instant startAt, Instant endAt);

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.siteId = :siteId
        AND s.deletedAt IS NULL
        ORDER BY s.startAt DESC
        """)
    Page<Shift> findBySite(TenantId tenantId, UUID siteId, Pageable pageable);

    /**
     * Updates mutable shift fields — notes and endAt.
     * Used by ShiftService.updateShift() (fixes bug #4).
     *
     * WHY @Modifying + native JPQL instead of adding an update() method to Shift?
     * Shift is an aggregate root whose state transitions are controlled by domain
     * methods (start(), complete(), cancel()).  Notes and endAt are operational
     * metadata, not state transitions.  A JPQL update is cleaner than adding a
     * generic setter to the domain model just to serve this one use case.
     * Phase 1: revisit if more fields need update semantics.
     */
    @Modifying
    @Query("""
        UPDATE Shift s
        SET s.notes     = :notes,
            s.endAt     = :endAt,
            s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.id = :id
        """)
    void updateShift(UUID id, String notes, Instant endAt);

    // ── Phase 1.5 additions ────────────────────────────────────────────────────

    /**
     * Tenant-scoped findById — avoids fetching shifts across tenant boundaries.
     */
    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.id = :id
        AND s.deletedAt IS NULL
        """)
    Optional<Shift> findByTenantAndId(TenantId tenantId, UUID id);

    /**
     * Overlap check returning a boolean — used by swap approval and rotation
     * schedule generation.  Excludes an optional shift ID (null = no exclusion)
     * so an existing shift can check against itself without false positives.
     */
    @Query("""
        SELECT COUNT(s) > 0 FROM Shift s
        WHERE s.tenantId  = :tenantId
        AND s.guardId     = :guardId
        AND s.deletedAt   IS NULL
        AND s.status NOT IN ('CANCELLED', 'MISSED')
        AND s.startAt     < :endAt
        AND s.endAt       > :startAt
        AND (:excludeId IS NULL OR s.id <> :excludeId)
        """)
    boolean hasOverlap(TenantId tenantId, UUID guardId,
                       Instant startAt, Instant endAt,
                       UUID excludeId);

    /**
     * SCHEDULED shifts whose startAt + grace period has passed — used by
     * NoShowAlertScheduler to detect late/no-show guards.
     */
    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.status = 'SCHEDULED'
        AND s.startAt < :threshold
        AND s.deletedAt IS NULL
        ORDER BY s.startAt
        """)
    List<Shift> findScheduledStartingBefore(TenantId tenantId, Instant threshold);

    /**
     * ACTIVE shifts whose endAt + overtime grace has passed — used by
     * NoShowAlertScheduler to detect forgotten clock-outs.
     */
    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.status = 'ACTIVE'
        AND s.endAt < :threshold
        AND s.deletedAt IS NULL
        ORDER BY s.endAt
        """)
    List<Shift> findActiveEndingBefore(TenantId tenantId, Instant threshold);
}
