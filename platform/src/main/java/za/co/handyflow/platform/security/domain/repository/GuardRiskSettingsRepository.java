package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardRiskSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface GuardRiskSettingsRepository extends JpaRepository<GuardRiskSettings, UUID> {

    @Query("SELECT s FROM GuardRiskSettings s WHERE s.tenantId = :tenantId")
    Optional<GuardRiskSettings> findForTenant(TenantId tenantId);
}
