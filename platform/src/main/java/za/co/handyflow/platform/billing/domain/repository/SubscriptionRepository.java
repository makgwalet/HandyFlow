package za.co.handyflow.platform.billing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.billing.domain.model.Subscription;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByTenantId(TenantId tenantId);
    boolean existsByTenantId(TenantId tenantId);

    // WHY this query? Used by the pilot expiry scheduler.
    // Finds all PILOT subscriptions where the pilot period has ended.
    @Query("""
        SELECT s FROM Subscription s
        WHERE s.status = 'PILOT'
        AND s.pilotEndsAt < :now
        """)
    List<Subscription> findExpiredPilots(Instant now);

    // Used by the suspension scheduler — finds PAST_DUE subscriptions
    // older than the grace period (7 days)
    @Query("""
        SELECT s FROM Subscription s
        WHERE s.status = 'PAST_DUE'
        AND s.updatedAt < :cutoff
        """)
    List<Subscription> findPastDueOlderThan(Instant cutoff);
}
