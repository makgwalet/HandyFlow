package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceAgreement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceAgreementRepository extends JpaRepository<ComplianceAgreement, UUID> {

    @Query("SELECT a FROM ComplianceAgreement a WHERE a.tenantId.value = :#{#tenantId.value} AND a.clientId = :clientId ORDER BY a.createdAt DESC")
    List<ComplianceAgreement> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT a FROM ComplianceAgreement a WHERE a.tenantId.value = :#{#tenantId.value} AND a.id = :id")
    Optional<ComplianceAgreement> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM ComplianceAgreement a WHERE a.tenantId.value = :#{#tenantId.value} ORDER BY a.createdAt DESC")
    List<ComplianceAgreement> findAllForTenant(@Param("tenantId") TenantId tenantId);

    /** The agreement that holds a quote, if any. A cancelled one has let go of it. */
    @Query("SELECT a FROM ComplianceAgreement a WHERE a.tenantId.value = :#{#tenantId.value} AND a.quoteId = :quoteId AND a.status <> 'CANCELLED'")
    Optional<ComplianceAgreement> findLiveForQuote(@Param("tenantId") TenantId tenantId, @Param("quoteId") UUID quoteId);

    @Query("SELECT a.quoteId FROM ComplianceAgreement a WHERE a.tenantId.value = :#{#tenantId.value} AND a.clientId = :clientId AND a.status <> 'CANCELLED'")
    List<UUID> findLiveQuoteIds(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);
}
