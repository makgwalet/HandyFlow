package za.co.handyflow.platform.bookings.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.bookings.domain.model.BookingAvailability;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface BookingAvailabilityRepository extends JpaRepository<BookingAvailability, UUID> {

    @Query("SELECT a FROM BookingAvailability a WHERE a.tenantId = :#{#tenantId.value} AND a.active = true AND (a.staffId = :staffId OR a.staffId IS NULL) ORDER BY a.dayOfWeek")
    List<BookingAvailability> findForStaff(TenantId tenantId, UUID staffId);

    @Query("SELECT a FROM BookingAvailability a WHERE a.tenantId = :#{#tenantId.value} AND a.active = true AND a.staffId IS NULL ORDER BY a.dayOfWeek")
    List<BookingAvailability> findTenantWide(TenantId tenantId);
}