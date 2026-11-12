package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelAutomationRule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelAutomationRuleRepository extends JpaRepository<FuelAutomationRule, UUID> {

    @Query("SELECT r FROM FuelAutomationRule r WHERE r.tenantId = :tenantId AND r.deletedAt IS NULL ORDER BY r.createdAt")
    List<FuelAutomationRule> findAllActive(TenantId tenantId);

    @Query("SELECT r FROM FuelAutomationRule r WHERE r.tenantId = :tenantId AND r.deletedAt IS NULL AND r.enabled = true ORDER BY r.createdAt")
    List<FuelAutomationRule> findEnabled(TenantId tenantId);

    @Query("SELECT r FROM FuelAutomationRule r WHERE r.tenantId = :tenantId AND r.id = :id AND r.deletedAt IS NULL")
    Optional<FuelAutomationRule> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT r FROM FuelAutomationRule r WHERE r.tenantId = :tenantId AND r.tankId = :tankId AND r.deletedAt IS NULL")
    Optional<FuelAutomationRule> findActiveByTank(TenantId tenantId, UUID tankId);

    /** Every tenant with at least one enabled rule: the daily automation sweep. */
    @Query("SELECT DISTINCT r.tenantId FROM FuelAutomationRule r WHERE r.deletedAt IS NULL AND r.enabled = true")
    List<TenantId> findTenantsWithEnabledRules();
}
