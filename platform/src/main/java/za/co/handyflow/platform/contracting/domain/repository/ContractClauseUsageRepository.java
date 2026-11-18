package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.contracting.domain.model.ContractClauseUsage;

import java.util.List;
import java.util.UUID;

public interface ContractClauseUsageRepository extends JpaRepository<ContractClauseUsage, UUID> {
    List<ContractClauseUsage> findByTenantIdAndContractIdOrderByInsertedAtDesc(UUID tenantId, UUID contractId);
}
