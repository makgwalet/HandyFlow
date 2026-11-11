package za.co.handyflow.platform.events.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query("""
        SELECT e FROM Event e WHERE e.tenantId = :#{#tenantId.value}
        AND e.deletedAt IS NULL
        AND (:status IS NULL OR e.status = :status)
        AND (:type IS NULL OR e.eventType = :type)
        ORDER BY e.startDatetime ASC
        """)
    Page<Event> findAll(TenantId tenantId, String status,
                        String type, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id AND e.deletedAt IS NULL")
    Optional<Event> findActiveById(TenantId tenantId, UUID id);

    /**
     * Same lookup as findActiveById but takes a row lock (SELECT ... FOR UPDATE)
     * held until the surrounding transaction ends. Registration, cancellation,
     * budget and status changes for ONE event are serialised through this.
     * Different events never block each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id AND e.deletedAt IS NULL")
    Optional<Event> findActiveByIdForUpdate(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenantId = :#{#tenantId.value} AND e.deletedAt IS NULL")
    long countByTenant(TenantId tenantId);

    // ── Dashboard queries (Chunk 5) ───────────────────────────────────────────

    @Query("SELECT e.status AS label, COUNT(e) AS total FROM Event e " +
            "WHERE e.tenantId = :#{#tenantId.value} AND e.deletedAt IS NULL GROUP BY e.status")
    List<LabelCount> countByStatusForTenant(TenantId tenantId);

    /** Everything LIVE right now, plus not-yet-live events starting inside the window (oldest start first). */
    @Query("""
        SELECT e FROM Event e WHERE e.tenantId = :#{#tenantId.value} AND e.deletedAt IS NULL
        AND (e.status = 'LIVE'
             OR (e.status IN ('DRAFT','PUBLISHED','SOLD_OUT') AND e.startDatetime BETWEEN :fromTime AND :toTime))
        ORDER BY e.startDatetime ASC
        """)
    List<Event> findUpcomingOrLive(TenantId tenantId, LocalDateTime fromTime, LocalDateTime toTime,
                                   Pageable pageable);
}