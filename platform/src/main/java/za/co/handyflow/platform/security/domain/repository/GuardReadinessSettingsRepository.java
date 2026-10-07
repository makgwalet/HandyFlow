package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardReadinessSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface GuardReadinessSettingsRepository extends JpaRepository<GuardReadinessSettings, UUID> {

    @Query("SELECT s FROM GuardReadinessSettings s WHERE s.tenantId = :tenantId")
    Optional<GuardReadinessSettings> findForTenant(TenantId tenantId);
}
