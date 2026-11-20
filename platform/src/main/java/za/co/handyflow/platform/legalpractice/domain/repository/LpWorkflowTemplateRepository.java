package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpWorkflowTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpWorkflowTemplateRepository extends JpaRepository<LpWorkflowTemplate, UUID> {

    @Query("SELECT t FROM LpWorkflowTemplate t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<LpWorkflowTemplate> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT t FROM LpWorkflowTemplate t WHERE t.tenantId = :tenantId AND (:includeOff = true OR t.active = true) ORDER BY t.active DESC, t.name ASC")
    List<LpWorkflowTemplate> findAllForTenant(TenantId tenantId, boolean includeOff);

    /** An active workflow with this name (compared without case), other than {@code exceptId}. */
    @Query("SELECT t FROM LpWorkflowTemplate t WHERE t.tenantId = :tenantId AND t.active = true AND LOWER(t.name) = :lowerName AND t.id <> :exceptId")
    List<LpWorkflowTemplate> findActiveByName(TenantId tenantId, String lowerName, UUID exceptId);
}
