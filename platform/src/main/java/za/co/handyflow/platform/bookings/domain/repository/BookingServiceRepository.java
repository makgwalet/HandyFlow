package za.co.handyflow.platform.bookings.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.bookings.domain.model.BookingService;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingServiceRepository extends JpaRepository<BookingService, UUID> {

    @Query("SELECT s FROM BookingService s WHERE s.tenantId = :#{#tenantId.value} AND s.deletedAt IS NULL AND s.active = true ORDER BY s.name")
    List<BookingService> findAllActive(TenantId tenantId);

    @Query("SELECT s FROM BookingService s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id AND s.deletedAt IS NULL")
    Optional<BookingService> findActiveById(TenantId tenantId, UUID id);
}