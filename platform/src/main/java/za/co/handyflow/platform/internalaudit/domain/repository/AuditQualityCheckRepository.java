package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditQualityCheck;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditQualityCheckRepository extends JpaRepository<AuditQualityCheck, UUID> {
    List<AuditQualityCheck> findByTenantIdAndReviewId(UUID tenantId, UUID reviewId);
    Optional<AuditQualityCheck> findByTenantIdAndReviewIdAndItemCode(UUID tenantId, UUID reviewId, String itemCode);
    List<AuditQualityCheck> findByTenantIdAndReviewIdIn(UUID tenantId, Collection<UUID> reviewIds);
}
