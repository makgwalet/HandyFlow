package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceAssessmentItemRepository extends JpaRepository<ComplianceAssessmentItem, UUID> {
    @Query("SELECT i FROM ComplianceAssessmentItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.assessmentId = :assessmentId ORDER BY i.code")
    List<ComplianceAssessmentItem> findForAssessment(@Param("tenantId") TenantId tenantId, @Param("assessmentId") UUID assessmentId);

    @Query("SELECT i FROM ComplianceAssessmentItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.assessmentId = :assessmentId AND i.obligationId = :obligationId")
    Optional<ComplianceAssessmentItem> findOne(@Param("tenantId") TenantId tenantId, @Param("assessmentId") UUID assessmentId, @Param("obligationId") UUID obligationId);
}
