package za.co.handyflow.platform.bookings.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.bookings.domain.model.BookingBlock;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BookingBlockRepository extends JpaRepository<BookingBlock, UUID> {

    @Query("SELECT b FROM BookingBlock b WHERE b.tenantId = :#{#tenantId.value} AND b.blockDate = :date AND (b.staffId = :staffId OR b.staffId IS NULL)")
    List<BookingBlock> findForStaffOnDate(TenantId tenantId, UUID staffId, LocalDate date);
}