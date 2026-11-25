package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceReminderLog;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ComplianceReminderLogRepository extends JpaRepository<ComplianceReminderLog, UUID> {
    @Query("SELECT l FROM ComplianceReminderLog l WHERE l.tenantId.value = :#{#tenantId.value} AND l.userId = :userId AND l.runDate = :day")
    List<ComplianceReminderLog> findForDay(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId, @Param("day") LocalDate day);

    @Query("SELECT l FROM ComplianceReminderLog l WHERE l.tenantId.value = :#{#tenantId.value} ORDER BY l.runDate DESC, l.createdAt DESC")
    List<ComplianceReminderLog> findRecent(@Param("tenantId") TenantId tenantId, Pageable page);
}
