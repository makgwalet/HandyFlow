package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceSmsLog;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ComplianceSmsLogRepository extends JpaRepository<ComplianceSmsLog, UUID> {
    @Query("SELECT l FROM ComplianceSmsLog l WHERE l.tenantId.value = :#{#tenantId.value} AND l.deadlineId = :deadlineId AND l.consentId = :consentId AND l.kind = :kind AND l.outcome = 'HANDED_OVER'")
    List<ComplianceSmsLog> findExisting(@Param("tenantId") TenantId tenantId, @Param("deadlineId") UUID deadlineId, @Param("consentId") UUID consentId, @Param("kind") String kind);

    @Query("SELECT l FROM ComplianceSmsLog l WHERE l.tenantId.value = :#{#tenantId.value} ORDER BY l.runDate DESC, l.createdAt DESC")
    List<ComplianceSmsLog> findRecent(@Param("tenantId") TenantId tenantId, Pageable page);

    @Query("SELECT l FROM ComplianceSmsLog l WHERE l.tenantId.value = :#{#tenantId.value} AND l.clientId = :clientId ORDER BY l.runDate DESC, l.createdAt DESC")
    List<ComplianceSmsLog> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable page);
}
