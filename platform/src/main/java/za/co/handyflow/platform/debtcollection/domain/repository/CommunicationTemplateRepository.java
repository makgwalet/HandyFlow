package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationTemplateRepository extends JpaRepository<CommunicationTemplate, UUID> {

    @Query("SELECT t FROM CommunicationTemplate t WHERE t.tenantId = :tenantId AND t.code = :code AND t.latest = true")
    Optional<CommunicationTemplate> findLatest(TenantId tenantId, String code);

    @Query("SELECT COALESCE(MAX(t.templateVersion), 0) FROM CommunicationTemplate t WHERE t.tenantId = :tenantId AND t.code = :code")
    int maxVersion(TenantId tenantId, String code);

    @Query("SELECT t FROM CommunicationTemplate t WHERE t.tenantId = :tenantId AND t.latest = true ORDER BY t.code")
    List<CommunicationTemplate> findAllLatest(TenantId tenantId);

    @Query("SELECT t FROM CommunicationTemplate t WHERE t.tenantId = :tenantId AND t.code = :code ORDER BY t.templateVersion DESC")
    List<CommunicationTemplate> findVersions(TenantId tenantId, String code);
}
