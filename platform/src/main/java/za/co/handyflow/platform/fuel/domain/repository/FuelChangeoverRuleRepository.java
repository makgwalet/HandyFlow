package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelChangeoverRule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelChangeoverRuleRepository extends JpaRepository<FuelChangeoverRule, UUID> {

    @Query("SELECT r FROM FuelChangeoverRule r WHERE r.tenantId = :tenantId ORDER BY r.fromFuel, r.toFuel")
    List<FuelChangeoverRule> findAllForTenant(TenantId tenantId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FuelChangeoverRule r WHERE r.tenantId = :tenantId")
    void deleteAllForTenant(TenantId tenantId);
}
