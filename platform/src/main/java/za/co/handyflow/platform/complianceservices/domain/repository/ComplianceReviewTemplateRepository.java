package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceReviewTemplateRepository extends JpaRepository<ComplianceReviewTemplate, UUID> {
    @Query("SELECT t FROM ComplianceReviewTemplate t WHERE t.tenantId.value = :#{#tenantId.value} ORDER BY t.name")
    List<ComplianceReviewTemplate> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT t FROM ComplianceReviewTemplate t WHERE t.tenantId.value = :#{#tenantId.value} AND t.id = :id")
    Optional<ComplianceReviewTemplate> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM ComplianceReviewTemplate t WHERE t.tenantId.value = :#{#tenantId.value} AND lower(t.name) = lower(:name)")
    Optional<ComplianceReviewTemplate> findByName(@Param("tenantId") TenantId tenantId, @Param("name") String name);
}
