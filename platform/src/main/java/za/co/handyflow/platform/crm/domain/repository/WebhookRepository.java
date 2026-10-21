package za.co.handyflow.platform.crm.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.Webhook;
import za.co.handyflow.platform.shared.TenantId;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {
    @Query("SELECT w FROM Webhook w WHERE w.tenantId = :tenantId AND w.deletedAt IS NULL ORDER BY w.createdAt, w.id")
    List<Webhook> findAllActive(@Param("tenantId") TenantId tenantId);

    @Query("SELECT w FROM Webhook w WHERE w.tenantId = :tenantId AND w.deletedAt IS NULL AND w.id = :id")
    Optional<Webhook> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
