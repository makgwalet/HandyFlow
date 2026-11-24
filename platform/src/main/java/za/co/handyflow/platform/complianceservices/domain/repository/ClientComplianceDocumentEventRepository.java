package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDocumentEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ClientComplianceDocumentEventRepository extends JpaRepository<ClientComplianceDocumentEvent, UUID> {
    @Query("SELECT e FROM ClientComplianceDocumentEvent e WHERE e.tenantId.value = :#{#tenantId.value} AND e.documentId = :documentId ORDER BY e.occurredAt, e.id")
    List<ClientComplianceDocumentEvent> findForDocument(@Param("tenantId") TenantId tenantId, @Param("documentId") UUID documentId);
}
