package za.co.handyflow.platform.approvals.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.approvals.domain.model.ApprovalDelegation;

import java.util.List;
import java.util.UUID;

public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, UUID> {

    /**
     * Every active delegation FROM this user — filtered further
     * in-memory by coversToday(module), since date-range + nullable-scope
     * logic is clearer as entity behaviour than a JPQL expression.
     */
    @Query("""
            SELECT d FROM ApprovalDelegation d
            WHERE d.tenantId = :tenantId AND d.delegatorUserId = :delegatorUserId AND d.active = true
            """)
    List<ApprovalDelegation> findActiveByDelegator(@Param("tenantId") UUID tenantId,
                                                   @Param("delegatorUserId") UUID delegatorUserId);

    @Query("""
            SELECT d FROM ApprovalDelegation d
            WHERE d.tenantId = :tenantId
            ORDER BY d.startDate DESC
            """)
    List<ApprovalDelegation> findByTenant(@Param("tenantId") UUID tenantId);
}