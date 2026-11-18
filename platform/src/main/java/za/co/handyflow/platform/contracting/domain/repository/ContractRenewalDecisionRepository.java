package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractRenewalDecision;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContractRenewalDecisionRepository extends JpaRepository<ContractRenewalDecision, UUID> {

    List<ContractRenewalDecision> findByTenantIdAndContractIdOrderByDecidedAtDesc(UUID tenantId, UUID contractId);

    @Query("SELECT d FROM ContractRenewalDecision d WHERE d.tenantId = :tenantId AND d.contractId IN :contractIds ORDER BY d.decidedAt DESC")
    List<ContractRenewalDecision> findByContracts(UUID tenantId, Collection<UUID> contractIds);

    @Query("SELECT d FROM ContractRenewalDecision d WHERE d.tenantId = :tenantId AND d.decidedAt >= :from AND d.decidedAt < :to")
    List<ContractRenewalDecision> findDecidedBetween(UUID tenantId, java.time.Instant from, java.time.Instant to);
}
