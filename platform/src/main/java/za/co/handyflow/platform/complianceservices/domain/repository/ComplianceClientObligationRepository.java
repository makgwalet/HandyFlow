package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientObligation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceClientObligationRepository extends JpaRepository<ComplianceClientObligation, UUID> {

    @Query("SELECT a FROM ComplianceClientObligation a WHERE a.tenantId.value = :#{#tenantId.value} AND a.clientId = :clientId")
    List<ComplianceClientObligation> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT a FROM ComplianceClientObligation a WHERE a.tenantId.value = :#{#tenantId.value} AND a.clientId = :clientId AND a.obligationId = :obligationId")
    Optional<ComplianceClientObligation> findOne(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("obligationId") UUID obligationId);

    @Query("SELECT a FROM ComplianceClientObligation a WHERE a.tenantId.value = :#{#tenantId.value} AND a.approvalStatus = 'AWAITING'")
    List<ComplianceClientObligation> findAwaitingApproval(@Param("tenantId") TenantId tenantId);

    @Query("SELECT a FROM ComplianceClientObligation a WHERE a.tenantId.value = :#{#tenantId.value} AND a.obligationId IN :obligationIds")
    List<ComplianceClientObligation> findForObligations(@Param("tenantId") TenantId tenantId, @Param("obligationIds") java.util.Collection<UUID> obligationIds);
}
