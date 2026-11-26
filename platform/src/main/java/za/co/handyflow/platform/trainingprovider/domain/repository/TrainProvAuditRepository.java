package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAuditRepository extends JpaRepository<TrainProvAudit, UUID> {
    @Query("SELECT a FROM TrainProvAudit a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvAudit> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT a FROM TrainProvAudit a WHERE a.tenantId = :#{#tenantId.value} AND (:status IS NULL OR a.status = :status)
        AND (:scope IS NULL OR a.scopeType = :scope) ORDER BY a.plannedOn DESC, a.auditNo DESC
        """)
    List<TrainProvAudit> search(TenantId tenantId, String status, String scope);
}
