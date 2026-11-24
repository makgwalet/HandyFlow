package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceQuote;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceQuoteRepository extends JpaRepository<ComplianceQuote, UUID> {

    @Query("SELECT q FROM ComplianceQuote q WHERE q.tenantId.value = :#{#tenantId.value} AND q.clientId = :clientId ORDER BY q.createdAt DESC")
    List<ComplianceQuote> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT q FROM ComplianceQuote q WHERE q.tenantId.value = :#{#tenantId.value} AND q.id = :id")
    Optional<ComplianceQuote> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    /** Every quote with a given stored status, newest first. */
    @Query("SELECT q FROM ComplianceQuote q WHERE q.tenantId.value = :#{#tenantId.value} AND q.status IN :statuses ORDER BY q.createdAt DESC")
    List<ComplianceQuote> findByStatuses(@Param("tenantId") TenantId tenantId, @Param("statuses") List<String> statuses);

    @Query("SELECT q FROM ComplianceQuote q WHERE q.tenantId.value = :#{#tenantId.value} ORDER BY q.createdAt DESC")
    List<ComplianceQuote> findAllForTenant(@Param("tenantId") TenantId tenantId);
}
