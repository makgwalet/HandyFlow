package za.co.handyflow.platform.crm.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.CrmWebhookDelivery;
import za.co.handyflow.platform.shared.TenantId;

public interface CrmWebhookDeliveryRepository extends JpaRepository<CrmWebhookDelivery, UUID> {
    @Query("SELECT d FROM CrmWebhookDelivery d WHERE d.tenantId = :tenantId ORDER BY d.sentAt DESC, d.id")
    List<CrmWebhookDelivery> findRecent(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM CrmWebhookDelivery d WHERE d.webhookId = :webhookId AND d.sentAt < :cutoff")
    int deleteOlderThan(@Param("webhookId") UUID webhookId, @Param("cutoff") Instant cutoff);
}
