package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.hr.domain.model.HrOnboardingTemplate;

import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrOnboardingTemplateRepository extends JpaRepository<HrOnboardingTemplate, UUID> {

    @Query("SELECT t FROM HrOnboardingTemplate t WHERE t.tenantId = :#{#tenantId.value} ORDER BY t.sortOrder, t.createdAt")
    List<HrOnboardingTemplate> forTenant(TenantId tenantId);

    @Query("SELECT t FROM HrOnboardingTemplate t WHERE t.tenantId = :#{#tenantId.value} AND t.active = true ORDER BY t.sortOrder, t.createdAt")
    List<HrOnboardingTemplate> activeForTenant(TenantId tenantId);

    @Query("SELECT t FROM HrOnboardingTemplate t WHERE t.tenantId = :#{#tenantId.value} AND t.id = :id")
    Optional<HrOnboardingTemplate> findByTenantAndId(TenantId tenantId, UUID id);
}
