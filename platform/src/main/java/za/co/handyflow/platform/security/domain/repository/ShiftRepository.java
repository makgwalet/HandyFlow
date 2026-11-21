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

    /** One guard's own shifts in a window, for the guard app's "my shifts" screen. */
    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.guardId    = :guardId
        AND s.deletedAt  IS NULL
        AND s.status NOT IN ('CANCELLED')
        AND s.endAt      > :from
        AND s.startAt    < :to
        ORDER BY s.startAt ASC
        """)
    List<Shift> findForGuardBetween(TenantId tenantId, UUID guardId, Instant from, Instant to);

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.id = :id
        AND s.deletedAt IS NULL
        """)
    Optional<Shift> findActiveById(TenantId tenantId, UUID id);

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

    @Modifying
    @Query("""
        UPDATE Shift s
        SET s.notes     = :notes,
            s.endAt     = :endAt,
            s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.id = :id
        """)
    void updateShift(UUID id, String notes, Instant endAt);

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.id = :id
        AND s.deletedAt IS NULL
        """)
    Optional<Shift> findByTenantAndId(TenantId tenantId, UUID id);

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

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.status = 'SCHEDULED'
        AND s.startAt < :threshold
        AND s.deletedAt IS NULL
        ORDER BY s.startAt
        """)
    List<Shift> findScheduledStartingBefore(TenantId tenantId, Instant threshold);

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.status = 'ACTIVE'
        AND s.endAt < :threshold
        AND s.deletedAt IS NULL
        ORDER BY s.endAt
        """)
    List<Shift> findActiveEndingBefore(TenantId tenantId, Instant threshold);

    // ── Reporting additions ────────────────────────────────────────────────────

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.siteId = :siteId
        AND s.startAt >= :from
        AND s.startAt < :to
        AND s.deletedAt IS NULL
        ORDER BY s.startAt
        """)
    List<Shift> findBySiteInRange(TenantId tenantId, UUID siteId, Instant from, Instant to);

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.guardId = :guardId
        AND s.startAt >= :from
        AND s.startAt < :to
        AND s.deletedAt IS NULL
        ORDER BY s.startAt
        """)
    List<Shift> findByGuardInRange(TenantId tenantId, UUID guardId, Instant from, Instant to);

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.startAt >= :from
        AND s.startAt < :to
        AND s.deletedAt IS NULL
        ORDER BY s.startAt
        """)
    List<Shift> findByTenantInRange(TenantId tenantId, Instant from, Instant to);

    // ── ADD THESE METHODS to the existing ShiftRepository interface ────────────
// (Do not replace the file -- append inside the existing interface body.)
//
// These replace the old findScheduledStartingBefore/findActiveEndingBefore
// call sites in NoShowAlertScheduler. The originals had no way to exclude
// shifts already alerted on; these add the *_alert_sent_at IS NULL filter
// so the scheduler only ever sees genuinely new breaches.

    /** SCHEDULED shifts past the LATE grace threshold that haven't been LATE-alerted yet. */
    @Query("""
    SELECT s FROM Shift s
    WHERE s.tenantId = :tenantId
    AND s.status = 'SCHEDULED'
    AND s.startAt < :lateThreshold
    AND s.lateAlertSentAt IS NULL
    AND s.deletedAt IS NULL
    ORDER BY s.siteId, s.startAt
    """)
    List<Shift> findLateNotYetAlerted(TenantId tenantId, Instant lateThreshold);

    /** SCHEDULED shifts past the NO_SHOW threshold that haven't been NO_SHOW-alerted yet. */
    @Query("""
    SELECT s FROM Shift s
    WHERE s.tenantId = :tenantId
    AND s.status = 'SCHEDULED'
    AND s.startAt < :noShowThreshold
    AND s.noShowAlertSentAt IS NULL
    AND s.deletedAt IS NULL
    ORDER BY s.siteId, s.startAt
    """)
    List<Shift> findNoShowNotYetAlerted(TenantId tenantId, Instant noShowThreshold);

    /** ACTIVE shifts past their scheduled end + grace that haven't been OVERTIME-alerted yet. */
    @Query("""
    SELECT s FROM Shift s
    WHERE s.tenantId = :tenantId
    AND s.status = 'ACTIVE'
    AND s.endAt < :overtimeThreshold
    AND s.overtimeAlertSentAt IS NULL
    AND s.deletedAt IS NULL
    ORDER BY s.siteId, s.endAt
    """)
    List<Shift> findOvertimeNotYetAlerted(TenantId tenantId, Instant overtimeThreshold);

    /**
     * Marks SCHEDULED shifts whose end time has passed as MISSED, in one atomic statement.
     * Safe to run from several instances: the status condition makes a second run a no-op.
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("""
        UPDATE Shift s
        SET s.status = :missed, s.updatedAt = :now
        WHERE s.status = :scheduled
        AND s.endAt < :cutoff
        AND s.deletedAt IS NULL
        """)
    int markEndedScheduledAsMissed(za.co.handyflow.platform.security.domain.model.ShiftStatus scheduled,
                                   za.co.handyflow.platform.security.domain.model.ShiftStatus missed,
                                   Instant cutoff, Instant now);

    /** The shifts the missed-sweep is about to mark, so a SHIFT_MISSED webhook can be sent for each. */
    @Query("""
        SELECT s FROM Shift s
        WHERE s.status = :scheduled
        AND s.endAt < :cutoff
        AND s.deletedAt IS NULL
        ORDER BY s.endAt
        """)
    List<Shift> findEndedScheduled(za.co.handyflow.platform.security.domain.model.ShiftStatus scheduled,
                                   Instant cutoff, org.springframework.data.domain.Pageable page);

    /** Marks one shift MISSED only if it is still SCHEDULED; returns 1 if this call did it, 0 if someone else did. */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("""
        UPDATE Shift s
        SET s.status = :missed, s.updatedAt = :now
        WHERE s.id = :id AND s.status = :scheduled AND s.deletedAt IS NULL
        """)
    int markMissedIfScheduled(UUID id, za.co.handyflow.platform.security.domain.model.ShiftStatus scheduled,
                              za.co.handyflow.platform.security.domain.model.ShiftStatus missed, Instant now);
}
