package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.fleet.domain.model.TelematicsSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface TelematicsSettingsRepository extends JpaRepository<TelematicsSettings, UUID> {

    Optional<TelematicsSettings> findByTenantId(TenantId tenantId);
}