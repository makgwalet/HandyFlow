package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAuditItemRepository extends JpaRepository<TrainProvAuditItem, UUID> {
    @Query("SELECT i FROM TrainProvAuditItem i WHERE i.tenantId = :#{#tenantId.value} AND i.id = :id")
    Optional<TrainProvAuditItem> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT i FROM TrainProvAuditItem i WHERE i.tenantId = :#{#tenantId.value} AND i.auditId = :auditId ORDER BY i.position")
    List<TrainProvAuditItem> forAudit(TenantId tenantId, UUID auditId);
}
