package za.co.handyflow.platform.crm.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.ReportSchedule;
import za.co.handyflow.platform.shared.TenantId;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {
    @Query("SELECT s FROM ReportSchedule s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL ORDER BY s.createdAt, s.id")
    List<ReportSchedule> findAllActive(@Param("tenantId") TenantId tenantId);

    @Query("SELECT s FROM ReportSchedule s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL AND s.id = :id")
    Optional<ReportSchedule> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
