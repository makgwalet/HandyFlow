package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelToleranceOverride;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelToleranceOverrideRepository extends JpaRepository<FuelToleranceOverride, UUID> {

    @Query("SELECT o FROM FuelToleranceOverride o WHERE o.tenantId = :tenantId ORDER BY o.scope, o.scopeKey")
    List<FuelToleranceOverride> findAllForTenant(TenantId tenantId);

    @Query("SELECT o FROM FuelToleranceOverride o WHERE o.tenantId = :tenantId AND o.scope = :scope AND o.scopeKey = :key")
    Optional<FuelToleranceOverride> findOne(TenantId tenantId, String scope, String key);
}
