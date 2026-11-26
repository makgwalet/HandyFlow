package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAuditActionRepository extends JpaRepository<TrainProvAuditAction, UUID> {
    @Query("SELECT a FROM TrainProvAuditAction a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvAuditAction> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT a FROM TrainProvAuditAction a WHERE a.tenantId = :#{#tenantId.value} AND a.auditId = :auditId ORDER BY a.dueOn, a.createdAt")
    List<TrainProvAuditAction> forAudit(TenantId tenantId, UUID auditId);

    @Query("SELECT a FROM TrainProvAuditAction a WHERE a.tenantId = :#{#tenantId.value} AND a.status = 'OPEN' AND a.dueOn < :today ORDER BY a.dueOn")
    List<TrainProvAuditAction> overdue(TenantId tenantId, java.time.LocalDate today);
}
