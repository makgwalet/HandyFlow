package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceDocumentRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceDocumentRequestRepository extends JpaRepository<ComplianceDocumentRequest, UUID> {
    @Query("SELECT r FROM ComplianceDocumentRequest r WHERE r.tenantId.value = :#{#tenantId.value} AND r.id = :id")
    Optional<ComplianceDocumentRequest> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM ComplianceDocumentRequest r WHERE r.tenantId.value = :#{#tenantId.value} AND r.clientId = :clientId ORDER BY r.requestedAt DESC")
    List<ComplianceDocumentRequest> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT r FROM ComplianceDocumentRequest r WHERE r.tenantId.value = :#{#tenantId.value} AND r.status = 'OPEN'")
    List<ComplianceDocumentRequest> findOpen(@Param("tenantId") TenantId tenantId);
}
