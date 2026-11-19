package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.contracting.domain.model.ApprovalAction;
import za.co.handyflow.platform.contracting.domain.model.ContractApprovalPolicy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractApprovalPolicyRepository extends JpaRepository<ContractApprovalPolicy, UUID> {
    @Query("SELECT p FROM ContractApprovalPolicy p WHERE p.tenantId = :tenantId")
    List<ContractApprovalPolicy> findByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT p FROM ContractApprovalPolicy p WHERE p.tenantId = :tenantId AND p.action = :action")
    Optional<ContractApprovalPolicy> findOne(@Param("tenantId") UUID tenantId, @Param("action") ApprovalAction action);
}
