package za.co.handyflow.platform.approvals.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.approvals.domain.model.ApprovalRule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, UUID> {

    /** Tenant's own active rules for a module+entityType, priority ascending — checked first. */
    @Query("""
            SELECT r FROM ApprovalRule r
            WHERE r.tenantId = :tenantId AND r.module = :module AND r.entityType = :entityType
              AND r.active = true
            ORDER BY r.priority ASC
            """)
    List<ApprovalRule> findActiveTenantRules(@Param("tenantId") UUID tenantId,
                                             @Param("module") String module,
                                             @Param("entityType") String entityType);

    /** Platform-default (tenantId IS NULL) active rules — the fallback if no tenant rule matches. */
    @Query("""
            SELECT r FROM ApprovalRule r
            WHERE r.tenantId IS NULL AND r.module = :module AND r.entityType = :entityType
              AND r.active = true
            ORDER BY r.priority ASC
            """)
    List<ApprovalRule> findActiveGlobalRules(@Param("module") String module, @Param("entityType") String entityType);

    @Query("SELECT r FROM ApprovalRule r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<ApprovalRule> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
            SELECT r FROM ApprovalRule r
            WHERE r.tenantId = :tenantId
              AND (:module IS NULL OR r.module = :module)
              AND (:entityType IS NULL OR r.entityType = :entityType)
            ORDER BY r.module, r.entityType, r.priority
            """)
    List<ApprovalRule> findByTenant(@Param("tenantId") UUID tenantId,
                                    @Param("module") String module,
                                    @Param("entityType") String entityType);
}