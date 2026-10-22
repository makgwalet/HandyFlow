package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrPerfFeedbackRepository extends JpaRepository<HrPerfFeedback, UUID> {

    @Query("SELECT f FROM HrPerfFeedback f WHERE f.tenantId = :#{#tenantId.value} AND f.id = :id")
    Optional<HrPerfFeedback> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT f FROM HrPerfFeedback f WHERE f.reviewId = :reviewId ORDER BY f.requestedAt")
    List<HrPerfFeedback> forReview(UUID reviewId);

    @Query("SELECT f FROM HrPerfFeedback f WHERE f.tenantId = :#{#tenantId.value} AND f.providerId = :providerId ORDER BY f.requestedAt DESC")
    List<HrPerfFeedback> forProvider(TenantId tenantId, UUID providerId);
}
