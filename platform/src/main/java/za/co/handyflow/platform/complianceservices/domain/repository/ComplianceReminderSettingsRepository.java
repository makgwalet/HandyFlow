package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceReminderSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceReminderSettingsRepository extends JpaRepository<ComplianceReminderSettings, UUID> {
    @Query("SELECT s FROM ComplianceReminderSettings s WHERE s.tenantId.value = :#{#tenantId.value}")
    Optional<ComplianceReminderSettings> findOne(@Param("tenantId") TenantId tenantId);

    /** Across all tenants, for the daily run. */
    @Query("SELECT s FROM ComplianceReminderSettings s WHERE s.enabled = true")
    List<ComplianceReminderSettings> findAllEnabled();
}
