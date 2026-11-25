package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.CompliancePortalQuery;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompliancePortalQueryRepository extends JpaRepository<CompliancePortalQuery, UUID> {
    @Query("SELECT q FROM CompliancePortalQuery q WHERE q.tenantId.value = :#{#tenantId.value} AND q.id = :id")
    Optional<CompliancePortalQuery> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT q FROM CompliancePortalQuery q WHERE q.tenantId.value = :#{#tenantId.value} AND q.clientId = :clientId ORDER BY q.lastMessageAt DESC")
    List<CompliancePortalQuery> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT q FROM CompliancePortalQuery q WHERE q.tenantId.value = :#{#tenantId.value} ORDER BY q.lastMessageAt DESC")
    List<CompliancePortalQuery> findAllForTenant(@Param("tenantId") TenantId tenantId);

    /** Waiting for the practice to answer. */
    @Query("SELECT q FROM CompliancePortalQuery q WHERE q.tenantId.value = :#{#tenantId.value} AND q.status = 'OPEN'")
    List<CompliancePortalQuery> findAwaitingAnswer(@Param("tenantId") TenantId tenantId);

    @Query("SELECT count(q) FROM CompliancePortalQuery q WHERE q.tenantId.value = :#{#tenantId.value} AND q.clientId = :clientId AND q.raisedBy = :person AND q.status = 'OPEN'")
    int countOpenFor(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("person") UUID person);
}
