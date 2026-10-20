package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.fleet.domain.model.DriverScoreSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface DriverScoreSettingsRepository extends JpaRepository<DriverScoreSettings, UUID> {

    Optional<DriverScoreSettings> findByTenantId(TenantId tenantId);
}