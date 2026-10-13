package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.WebhookDeliveryLog;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, UUID> {

    @Query("SELECT l FROM WebhookDeliveryLog l WHERE l.tenantId = :tenantId AND l.endpointId = :endpointId ORDER BY l.createdAt DESC")
    List<WebhookDeliveryLog> findRecent(TenantId tenantId, UUID endpointId, Pageable pageable);
}
