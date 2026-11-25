package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceReviewTemplateItemRepository extends JpaRepository<ComplianceReviewTemplateItem, UUID> {
    @Query("SELECT i FROM ComplianceReviewTemplateItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.templateId = :templateId ORDER BY i.position")
    List<ComplianceReviewTemplateItem> findForTemplate(@Param("tenantId") TenantId tenantId, @Param("templateId") UUID templateId);

    @Query("SELECT i FROM ComplianceReviewTemplateItem i WHERE i.tenantId.value = :#{#tenantId.value}")
    List<ComplianceReviewTemplateItem> findAllForTenant(@Param("tenantId") TenantId tenantId);
}
