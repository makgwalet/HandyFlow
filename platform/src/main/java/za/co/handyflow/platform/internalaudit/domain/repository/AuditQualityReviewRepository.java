package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditQualityReview;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditQualityReviewRepository extends JpaRepository<AuditQualityReview, UUID> {
    Optional<AuditQualityReview> findByTenantIdAndId(UUID tenantId, UUID id);
    List<AuditQualityReview> findByTenantIdOrderByRequestedAtDesc(UUID tenantId);
    List<AuditQualityReview> findByTenantIdAndEngagementIdOrderByRequestedAtDesc(UUID tenantId, UUID engagementId);
}
