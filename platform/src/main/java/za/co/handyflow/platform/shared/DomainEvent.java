package za.co.handyflow.platform.shared;

import java.time.Instant;

/**
 * WHY A MARKER INTERFACE FOR DOMAIN EVENTS?
 *
 * Spring Modulith uses this to track published events.
 * Every cross-module event must implement this — it signals to
 * the framework: "this is an intentional integration point between modules."
 *
 * Spring Modulith will:
 * 1. Log these events
 * 2. Store them in event_publication table (for retry on failure)
 * 3. Use them in architecture verification tests
 */
public interface DomainEvent {
    Instant occurredOn();
    TenantId tenantId();
}
