package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceSmsSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceSmsSettingsRepository extends JpaRepository<ComplianceSmsSettings, UUID> {
    @Query("SELECT s FROM ComplianceSmsSettings s WHERE s.tenantId.value = :#{#tenantId.value}")
    Optional<ComplianceSmsSettings> findOne(@Param("tenantId") TenantId tenantId);

    @Query("SELECT s FROM ComplianceSmsSettings s WHERE s.enabled = true")
    List<ComplianceSmsSettings> findAllEnabled();
}
