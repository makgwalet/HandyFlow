package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditActionPlan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditActionPlanRepository extends JpaRepository<AuditActionPlan, UUID> {

    @Query("SELECT p FROM AuditActionPlan p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<AuditActionPlan> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM AuditActionPlan p WHERE p.tenantId = :tenantId AND p.findingId = :findingId ORDER BY p.dueDate, p.createdAt")
    List<AuditActionPlan> findByFinding(@Param("tenantId") UUID tenantId, @Param("findingId") UUID findingId);

    @Query("SELECT p FROM AuditActionPlan p WHERE p.tenantId = :tenantId AND p.status IN ('OPEN', 'COMPLETED') AND p.dueDate < :today ORDER BY p.dueDate")
    List<AuditActionPlan> findOverdue(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today);
}
