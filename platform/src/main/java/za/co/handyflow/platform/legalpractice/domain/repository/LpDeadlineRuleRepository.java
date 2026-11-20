package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpDeadlineRule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpDeadlineRuleRepository extends JpaRepository<LpDeadlineRule, UUID> {

    @Query("SELECT r FROM LpDeadlineRule r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<LpDeadlineRule> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT r FROM LpDeadlineRule r WHERE r.tenantId = :tenantId ORDER BY r.court ASC, r.name ASC")
    List<LpDeadlineRule> findAllForTenant(TenantId tenantId);

    @Query("SELECT r FROM LpDeadlineRule r WHERE r.tenantId = :tenantId AND lower(r.code) = lower(:code)")
    Optional<LpDeadlineRule> findByCode(TenantId tenantId, String code);
}
