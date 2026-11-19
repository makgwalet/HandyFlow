package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ApplicabilityAssessment;
import za.co.handyflow.platform.legalcompliance.domain.model.AssessmentStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicabilityAssessmentRepository extends JpaRepository<ApplicabilityAssessment, UUID> {

    @Query("SELECT a FROM ApplicabilityAssessment a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<ApplicabilityAssessment> findForTenant(TenantId tenantId, UUID id);

    /** The decision currently in force (APPROVED) or waiting (PENDING_APPROVAL) for each requirement. */
    @Query("SELECT a FROM ApplicabilityAssessment a WHERE a.tenantId = :tenantId AND a.status IN :statuses")
    List<ApplicabilityAssessment> findByStatuses(TenantId tenantId, Collection<AssessmentStatus> statuses);

    @Query("SELECT a FROM ApplicabilityAssessment a WHERE a.tenantId = :tenantId AND a.requirementId = :requirementId AND a.status = :status")
    Optional<ApplicabilityAssessment> findOne(TenantId tenantId, UUID requirementId, AssessmentStatus status);

    @Query("SELECT a FROM ApplicabilityAssessment a WHERE a.tenantId = :tenantId AND a.requirementId = :requirementId ORDER BY a.assessedAt DESC")
    List<ApplicabilityAssessment> history(TenantId tenantId, UUID requirementId);
}
