package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractSignature;

import java.util.List;
import java.util.UUID;

public interface ContractSignatureRepository extends JpaRepository<ContractSignature, UUID> {

    @Query("SELECT s FROM ContractSignature s WHERE s.contractId = :contractId ORDER BY s.signedAt")
    List<ContractSignature> findByContract(UUID contractId);
}