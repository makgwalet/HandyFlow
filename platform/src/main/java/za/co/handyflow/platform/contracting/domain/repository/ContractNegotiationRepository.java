package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiation;

import java.util.Optional;
import java.util.UUID;

public interface ContractNegotiationRepository extends JpaRepository<ContractNegotiation, UUID> {
    Optional<ContractNegotiation> findByTenantIdAndContractId(UUID tenantId, UUID contractId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM ContractNegotiation n WHERE n.tenantId = :tenantId AND n.contractId = :contractId")
    Optional<ContractNegotiation> findForUpdate(UUID tenantId, UUID contractId);
}
