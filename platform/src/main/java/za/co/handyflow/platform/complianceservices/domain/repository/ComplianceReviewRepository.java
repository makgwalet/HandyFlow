package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceReviewRepository extends JpaRepository<ComplianceReview, UUID> {
    @Query("SELECT r FROM ComplianceReview r WHERE r.tenantId.value = :#{#tenantId.value} AND r.id = :id")
    Optional<ComplianceReview> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM ComplianceReview r WHERE r.tenantId.value = :#{#tenantId.value} ORDER BY r.startedAt DESC")
    List<ComplianceReview> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT r FROM ComplianceReview r WHERE r.tenantId.value = :#{#tenantId.value} AND r.clientId = :clientId ORDER BY r.startedAt DESC")
    List<ComplianceReview> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT r FROM ComplianceReview r WHERE r.tenantId.value = :#{#tenantId.value} AND r.subjectType = :type AND r.subjectId = :subjectId ORDER BY r.round DESC")
    List<ComplianceReview> findForSubject(@Param("tenantId") TenantId tenantId, @Param("type") String type, @Param("subjectId") UUID subjectId);

    @Query("SELECT r FROM ComplianceReview r WHERE r.tenantId.value = :#{#tenantId.value} AND (r.status = 'IN_PROGRESS' OR r.status = 'REWORK_REQUIRED')")
    List<ComplianceReview> findNeedingAttention(@Param("tenantId") TenantId tenantId);

    @Query("SELECT r.previousReviewId FROM ComplianceReview r WHERE r.tenantId.value = :#{#tenantId.value} AND r.previousReviewId IS NOT NULL")
    List<UUID> findPreviousIds(@Param("tenantId") TenantId tenantId);
}
