package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceAgreementSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface ComplianceAgreementSettingsRepository extends JpaRepository<ComplianceAgreementSettings, UUID> {
    @Query("SELECT s FROM ComplianceAgreementSettings s WHERE s.tenantId.value = :#{#tenantId.value}")
    Optional<ComplianceAgreementSettings> findOne(@Param("tenantId") TenantId tenantId);
}
