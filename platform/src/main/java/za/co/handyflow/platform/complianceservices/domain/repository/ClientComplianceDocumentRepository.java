package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDocument;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientComplianceDocumentRepository extends JpaRepository<ClientComplianceDocument, UUID> {

    @Query("SELECT d FROM ClientComplianceDocument d WHERE d.tenantId.value = :#{#tenantId.value} AND d.id = :id")
    Optional<ClientComplianceDocument> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT d FROM ClientComplianceDocument d WHERE d.tenantId.value = :#{#tenantId.value} " +
           "AND d.clientId = :clientId ORDER BY d.expiryDate ASC NULLS LAST")
    List<ClientComplianceDocument> findByClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);
}
