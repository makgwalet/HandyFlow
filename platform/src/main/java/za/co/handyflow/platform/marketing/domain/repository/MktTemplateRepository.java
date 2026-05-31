package za.co.handyflow.platform.marketing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.marketing.domain.model.MktTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MktTemplateRepository extends JpaRepository<MktTemplate, UUID> {
    List<MktTemplate> findByTenantIdAndActiveTrueOrderByCreatedAtDesc(TenantId tenantId);
    Optional<MktTemplate> findByIdAndTenantId(UUID id, TenantId tenantId);
}
