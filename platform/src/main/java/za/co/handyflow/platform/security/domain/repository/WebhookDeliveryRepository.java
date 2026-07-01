// security/domain/repository/WebhookDeliveryRepository.java
package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.WebhookDelivery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    @Query("SELECT d FROM WebhookDelivery d WHERE d.subscriptionId = :subId ORDER BY d.createdAt DESC")
    Page<WebhookDelivery> findBySubscription(UUID subId, Pageable pageable);

    /** Deliveries due for retry — used by WebhookRetryScheduler every 5 minutes. */
    @Query("""
        SELECT d FROM WebhookDelivery d
        WHERE d.deliveredAt IS NULL
        AND d.nextRetryAt IS NOT NULL
        AND d.nextRetryAt <= :now
        AND d.attemptNumber < 5
        """)
    List<WebhookDelivery> findDueForRetry(Instant now);
}