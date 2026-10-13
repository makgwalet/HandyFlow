package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.FeeRule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeRuleRepository extends JpaRepository<FeeRule, UUID> {

    @Query("SELECT r FROM FeeRule r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<FeeRule> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("SELECT r FROM FeeRule r WHERE r.tenantId = :tenantId AND r.code = :code AND r.status = 'APPROVED'")
    Optional<FeeRule> findApprovedByCode(TenantId tenantId, String code);

    @Query("SELECT COALESCE(MAX(r.ruleVersion), 0) FROM FeeRule r WHERE r.tenantId = :tenantId AND r.code = :code")
    int maxVersion(TenantId tenantId, String code);

    @Query("SELECT r FROM FeeRule r WHERE r.tenantId = :tenantId ORDER BY r.code, r.ruleVersion DESC")
    List<FeeRule> findAllForTenant(TenantId tenantId);

    @Query("SELECT r FROM FeeRule r WHERE r.tenantId = :tenantId AND r.status = 'APPROVED' ORDER BY r.code")
    List<FeeRule> findAllApproved(TenantId tenantId);
}
