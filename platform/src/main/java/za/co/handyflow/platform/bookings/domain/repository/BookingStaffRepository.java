package za.co.handyflow.platform.bookings.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.bookings.domain.model.BookingStaff;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingStaffRepository extends JpaRepository<BookingStaff, UUID> {

    @Query("SELECT s FROM BookingStaff s WHERE s.tenantId = :#{#tenantId.value} AND s.active = true ORDER BY s.name")
    List<BookingStaff> findAllActive(TenantId tenantId);

    @Query("SELECT s FROM BookingStaff s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id")
    Optional<BookingStaff> findByTenantAndId(TenantId tenantId, UUID id);
}