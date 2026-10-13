package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.WebhookEndpoint;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    /** The public receiver has no tenant context: the token hash alone selects the endpoint (and through it, the tenant). */
    @Query("SELECT e FROM WebhookEndpoint e WHERE e.tokenHash = :tokenHash")
    Optional<WebhookEndpoint> findByTokenHash(String tokenHash);

    @Query("SELECT e FROM WebhookEndpoint e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<WebhookEndpoint> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("SELECT e FROM WebhookEndpoint e WHERE e.tenantId = :tenantId ORDER BY e.createdAt DESC")
    List<WebhookEndpoint> findAllForTenant(TenantId tenantId);
}
