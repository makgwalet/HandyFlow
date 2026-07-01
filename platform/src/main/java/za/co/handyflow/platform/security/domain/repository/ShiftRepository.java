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
}
