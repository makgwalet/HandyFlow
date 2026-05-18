// ─── PATTERN 2: EVENTS (Async, Decoupled) ───
// When: Module A wants to notify Module B that something happened
// Example: Identity notifies Billing when a new tenant is created
package za.co.handyflow.platform.identity;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

public record  TenantCreatedEvent(
        TenantId tenantId,
        String tenantName,
        String ownerEmail,
        UUID ownerId,
        Instant occurredOn
) implements DomainEvent {
    // Factory method — keeps construction clean
    public static TenantCreatedEvent of(TenantId tenantId, String name,
                                        String email, UUID ownerId) {
        return new TenantCreatedEvent(tenantId, name, email, ownerId, Instant.now());
    }
}
