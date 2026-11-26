package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAuditTemplateVersionRepository extends JpaRepository<TrainProvAuditTemplateVersion, UUID> {
    @Query("SELECT v FROM TrainProvAuditTemplateVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.id = :id")
    Optional<TrainProvAuditTemplateVersion> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT v FROM TrainProvAuditTemplateVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.templateId = :templateId ORDER BY v.versionNo DESC")
    List<TrainProvAuditTemplateVersion> forTemplate(TenantId tenantId, UUID templateId);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM TrainProvAuditTemplateVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.templateId = :templateId")
    int latestNo(TenantId tenantId, UUID templateId);
}
