package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventCheckIn;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventCheckInRepository extends JpaRepository<EventCheckIn, UUID> {

    @Query("SELECT c FROM EventCheckIn c WHERE c.eventId = :eventId ORDER BY c.scannedAt DESC")
    List<EventCheckIn> findByEvent(UUID eventId);

    @Query("SELECT COUNT(c) FROM EventCheckIn c WHERE c.eventId = :eventId AND c.result = 'SUCCESS'")
    long countSuccessful(UUID eventId);

    // ── Dashboard / report queries (Chunk 5) ──────────────────────────────────

    @Query("SELECT COUNT(c) FROM EventCheckIn c WHERE c.eventId = :eventId")
    long countByEvent(UUID eventId);

    /** Scan outcomes: SUCCESS, WALK_IN, ALREADY_CHECKED_IN, CANCELLED_TICKET, TRANSFERRED_TICKET, NOT_FOUND. */
    @Query("SELECT c.result AS label, COUNT(c) AS total FROM EventCheckIn c " +
            "WHERE c.eventId = :eventId GROUP BY c.result")
    List<LabelCount> countGroupedByResult(UUID eventId);

    /** Timestamps of scans that actually admitted someone — the input to the arrivals-over-time chart. */
    @Query("SELECT c.scannedAt FROM EventCheckIn c WHERE c.eventId = :eventId " +
            "AND c.result IN ('SUCCESS','WALK_IN') ORDER BY c.scannedAt")
    List<Instant> findAdmissionTimes(UUID eventId);
}