package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.SamplingPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SamplingPlanRepository extends JpaRepository<SamplingPlan, UUID> {

    @Query("SELECT p FROM SamplingPlan p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<SamplingPlan> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM SamplingPlan p WHERE p.tenantId = :tenantId AND p.engagementId = :engagementId ORDER BY p.createdAt DESC")
    List<SamplingPlan> findByEngagement(@Param("tenantId") UUID tenantId, @Param("engagementId") UUID engagementId);
}
