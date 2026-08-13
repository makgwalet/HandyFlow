package za.co.handyflow.platform.bookingagency.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookingagency.domain.model.BookAgencyBooking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookAgencyBookingRepository extends JpaRepository<BookAgencyBooking, UUID> {

    @Query("SELECT b FROM BookAgencyBooking b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<BookAgencyBooking> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * Candidate bookings to check for overlap — deliberately widened
     * beyond the exact requested window (any CONFIRMED booking for this
     * resource that starts before the requested end) rather than trying
     * to express the full overlap condition in JPQL. The actual
     * overlap check (BookAgencyBooking.overlaps()) runs in Java against
     * this candidate set — simpler and more obviously correct than a
     * denser SQL overlap predicate, at the cost of pulling a few more
     * rows than strictly necessary. Fine at the row counts a single
     * resource's booking calendar realistically has; revisit if that
     * assumption stops holding.
     */
    @Query("""
        SELECT b FROM BookAgencyBooking b
        WHERE b.resourceId = :resourceId AND b.status = 'CONFIRMED'
        AND b.startDatetime < :windowEnd
        """)
    List<BookAgencyBooking> findConfirmedCandidatesForOverlapCheck(
            @Param("resourceId") UUID resourceId, @Param("windowEnd") LocalDateTime windowEnd);

    @Query("SELECT b FROM BookAgencyBooking b WHERE b.clientId = :clientId ORDER BY b.startDatetime DESC")
    Page<BookAgencyBooking> findByClient(@Param("clientId") UUID clientId, Pageable pageable);
}