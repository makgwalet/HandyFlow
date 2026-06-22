package za.co.handyflow.platform.invoicing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.invoicing.domain.model.RecurringSchedule;
import za.co.handyflow.platform.invoicing.domain.model.RecurringScheduleStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringScheduleRepository extends JpaRepository<RecurringSchedule, UUID> {

    @Query("SELECT s FROM RecurringSchedule s WHERE s.tenantId = :tenantId AND s.status <> 'CANCELLED' ORDER BY s.createdAt DESC")
    Page<RecurringSchedule> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("SELECT s FROM RecurringSchedule s WHERE s.tenantId = :tenantId AND s.id = :id AND s.status <> 'CANCELLED'")
    Optional<RecurringSchedule> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    /** Used by the scheduler to find all ACTIVE schedules whose nextRunAt is due. */
    @Query("SELECT s FROM RecurringSchedule s WHERE s.status = 'ACTIVE' AND s.nextRunAt <= :now")
    List<RecurringSchedule> findDueSchedules(@Param("now") Instant now);
}