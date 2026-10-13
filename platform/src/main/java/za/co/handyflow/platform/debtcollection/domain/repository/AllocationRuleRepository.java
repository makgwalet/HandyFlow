package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationRule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllocationRuleRepository extends JpaRepository<AllocationRule, UUID> {

    @Query("SELECT r FROM AllocationRule r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<AllocationRule> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("SELECT r FROM AllocationRule r WHERE r.tenantId = :tenantId ORDER BY r.ruleOrder, r.createdAt")
    List<AllocationRule> findAllForTenant(TenantId tenantId);

    @Query("SELECT r FROM AllocationRule r WHERE r.tenantId = :tenantId AND r.active = true ORDER BY r.ruleOrder, r.createdAt")
    List<AllocationRule> findActiveForTenant(TenantId tenantId);
}
