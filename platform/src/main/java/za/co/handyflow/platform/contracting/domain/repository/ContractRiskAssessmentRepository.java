package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.contracting.domain.model.ContractRiskAssessment;

import java.util.List;
import java.util.UUID;

public interface ContractRiskAssessmentRepository extends JpaRepository<ContractRiskAssessment, UUID> {
    List<ContractRiskAssessment> findTop20ByTenantIdAndContractIdOrderByAssessedAtDesc(UUID tenantId, UUID contractId);
}
