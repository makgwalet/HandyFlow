package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractPartyRepository extends JpaRepository<ContractParty, UUID> {

    @Query("SELECT p FROM ContractParty p WHERE p.contractId = :contractId ORDER BY p.signingOrder")
    List<ContractParty> findByContract(UUID contractId);

    @Query("SELECT p FROM ContractParty p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<ContractParty> findByTenantAndId(TenantId tenantId, UUID id);

    /** Same, but locks the party row so two simultaneous link reissues cannot both succeed. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ContractParty p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<ContractParty> findByTenantAndIdForUpdate(TenantId tenantId, UUID id);

    /** Parties for many contracts in one query (list screens). */
    @Query("SELECT p FROM ContractParty p WHERE p.contractId IN :contractIds ORDER BY p.contractId, p.signingOrder")
    List<ContractParty> findByContractIds(java.util.Collection<UUID> contractIds);
}
