package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.contracting.domain.model.ContractChangeProposal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractChangeProposalRepository extends JpaRepository<ContractChangeProposal, UUID> {
    Optional<ContractChangeProposal> findByTenantIdAndId(UUID tenantId, UUID id);
    List<ContractChangeProposal> findByTenantIdAndContractIdOrderByCreatedAtDesc(UUID tenantId, UUID contractId);
}
