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

    @Query("SELECT b FROM Booking b WHERE b.tenantId = :#{#tenantId.value} AND b.id = :id")
    Optional<Booking> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
    SELECT b FROM Booking b
    WHERE b.staffId = :staffId
    AND b.bookingDate = :date
    AND b.status NOT IN ('CANCELLED','NO_SHOW')
    AND b.startTime < :slotEnd
    AND b.endTime > :slotStart
    """)
    List<Booking> findConflicts(UUID staffId, LocalDate date,
                                LocalTime slotStart, LocalTime slotEnd);

    @Query("""
    SELECT b FROM Booking b
    WHERE b.tenantId = :#{#tenantId.value}
    AND b.status = 'CONFIRMED'
    AND b.reminderSent = false
    AND b.bookingDate = :tomorrow
    """)
    List<Booking> findUnremindedForDate(TenantId tenantId, LocalDate tomorrow);
}