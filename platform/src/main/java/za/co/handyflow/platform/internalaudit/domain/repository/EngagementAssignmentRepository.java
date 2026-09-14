package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.EngagementAssignment;

import java.util.List;
import java.util.UUID;

public interface EngagementAssignmentRepository extends JpaRepository<EngagementAssignment, UUID> {

    @Query("SELECT a FROM EngagementAssignment a WHERE a.tenantId = :tenantId AND a.engagementId = :engagementId")
    List<EngagementAssignment> findByEngagement(@Param("tenantId") UUID tenantId, @Param("engagementId") UUID engagementId);
}
