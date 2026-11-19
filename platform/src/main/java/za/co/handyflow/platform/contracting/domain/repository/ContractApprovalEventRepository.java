package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.contracting.domain.model.ContractApprovalEvent;

import java.util.List;
import java.util.UUID;

public interface ContractApprovalEventRepository extends JpaRepository<ContractApprovalEvent, UUID> {
    @Query("SELECT e FROM ContractApprovalEvent e WHERE e.tenantId = :tenantId AND e.approvalId = :approvalId ORDER BY e.createdAt")
    List<ContractApprovalEvent> findByApproval(@Param("tenantId") UUID tenantId, @Param("approvalId") UUID approvalId);
}
