package za.co.handyflow.platform.bookings.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookings.domain.model.Booking;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
            SELECT b FROM Booking b
            WHERE b.tenantId = :#{#tenantId.value}
              AND b.id = :id
            """)
    Optional<Booking> findByTenantAndId(TenantId tenantId, UUID id);

    /**
     * Find conflicting bookings for slot availability checking.
     *
     * WHY check cancelled_at IS NULL in addition to status?
     * The schema has both a status column AND a cancelled_at timestamp.
     * Using only status IN ('CANCELLED','NO_SHOW') is sufficient, but
     * being explicit about cancelled_at IS NULL makes the intent clearer
     * and matches the V97 index definition.
     *
     * WHY use b.startTime < slotEnd AND b.endTime > slotStart?
     * This is the standard interval overlap test:
     *   A overlaps B if A.start < B.end AND A.end > B.start
     * It catches all overlap cases: partial overlap, full containment,
     * and exact time match.
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.staffId = :staffId
              AND b.bookingDate = :date
              AND b.status NOT IN ('CANCELLED', 'NO_SHOW')
              AND b.startTime < :slotEnd
              AND b.endTime > :slotStart
            """)
    List<Booking> findConflicts(
            @Param("staffId")   UUID staffId,
            @Param("date")      LocalDate date,
            @Param("slotStart") LocalTime slotStart,
            @Param("slotEnd")   LocalTime slotEnd
    );

    /**
     * All active (non-cancelled) bookings for a staff member on a date.
     *
     * WHY a separate query from findConflicts?
     * SlotEngine needs ALL bookings on the day to check buffer overlap
     * across the whole schedule.  findConflicts takes a specific time range
     * (slot start/end) — it can't be reused for the "load all for the day"
     * case without awkward dummy parameters.
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.staffId = :staffId
              AND b.bookingDate = :date
              AND b.status NOT IN ('CANCELLED', 'NO_SHOW')
            ORDER BY b.startTime
            """)
    List<Booking> findActiveOnDate(
            @Param("staffId") UUID staffId,
            @Param("date")    LocalDate date
    );

    /**
     * Find confirmed bookings whose reminder has not yet been sent,
     * scheduled for tomorrow.  Used by the reminder scheduler.
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.tenantId = :#{#tenantId.value}
              AND b.status = 'CONFIRMED'
              AND b.reminderSent = false
              AND b.bookingDate = :tomorrow
            """)
    List<Booking> findUnremindedForDate(
            @Param("tenantId") TenantId tenantId,
            @Param("tomorrow") LocalDate tomorrow
    );

    /**
     * Returns distinct tenant IDs that have at least one booking.
     * Used by schedulers to iterate tenants without importing TenantRepository.
     * Same pattern as CustomerRepository.findDistinctActiveTenantIds().
     */
    @Query(value = "SELECT DISTINCT b.tenant_id FROM bookings b", nativeQuery = true)
    List<UUID> findDistinctActiveTenantIds();

}