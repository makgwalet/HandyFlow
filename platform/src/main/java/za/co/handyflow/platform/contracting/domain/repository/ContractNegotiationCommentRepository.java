package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiationComment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractNegotiationCommentRepository extends JpaRepository<ContractNegotiationComment, UUID> {
    Optional<ContractNegotiationComment> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ContractNegotiationComment> findByTenantIdAndContractIdOrderByCreatedAtAsc(UUID tenantId, UUID contractId);

    /** What a counterparty may see: no internal notes. */
    List<ContractNegotiationComment> findByTenantIdAndContractIdAndInternalOnlyFalseOrderByCreatedAtAsc(UUID tenantId, UUID contractId);
}
