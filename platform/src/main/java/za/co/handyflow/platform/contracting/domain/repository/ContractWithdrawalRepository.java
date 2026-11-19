package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.contracting.domain.model.ContractWithdrawal;

import java.util.Optional;
import java.util.UUID;

public interface ContractWithdrawalRepository extends JpaRepository<ContractWithdrawal, UUID> {
    @Query("SELECT w FROM ContractWithdrawal w WHERE w.tenantId = :tenantId AND w.contractId = :contractId")
    Optional<ContractWithdrawal> findByContract(@Param("tenantId") UUID tenantId, @Param("contractId") UUID contractId);
}
