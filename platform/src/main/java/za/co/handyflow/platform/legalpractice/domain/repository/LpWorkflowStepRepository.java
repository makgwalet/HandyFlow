package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpWorkflowStep;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface LpWorkflowStepRepository extends JpaRepository<LpWorkflowStep, UUID> {

    @Query("SELECT s FROM LpWorkflowStep s WHERE s.tenantId = :tenantId AND s.templateId = :templateId ORDER BY s.stepNo ASC")
    List<LpWorkflowStep> findForTemplate(TenantId tenantId, UUID templateId);

    @Modifying
    @Query("DELETE FROM LpWorkflowStep s WHERE s.tenantId = :tenantId AND s.templateId = :templateId")
    void deleteForTemplate(TenantId tenantId, UUID templateId);
}
