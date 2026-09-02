package za.co.handyflow.platform.insurance.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.insurance.domain.model.InsPolicy;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every {@code @Query} below binds {@code tenantId} DIRECTLY
 * ({@code WHERE p.tenantId = :tenantId}) — {@code InsPolicy.tenantId} is
 * {@code @Embedded TenantId}, so this is the confirmed-correct form (see
 * {@code LpMatterRepository}/{@code LitigationMatterRepository}, and the
 * standing bug note in
 * {@code HandyFlow-Bug-EntitlementService-TenantModule-Disconnect.md}
 * against the {@code :#{#tenantId.value}} SpEL-unwrap anti-pattern, which
 * is only correct for entities whose tenant column is a raw {@code UUID}).
 */
public interface InsPolicyRepository extends JpaRepository<InsPolicy, UUID> {

    @Query("SELECT p FROM InsPolicy p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<InsPolicy> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("""
        SELECT p FROM InsPolicy p
        WHERE p.tenantId = :tenantId
        AND (:status IS NULL OR p.status = :status)
        AND (:lineOfBusiness IS NULL OR p.lineOfBusiness = :lineOfBusiness)
        AND (:search IS NULL OR LOWER(p.policyNumber) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(p.insurerName) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(p.assetReference) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY p.expiryDate ASC
        """)
    Page<InsPolicy> search(@Param("tenantId") TenantId tenantId, @Param("status") String status,
                            @Param("lineOfBusiness") String lineOfBusiness, @Param("search") String search,
                            Pageable pageable);

    @Query("""
        SELECT p FROM InsPolicy p
        WHERE p.tenantId = :tenantId AND p.renewalOfPolicyId = :policyId
        ORDER BY p.startDate DESC
        """)
    List<InsPolicy> findRenewalChain(@Param("tenantId") TenantId tenantId, @Param("policyId") UUID policyId);

    /**
     * Cross-tenant sweep target for {@code InsNotificationScheduler} —
     * every non-terminal policy whose expiry falls on-or-before
     * {@code cutoff}, so the scheduler can mark it EXPIRED. Mirrors
     * {@code AgHealthEventRepository.findDueAcrossTenants()}'s exact
     * cross-tenant-then-group-by-tenant shape.
     */
    @Query("""
        SELECT p FROM InsPolicy p
        WHERE p.status IN ('ACTIVE', 'LAPSED')
        AND p.expiryDate <= :cutoff
        """)
    List<InsPolicy> findExpiredAcrossTenants(@Param("cutoff") LocalDate cutoff);

    /**
     * Cross-tenant sweep target for the expiring-soon reminder — active
     * policies whose expiry falls within the lookahead window and that
     * haven't already been reminded today (so a policy sitting in the
     * window for 30 days doesn't generate 30 separate notifications).
     */
    @Query("""
        SELECT p FROM InsPolicy p
        WHERE p.status IN ('ACTIVE', 'LAPSED')
        AND p.expiryDate BETWEEN :today AND :lookahead
        AND (p.expiryReminderSentAt IS NULL OR p.expiryReminderSentAt < :sinceMidnight)
        """)
    List<InsPolicy> findExpiringSoonNotYetRemindedTodayAcrossTenants(@Param("today") LocalDate today,
                                                                      @Param("lookahead") LocalDate lookahead,
                                                                      @Param("sinceMidnight") Instant sinceMidnight);
}
