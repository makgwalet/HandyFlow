// security/domain/repository/WebhookSubscriptionRepository.java
package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.WebhookSubscription;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    @Query("SELECT s FROM WebhookSubscription s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<WebhookSubscription> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT s FROM WebhookSubscription s WHERE s.tenantId = :tenantId ORDER BY s.createdAt DESC")
    List<WebhookSubscription> findByTenant(TenantId tenantId);

    /** All active subscriptions interested in a given event type — used by the dispatcher. */
    @Query("""
        SELECT s FROM WebhookSubscription s
        WHERE s.active = true
        AND s.tenantId = :tenantId
        AND CAST(s.eventTypes AS text) LIKE CONCAT('%', :eventType, '%')
        """)
    List<WebhookSubscription> findActiveByEventType(TenantId tenantId, String eventType);
}