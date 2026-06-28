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
}
