package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceReviewItemRepository extends JpaRepository<ComplianceReviewItem, UUID> {
    @Query("SELECT i FROM ComplianceReviewItem i WHERE i.tenantId.value = :#{#tenantId.value} AND i.reviewId = :reviewId ORDER BY i.position")
    List<ComplianceReviewItem> findForReview(@Param("tenantId") TenantId tenantId, @Param("reviewId") UUID reviewId);

    @Query("SELECT i FROM ComplianceReviewItem i WHERE i.tenantId.value = :#{#tenantId.value}")
    List<ComplianceReviewItem> findAllForTenant(@Param("tenantId") TenantId tenantId);
}
