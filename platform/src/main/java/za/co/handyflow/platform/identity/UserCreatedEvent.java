package za.co.handyflow.platform.identity;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedEvent(
        TenantId tenantId,
        UUID userId,
        String email,
        Instant occurredOn
) implements DomainEvent {

    public static UserCreatedEvent of(TenantId tenantId, UUID userId, String email) {
        return new UserCreatedEvent(tenantId, userId, email, Instant.now());
    }
}