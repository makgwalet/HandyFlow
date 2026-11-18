package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractRiskRule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRiskRuleRepository extends JpaRepository<ContractRiskRule, UUID> {

    Optional<ContractRiskRule> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT r FROM ContractRiskRule r WHERE r.tenantId = :tenantId AND (:status IS NULL OR r.status = :status) ORDER BY r.name, r.versionNo DESC")
    List<ContractRiskRule> search(UUID tenantId, String status);

    List<ContractRiskRule> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<ContractRiskRule> findFirstByTenantIdAndFamilyIdAndStatus(UUID tenantId, UUID familyId, String status);

    @Query("SELECT COALESCE(MAX(r.versionNo), 0) FROM ContractRiskRule r WHERE r.tenantId = :tenantId AND r.familyId = :familyId")
    int latestVersionNo(UUID tenantId, UUID familyId);
}
