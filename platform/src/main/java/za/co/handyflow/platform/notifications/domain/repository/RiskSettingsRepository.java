package za.co.handyflow.platform.notifications.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface RiskSettingsRepository extends JpaRepository<RiskSettings, UUID> {

    @Query("SELECT s FROM RiskSettings s WHERE s.tenantId = :tenantId")
    Optional<RiskSettings> findForTenant(TenantId tenantId);
}
