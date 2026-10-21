package za.co.handyflow.platform.crm.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.WebhookDelivery;
import za.co.handyflow.platform.shared.TenantId;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    @Query("SELECT d FROM WebhookDelivery d WHERE d.tenantId = :tenantId ORDER BY d.sentAt DESC, d.id")
    List<WebhookDelivery> findRecent(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM WebhookDelivery d WHERE d.webhookId = :webhookId AND d.sentAt < :cutoff")
    int deleteOlderThan(@Param("webhookId") UUID webhookId, @Param("cutoff") Instant cutoff);
}
