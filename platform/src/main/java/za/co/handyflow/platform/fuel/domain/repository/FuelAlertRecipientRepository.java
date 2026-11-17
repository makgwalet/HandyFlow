package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelAlertRecipient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelAlertRecipientRepository extends JpaRepository<FuelAlertRecipient, UUID> {

    @Query("SELECT r FROM FuelAlertRecipient r WHERE r.tenantId = :tenantId ORDER BY r.topic, r.createdAt")
    List<FuelAlertRecipient> findAllForTenant(TenantId tenantId);

    @Query("SELECT r FROM FuelAlertRecipient r WHERE r.tenantId = :tenantId AND r.topic = :topic")
    List<FuelAlertRecipient> findByTopic(TenantId tenantId, String topic);
}
