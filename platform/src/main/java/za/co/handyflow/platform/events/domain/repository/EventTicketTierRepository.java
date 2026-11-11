package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventTicketTier;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventTicketTierRepository extends JpaRepository<EventTicketTier, UUID> {

    @Query("SELECT t FROM EventTicketTier t WHERE t.eventId = :eventId AND t.active = true ORDER BY t.price")
    List<EventTicketTier> findByEvent(UUID eventId);

    @Query("SELECT t FROM EventTicketTier t WHERE t.id = :id AND t.eventId = :eventId")
    Optional<EventTicketTier> findByIdAndEvent(UUID id, UUID eventId);

    @Query("SELECT COUNT(t) FROM EventTicketTier t WHERE t.eventId = :eventId AND t.active = true AND t.quantitySold < t.quantity")
    long countAvailableTiers(UUID eventId);

    /**
     * Seats left on a tier, read straight from the database. Deliberately a
     * scalar query rather than tier.getAvailable(): claimSeat()/releaseSeat()
     * change the row without updating an already-loaded entity, so a loaded
     * tier's quantitySold can be stale within the same transaction.
     * Returns null if the tier doesn't exist.
     */
    @Query("SELECT (t.quantity - t.quantitySold) FROM EventTicketTier t WHERE t.id = :id")
    Integer availableSeats(UUID id);

    /**
     * Atomically claims ONE seat. The availability test and the increment are
     * a single SQL statement, so two concurrent registrations can never both
     * take the last seat.
     *
     * @return 1 if a seat was claimed, 0 if the tier is sold out or inactive
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE EventTicketTier t SET t.quantitySold = t.quantitySold + 1, t.updatedAt = :now " +
            "WHERE t.id = :id AND t.active = true AND t.quantitySold < t.quantity")
    int claimSeat(UUID id, Instant now);

    /** Atomically gives a seat back; guarded so the counter can never go negative. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE EventTicketTier t SET t.quantitySold = t.quantitySold - 1, t.updatedAt = :now " +
            "WHERE t.id = :id AND t.quantitySold > 0")
    int releaseSeat(UUID id, Instant now);

    /** Atomic checked-in counter bump — never a full-row save from check-in. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE EventTicketTier t SET t.quantityCheckedIn = t.quantityCheckedIn + 1, t.updatedAt = :now " +
            "WHERE t.id = :id")
    int markCheckedIn(UUID id, Instant now);
}