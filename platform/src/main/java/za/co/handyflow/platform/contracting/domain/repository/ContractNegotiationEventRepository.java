package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiationEvent;

import java.util.List;
import java.util.UUID;

public interface ContractNegotiationEventRepository extends JpaRepository<ContractNegotiationEvent, UUID> {
    List<ContractNegotiationEvent> findTop200ByTenantIdAndContractIdOrderByCreatedAtDesc(UUID tenantId, UUID contractId);
}
