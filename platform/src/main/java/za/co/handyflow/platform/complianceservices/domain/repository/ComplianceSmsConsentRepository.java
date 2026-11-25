package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceSmsConsent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceSmsConsentRepository extends JpaRepository<ComplianceSmsConsent, UUID> {
    @Query("SELECT c FROM ComplianceSmsConsent c WHERE c.tenantId.value = :#{#tenantId.value} AND c.id = :id")
    Optional<ComplianceSmsConsent> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM ComplianceSmsConsent c WHERE c.tenantId.value = :#{#tenantId.value} AND c.clientId = :clientId ORDER BY c.givenAt DESC")
    List<ComplianceSmsConsent> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT c FROM ComplianceSmsConsent c WHERE c.tenantId.value = :#{#tenantId.value} AND c.withdrawnAt IS NULL")
    List<ComplianceSmsConsent> findActive(@Param("tenantId") TenantId tenantId);
}
