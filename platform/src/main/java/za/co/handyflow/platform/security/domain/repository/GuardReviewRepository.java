// security/domain/repository/GuardReviewRepository.java
package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardReview;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface GuardReviewRepository extends JpaRepository<GuardReview, UUID> {

    @Query("SELECT r FROM GuardReview r WHERE r.tenantId = :tenantId AND r.guardId = :guardId ORDER BY r.reviewDate DESC, r.createdAt DESC")
    List<GuardReview> findForGuard(TenantId tenantId, UUID guardId);
}
