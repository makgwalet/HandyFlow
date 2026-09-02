package za.co.handyflow.platform.insurancebrokerage.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokPolicy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Direct {@code :tenantId} bind against the plain {@code UUID tenantId}
 * column throughout — this module's entities are NOT {@code @Embedded
 * TenantId}, so the {@code :#{#tenantId.value}} SpEL-unwrap question
 * documented in {@code HandyFlow-Bug-EntitlementService-TenantModule-Disconnect.md}
 * does not apply to this repository family at all (see package-info).
 */
public interface InsBrokPolicyRepository extends JpaRepository<InsBrokPolicy, UUID> {

    @Query("SELECT p FROM InsBrokPolicy p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<InsBrokPolicy> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM InsBrokPolicy p WHERE p.tenantId = :tenantId AND p.clientId = :clientId ORDER BY p.createdAt DESC")
    Page<InsBrokPolicy> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("""
            SELECT p FROM InsBrokPolicy p WHERE p.tenantId = :tenantId
            AND (:status IS NULL OR p.status = :status)
            AND (:lineOfBusiness IS NULL OR p.lineOfBusiness = :lineOfBusiness)
            AND (:search IS NULL OR LOWER(p.policyNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(p.assetReference) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY p.createdAt DESC
            """)
    Page<InsBrokPolicy> search(@Param("tenantId") UUID tenantId, @Param("status") String status,
                                @Param("lineOfBusiness") String lineOfBusiness, @Param("search") String search,
                                Pageable pageable);

    /** The renewal chain for a given original policy, oldest first — same shape InsPolicyRepository already uses. */
    @Query("""
            SELECT p FROM InsBrokPolicy p WHERE p.tenantId = :tenantId
            AND (p.id = :policyId OR p.renewalOfPolicyId = :policyId)
            ORDER BY p.createdAt ASC
            """)
    List<InsBrokPolicy> findRenewalChain(@Param("tenantId") UUID tenantId, @Param("policyId") UUID policyId);

    @Query("""
            SELECT p FROM InsBrokPolicy p WHERE p.status IN ('ACTIVE','LAPSED')
            AND p.expiryDate BETWEEN :today AND :warningDate
            AND (p.expiryReminderSentAt IS NULL OR p.expiryReminderSentAt < :todayStart)
            """)
    List<InsBrokPolicy> findExpiringForReminder(@Param("today") LocalDate today,
                                                 @Param("warningDate") LocalDate warningDate,
                                                 @Param("todayStart") java.time.Instant todayStart);

    @Query("SELECT p FROM InsBrokPolicy p WHERE p.status IN ('ACTIVE','LAPSED') AND p.expiryDate < :today")
    List<InsBrokPolicy> findPastExpiryNotRenewed(@Param("today") LocalDate today);
}
