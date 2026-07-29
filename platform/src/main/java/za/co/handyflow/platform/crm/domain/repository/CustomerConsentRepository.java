package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.CustomerConsent;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerConsentRepository extends JpaRepository<CustomerConsent, UUID> {

    @Query("""
            SELECT c FROM CustomerConsent c
            WHERE c.tenantId   = :tenantId
              AND c.customerId = :customerId
              AND c.withdrawnAt IS NULL
            """)
    Optional<CustomerConsent> findActiveByCustomer(
            @Param("tenantId")   TenantId tenantId,
            @Param("customerId") UUID customerId
    );

    @Query("""
            SELECT c FROM CustomerConsent c
            WHERE c.tenantId   = :tenantId
              AND c.customerId = :customerId
            ORDER BY c.consentedAt DESC
            """)
    List<CustomerConsent> findAllByCustomer(
            @Param("tenantId")   TenantId tenantId,
            @Param("customerId") UUID customerId
    );

    @Query("""
            SELECT c FROM CustomerConsent c
            WHERE c.tenantId           = :tenantId
              AND c.withdrawnAt        IS NULL
              AND c.retentionExpiresAt IS NOT NULL
              AND c.retentionExpiresAt < :now
            """)
    List<CustomerConsent> findExpiredForTenant(
            @Param("tenantId") TenantId tenantId,
            @Param("now")      Instant now
    );

    /**
     * FIX: "no consent-expiring-soon reminder" gap. Deliberately excludes
     * anything already past retentionExpiresAt (that's findExpiredForTenant's
     * job — a consent shouldn't be picked up by both jobs) and anything
     * already reminded (expiryReminderSentAt IS NULL is the edge-trigger
     * guard — see CustomerConsent.markExpiryReminderSent for why).
     */
    @Query("""
            SELECT c FROM CustomerConsent c
            WHERE c.tenantId              = :tenantId
              AND c.withdrawnAt           IS NULL
              AND c.retentionExpiresAt    IS NOT NULL
              AND c.retentionExpiresAt    >= :now
              AND c.retentionExpiresAt    <= :threshold
              AND c.expiryReminderSentAt  IS NULL
            """)
    List<CustomerConsent> findExpiringSoonForTenant(
            @Param("tenantId")   TenantId tenantId,
            @Param("now")        Instant now,
            @Param("threshold")  Instant threshold
    );
}