package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.earthmoving.domain.model.EarthSettings;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EarthSettingsRepository extends JpaRepository<EarthSettings, UUID> {

    /** Companies that have switched the monthly billing run on. */
    @Query("SELECT s.tenantId FROM EarthSettings s WHERE s.autoBillingEnabled = true")
    List<UUID> findTenantIdsWithAutoBilling();
}
