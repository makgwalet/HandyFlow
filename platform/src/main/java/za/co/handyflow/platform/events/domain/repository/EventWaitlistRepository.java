package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventWaitlistEntry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventWaitlistRepository extends JpaRepository<EventWaitlistEntry, UUID> {

    /** Oldest-first batch of waiting entries — the promotion order. */
    @Query("SELECT w FROM EventWaitlistEntry w WHERE w.eventId = :eventId AND w.status = 'WAITING' " +
            "ORDER BY w.createdAt ASC")
    List<EventWaitlistEntry> findWaiting(UUID eventId, Pageable pageable);

    @Query("SELECT w FROM EventWaitlistEntry w WHERE w.eventId = :eventId AND w.status = :status " +
            "ORDER BY w.createdAt ASC")
    Page<EventWaitlistEntry> findByEventAndStatus(UUID eventId, String status, Pageable pageable);

    @Query("SELECT w FROM EventWaitlistEntry w WHERE w.id = :id AND w.eventId = :eventId")
    Optional<EventWaitlistEntry> findByIdAndEvent(UUID id, UUID eventId);

    @Query("SELECT COUNT(w) FROM EventWaitlistEntry w WHERE w.eventId = :eventId AND w.status = 'WAITING'")
    long countWaiting(UUID eventId);

    @Query("SELECT COUNT(w) > 0 FROM EventWaitlistEntry w WHERE w.eventId = :eventId AND w.status = 'WAITING' " +
            "AND LOWER(w.email) = LOWER(:email) AND LOWER(w.fullName) = LOWER(:fullName)")
    boolean existsWaiting(UUID eventId, String email, String fullName);

    /** Closes the whole waitlist when an event is completed or cancelled. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE EventWaitlistEntry w SET w.status = 'REMOVED', w.updatedAt = :now " +
            "WHERE w.eventId = :eventId AND w.status = 'WAITING'")
    int removeAllWaiting(UUID eventId, Instant now);
}