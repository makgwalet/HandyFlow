package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgFinanceSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface AgFinanceSettingsRepository extends JpaRepository<AgFinanceSettings, UUID> {

    @Query("SELECT s FROM AgFinanceSettings s WHERE s.tenantId = :tenantId")
    Optional<AgFinanceSettings> findForTenant(TenantId tenantId);
}
