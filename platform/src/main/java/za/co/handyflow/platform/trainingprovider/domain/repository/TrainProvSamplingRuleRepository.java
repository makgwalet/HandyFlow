package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSamplingRule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvSamplingRuleRepository extends JpaRepository<TrainProvSamplingRule, UUID> {

    @Query("SELECT r FROM TrainProvSamplingRule r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<TrainProvSamplingRule> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT r FROM TrainProvSamplingRule r WHERE r.tenantId = :#{#tenantId.value} ORDER BY r.status, r.ruleNo")
    List<TrainProvSamplingRule> findAllForTenant(TenantId tenantId);

    @Query("""
        SELECT r FROM TrainProvSamplingRule r WHERE r.tenantId = :#{#tenantId.value} AND r.status = 'ACTIVE' AND r.scopeType = :scopeType
        AND ((:programmeId IS NULL AND r.programmeId IS NULL) OR r.programmeId = :programmeId)
        AND ((:cohortId IS NULL AND r.cohortId IS NULL) OR r.cohortId = :cohortId)
        AND ((:assessmentType IS NULL AND r.assessmentType IS NULL) OR r.assessmentType = :assessmentType)
        """)
    Optional<TrainProvSamplingRule> findLiveForScope(TenantId tenantId, String scopeType, UUID programmeId, UUID cohortId, String assessmentType);
}
