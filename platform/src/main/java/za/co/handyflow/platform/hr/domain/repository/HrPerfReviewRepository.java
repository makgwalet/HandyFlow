package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrPerfReviewRepository extends JpaRepository<HrPerfReview, UUID> {

    @Query("SELECT r FROM HrPerfReview r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<HrPerfReview> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT r FROM HrPerfReview r WHERE r.tenantId = :#{#tenantId.value} AND r.cycleId = :cycleId")
    List<HrPerfReview> forCycle(TenantId tenantId, UUID cycleId);

    @Query("SELECT r FROM HrPerfReview r WHERE r.tenantId = :#{#tenantId.value} AND r.employeeId = :employeeId ORDER BY r.createdAt DESC")
    List<HrPerfReview> forEmployee(TenantId tenantId, UUID employeeId);

    @Query("SELECT r FROM HrPerfReview r WHERE r.tenantId = :#{#tenantId.value} AND r.reviewerId = :reviewerId ORDER BY r.createdAt DESC")
    List<HrPerfReview> forReviewer(TenantId tenantId, UUID reviewerId);

    @Query("SELECT r FROM HrPerfReview r WHERE r.cycleId IN :cycleIds")
    List<HrPerfReview> forCycles(java.util.Collection<UUID> cycleIds);
}
