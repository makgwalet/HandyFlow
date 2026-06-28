package za.co.handyflow.platform.bookings.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookings.domain.model.BookingServiceStaff;

import java.util.List;
import java.util.UUID;

/**
 * BookingServiceStaffRepository — manages the staff–service skill mapping.
 *
 * WHY a separate repository and not just native SQL in BookingsService?
 * The skill mapping is a domain concept: "Thandi is qualified to perform
 * Full Colour Treatment."  Keeping it behind a typed repository means:
 *   1. The service layer speaks in domain terms, not raw SQL.
 *   2. The SlotEngine can query skills without knowing the table structure.
 *   3. Future skill-level attributes (e.g. "junior", "senior") can be added
 *      to BookingServiceStaff without touching every caller.
 *
 * BACKWARDS COMPATIBILITY:
 * If a service has NO assignments in booking_service_staff, ALL staff are
 * eligible (open access, matches the original behaviour).
 * Once you add an assignment for a service, only assigned staff appear.
 * This means existing tenants who haven't configured skills yet are unaffected.
 */
public interface BookingServiceStaffRepository
        extends JpaRepository<BookingServiceStaff, BookingServiceStaff.Id> {

    /** Which staff members are assigned to a given service? */
    @Query(value = "SELECT staff_id FROM booking_service_staff WHERE service_id = :serviceId",
            nativeQuery = true)
    List<UUID> findStaffIdsByService(@Param("serviceId") UUID serviceId);

    /** Which services is a given staff member assigned to? */
    @Query(value = "SELECT service_id FROM booking_service_staff WHERE staff_id = :staffId",
            nativeQuery = true)
    List<UUID> findServiceIdsByStaff(@Param("staffId") UUID staffId);

    /** Does a specific assignment exist? */
    @Query(value = "SELECT COUNT(*) > 0 FROM booking_service_staff WHERE service_id = :svcId AND staff_id = :staffId",
            nativeQuery = true)
    boolean existsAssignment(@Param("svcId") UUID serviceId, @Param("staffId") UUID staffId);

    /** Does this service have ANY skill assignments? */
    @Query(value = "SELECT COUNT(*) > 0 FROM booking_service_staff WHERE service_id = :serviceId",
            nativeQuery = true)
    boolean hasAnyAssignments(@Param("serviceId") UUID serviceId);

    /** Remove all staff from a service (used when re-setting assignments). */
    @Modifying
    @Query(value = "DELETE FROM booking_service_staff WHERE service_id = :serviceId",
            nativeQuery = true)
    void deleteByServiceId(@Param("serviceId") UUID serviceId);

    /** Remove a specific assignment. */
    @Modifying
    @Query(value = "DELETE FROM booking_service_staff WHERE service_id = :svcId AND staff_id = :staffId",
            nativeQuery = true)
    void deleteAssignment(@Param("svcId") UUID serviceId, @Param("staffId") UUID staffId);
}
