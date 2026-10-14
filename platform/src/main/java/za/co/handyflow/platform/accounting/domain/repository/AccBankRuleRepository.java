package za.co.handyflow.platform.accounting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.accounting.domain.model.AccBankRule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccBankRuleRepository extends JpaRepository<AccBankRule, UUID> {

    @Query("SELECT r FROM AccBankRule r WHERE r.tenantId = :#{#tenantId.value} ORDER BY r.active DESC, r.priority, r.name")
    List<AccBankRule> findAllForTenant(TenantId tenantId);

    @Query("SELECT r FROM AccBankRule r WHERE r.tenantId = :#{#tenantId.value} AND r.active = true ORDER BY r.priority, r.name")
    List<AccBankRule> findActive(TenantId tenantId);

    @Query("SELECT r FROM AccBankRule r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<AccBankRule> findByTenantAndId(TenantId tenantId, UUID id);
}
