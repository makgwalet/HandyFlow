package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelSettingsRepository extends JpaRepository<FuelSettings, UUID> {

    @Query("SELECT s FROM FuelSettings s WHERE s.tenantId = :tenantId")
    Optional<FuelSettings> findByTenant(TenantId tenantId);

    /** Every tenant that has set a delivery variance tolerance: the daily variance sweep. */
    @Query("SELECT s.tenantId FROM FuelSettings s WHERE s.varianceTolerancePct IS NOT NULL")
    List<TenantId> findTenantsWithVarianceTolerance();
}
