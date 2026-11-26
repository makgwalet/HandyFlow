package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAuditTemplateRepository extends JpaRepository<TrainProvAuditTemplate, UUID> {
    @Query("SELECT x FROM TrainProvAuditTemplate x WHERE x.tenantId = :#{#tenantId.value} AND x.id = :id")
    Optional<TrainProvAuditTemplate> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM TrainProvAuditTemplate x WHERE x.tenantId = :#{#tenantId.value} ORDER BY x.status, x.templateNo")
    List<TrainProvAuditTemplate> allForTenant(TenantId tenantId);
}
