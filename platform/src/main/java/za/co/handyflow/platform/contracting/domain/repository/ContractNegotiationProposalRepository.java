package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiationProposal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractNegotiationProposalRepository extends JpaRepository<ContractNegotiationProposal, UUID> {
    Optional<ContractNegotiationProposal> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ContractNegotiationProposal> findByTenantIdAndContractIdOrderByCreatedAtAsc(UUID tenantId, UUID contractId);

    List<ContractNegotiationProposal> findByTenantIdAndContractIdAndPartyIdOrderByCreatedAtAsc(UUID tenantId, UUID contractId, UUID partyId);

    List<ContractNegotiationProposal> findByTenantIdAndContractIdAndStatus(UUID tenantId, UUID contractId, String status);
}
