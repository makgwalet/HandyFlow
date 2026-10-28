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

    @Query("SELECT p FROM AuditActionPlan p WHERE p.tenantId = :tenantId ORDER BY p.dueDate, p.createdAt")
    List<AuditActionPlan> findAllForTenant(@Param("tenantId") UUID tenantId);

    /** Cross-tenant sweep for reminders: every action past its due date and still not validated. */
    @Query("SELECT p FROM AuditActionPlan p WHERE p.status IN ('OPEN', 'COMPLETED') AND p.dueDate < :today")
    List<AuditActionPlan> findOverdueAcrossTenants(@Param("today") LocalDate today);

    @Query("SELECT p FROM AuditActionPlan p WHERE p.tenantId = :tenantId AND p.owner = :userId AND p.status = 'OPEN' ORDER BY p.dueDate")
    List<AuditActionPlan> findOpenOwnedBy(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /** Completed and waiting for validation by someone who neither owns the action nor completed it. */
    @Query("SELECT p FROM AuditActionPlan p WHERE p.tenantId = :tenantId AND p.status = 'COMPLETED' AND (p.owner IS NULL OR p.owner <> :userId) AND (p.completedBy IS NULL OR p.completedBy <> :userId) ORDER BY p.dueDate")
    List<AuditActionPlan> findAwaitingValidationBy(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    @Query("SELECT p FROM AuditActionPlan p WHERE p.tenantId = :tenantId AND p.findingId IN :findingIds ORDER BY p.dueDate, p.createdAt")
    List<AuditActionPlan> findByFindingIds(@Param("tenantId") UUID tenantId, @Param("findingIds") java.util.Collection<UUID> findingIds);
}
