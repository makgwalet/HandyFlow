package za.co.handyflow.platform.events.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventGuest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventGuestRepository extends JpaRepository<EventGuest, UUID> {

    @Query("""
        SELECT g FROM EventGuest g WHERE g.eventId = :eventId
        AND (:status IS NULL OR g.status = :status)
        AND (:tierId IS NULL OR g.tierId = :tierId)
        ORDER BY g.createdAt DESC
        """)
    Page<EventGuest> findByEvent(UUID eventId, String status,
                                 UUID tierId, Pageable pageable);

    /** Look up by the UUID QR token stored in qr_code column */
    @Query("SELECT g FROM EventGuest g WHERE g.qrCode = :qrCode")
    Optional<EventGuest> findByQrCode(String qrCode);

    /** Look up by the human-readable ticket number (EVT-2026-00001-0001). */
    @Query("SELECT g FROM EventGuest g WHERE g.ticketNumber = :ticketNumber")
    Optional<EventGuest> findByTicketNumber(String ticketNumber);

    /**
     * Check-in lookup: matches a QR token OR a typed ticket number, scoped to
     * ONE event, and locks the guest row until the transaction ends so two
     * simultaneous scans of one ticket can't both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM EventGuest g WHERE g.eventId = :eventId " +
            "AND (g.qrCode = :code OR g.ticketNumber = :code)")
    Optional<EventGuest> findForCheckIn(UUID eventId, String code);

    @Query("SELECT g FROM EventGuest g WHERE g.id = :id AND g.eventId = :eventId")
    Optional<EventGuest> findByIdAndEvent(UUID id, UUID eventId);

    /**
     * Live registrations: excludes CANCELLED, NO_SHOW, TRANSFERRED (the old holder of a moved ticket) and
     * EXPIRED (an unpaid hold that ran out). RESERVED holds DO count — they occupy a place until they expire.
     */
    @Query("SELECT COUNT(g) FROM EventGuest g WHERE g.eventId = :eventId " +
            "AND g.status NOT IN ('CANCELLED','NO_SHOW','TRANSFERRED','EXPIRED')")
    long countActive(UUID eventId);

    /**
     * Every guest record ever created for the event, whatever its status.
     * Records are never deleted, so this only grows — which is what makes
     * (count + 1) a safe ticket sequence under the event row lock.
     */
    @Query("SELECT COUNT(g) FROM EventGuest g WHERE g.eventId = :eventId")
    long countAllByEvent(UUID eventId);

    @Query("SELECT COUNT(g) FROM EventGuest g WHERE g.eventId = :eventId AND g.status = :status")
    long countByStatus(UUID eventId, String status);

    @Query("SELECT COUNT(g) FROM EventGuest g WHERE g.eventId = :eventId AND g.status = 'CHECKED_IN'")
    long countCheckedIn(UUID eventId);

    /** Same person (email + name, case-insensitive) already holding a live registration for this event. */
    @Query("SELECT COUNT(g) > 0 FROM EventGuest g WHERE g.eventId = :eventId " +
            "AND LOWER(g.email) = LOWER(:email) AND LOWER(g.fullName) = LOWER(:fullName) " +
            "AND g.status NOT IN ('CANCELLED','NO_SHOW','TRANSFERRED','EXPIRED')")
    boolean existsActiveDuplicate(UUID eventId, String email, String fullName);

    /** Recipients for postponement / cancellation notices: live guests who gave an email address. */
    @Query("SELECT g FROM EventGuest g WHERE g.eventId = :eventId AND g.email IS NOT NULL " +
            "AND g.status NOT IN ('CANCELLED','NO_SHOW','TRANSFERRED','EXPIRED')")
    List<EventGuest> findActiveWithEmail(UUID eventId);

    @Query("SELECT g FROM EventGuest g WHERE g.eventId = :eventId AND g.status = 'CHECKED_IN'")
    List<EventGuest> findCheckedIn(UUID eventId);

    // ── Dashboard / report queries (Chunk 5) ──────────────────────────────────

    /** Every status, with its count (CANCELLED and TRANSFERRED included). */
    @Query("SELECT g.status AS label, COUNT(g) AS total FROM EventGuest g " +
            "WHERE g.eventId = :eventId GROUP BY g.status")
    List<LabelCount> countGroupedByStatus(UUID eventId);

    /** How live registrations came about: STAFF, GROUP, WALK_IN, IMPORT, WAITLIST, TRANSFER. */
    @Query("SELECT g.source AS label, COUNT(g) AS total FROM EventGuest g WHERE g.eventId = :eventId " +
            "AND g.status NOT IN ('CANCELLED','NO_SHOW','TRANSFERRED','EXPIRED') GROUP BY g.source")
    List<LabelCount> countActiveGroupedBySource(UUID eventId);

    /**
     * When each registration was made, for the registrations-per-day trend.
     * TRANSFER records are excluded: a transfer re-issues a ticket, it is not a new registration.
     */
    @Query("SELECT g.createdAt FROM EventGuest g WHERE g.eventId = :eventId AND g.source <> 'TRANSFER' " +
            "ORDER BY g.createdAt")
    List<Instant> findRegistrationTimes(UUID eventId);

    /** Total of the amounts staff recorded as paid, live registrations only. Null when there are none. */
    @Query("SELECT SUM(g.amountPaid) FROM EventGuest g WHERE g.eventId = :eventId " +
            "AND g.status NOT IN ('CANCELLED','NO_SHOW','TRANSFERRED','EXPIRED')")
    BigDecimal sumAmountPaid(UUID eventId);

    @Query("SELECT COUNT(g) FROM EventGuest g WHERE g.eventId = :eventId AND g.paymentStatus = 'PENDING' " +
            "AND g.status NOT IN ('CANCELLED','NO_SHOW','TRANSFERRED','EXPIRED')")
    long countPaymentPending(UUID eventId);

    /** Live registration counts for many events in one query — the overview dashboard. */
    @Query("SELECT g.eventId AS eventId, COUNT(g) AS total FROM EventGuest g WHERE g.eventId IN :eventIds " +
            "AND g.status NOT IN ('CANCELLED','NO_SHOW','TRANSFERRED','EXPIRED') GROUP BY g.eventId")
    List<EventCount> countActiveByEvents(Collection<UUID> eventIds);

    /**
     * Money staff typed in against live guests who have NO payment record — i.e. tickets registered
     * the old way (staff-entered amountPaid) rather than through checkout. Reported separately in
     * profitability because it is unverified, but it must not be silently left out of revenue.
     */
    @Query("SELECT SUM(g.amountPaid) FROM EventGuest g WHERE g.eventId = :eventId " +
            "AND g.status NOT IN ('CANCELLED','NO_SHOW','TRANSFERRED','EXPIRED') AND g.paymentStatus <> 'FREE' " +
            "AND NOT EXISTS (SELECT p.id FROM EventPayment p WHERE p.guestId = g.id)")
    BigDecimal sumAmountPaidWithoutPayment(UUID eventId);

    /** The guest register: every record, in ticket order. Callers check the size first. */
    @Query("SELECT g FROM EventGuest g WHERE g.eventId = :eventId ORDER BY g.ticketNumber")
    List<EventGuest> findAllForExport(UUID eventId);

    /** Registered but never scanned in — the no-show list once an event is over. */
    @Query("SELECT g FROM EventGuest g WHERE g.eventId = :eventId AND g.status IN ('REGISTERED','CONFIRMED') " +
            "ORDER BY g.ticketNumber")
    List<EventGuest> findNotCheckedIn(UUID eventId);
}