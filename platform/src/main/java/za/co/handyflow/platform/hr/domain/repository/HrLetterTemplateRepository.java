package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrLetterTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrLetterTemplateRepository extends JpaRepository<HrLetterTemplate, UUID> {

    @Query("SELECT t FROM HrLetterTemplate t WHERE t.tenantId = :#{#tenantId.value} ORDER BY t.name")
    List<HrLetterTemplate> forTenant(TenantId tenantId);

    @Query("SELECT t FROM HrLetterTemplate t WHERE t.tenantId = :#{#tenantId.value} AND t.id = :id")
    Optional<HrLetterTemplate> findByTenantAndId(TenantId tenantId, UUID id);
}
