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

public interface ComplianceAssessmentWeightsRepository extends JpaRepository<ComplianceAssessmentWeights, UUID> {
    @Query("SELECT w FROM ComplianceAssessmentWeights w WHERE w.tenantId.value = :#{#tenantId.value}")
    Optional<ComplianceAssessmentWeights> findForTenant(@Param("tenantId") TenantId tenantId);
}
