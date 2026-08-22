package za.co.handyflow.platform.approvals.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.approvals.domain.model.ApprovalRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    @Query("""
            SELECT r FROM ApprovalRequest r
            WHERE r.tenantId = :tenantId AND r.module = :module
              AND r.entityType = :entityType AND r.entityId = :entityId
            ORDER BY r.submittedAt DESC
            """)
    List<ApprovalRequest> findByEntity(@Param("tenantId") TenantId tenantId, @Param("module") String module,
                                       @Param("entityType") String entityType, @Param("entityId") UUID entityId);

    /** The current (most recent, not-yet-superseded-by-a-resubmission) request for an entity. */
    @Query("""
            SELECT r FROM ApprovalRequest r
            WHERE r.tenantId = :tenantId AND r.module = :module
              AND r.entityType = :entityType AND r.entityId = :entityId
            ORDER BY r.submittedAt DESC LIMIT 1
            """)
    Optional<ApprovalRequest> findLatestForEntity(@Param("tenantId") TenantId tenantId, @Param("module") String module,
                                                  @Param("entityType") String entityType, @Param("entityId") UUID entityId);
}