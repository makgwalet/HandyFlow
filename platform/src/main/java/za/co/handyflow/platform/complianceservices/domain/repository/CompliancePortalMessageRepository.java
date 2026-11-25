package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.CompliancePortalMessage;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface CompliancePortalMessageRepository extends JpaRepository<CompliancePortalMessage, UUID> {
    @Query("SELECT m FROM CompliancePortalMessage m WHERE m.tenantId.value = :#{#tenantId.value} AND m.queryId = :queryId ORDER BY m.createdAt, m.id")
    List<CompliancePortalMessage> findForQuery(@Param("tenantId") TenantId tenantId, @Param("queryId") UUID queryId);
}
