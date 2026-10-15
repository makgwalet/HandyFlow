package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReportAmendment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditReportAmendmentRepository extends JpaRepository<AuditReportAmendment, UUID> {

    @Query("SELECT a FROM AuditReportAmendment a WHERE a.tenantId = :tenantId AND a.engagementId = :engagementId AND a.resubmittedAt IS NULL")
    Optional<AuditReportAmendment> findOpen(@Param("tenantId") UUID tenantId, @Param("engagementId") UUID engagementId);

    @Query("SELECT a FROM AuditReportAmendment a WHERE a.tenantId = :tenantId AND a.engagementId = :engagementId ORDER BY a.amendedAt DESC")
    List<AuditReportAmendment> findForEngagement(@Param("tenantId") UUID tenantId, @Param("engagementId") UUID engagementId);
}
