package za.co.handyflow.platform.identity;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TenantCreatedEvent(
        TenantId tenantId,
        String tenantName,
        String ownerEmail,
        UUID ownerId,
        List<String> moduleKeys,
        Instant occurredOn
) implements DomainEvent {

    public static TenantCreatedEvent of(TenantId tenantId, String name,
                                         String email, UUID ownerId,
                                         List<String> moduleKeys) {
        return new TenantCreatedEvent(tenantId, name, email, ownerId,
            moduleKeys != null ? moduleKeys : List.of(), Instant.now());
    }
}
