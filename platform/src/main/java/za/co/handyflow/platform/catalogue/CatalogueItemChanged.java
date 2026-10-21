package za.co.handyflow.platform.catalogue;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Something about a catalogue item changed: it was created, edited, repriced, activated, archived, deleted, got a picture,
 * or changed its package parts. {@code action} names what happened (the history action, e.g. PRICE_CHANGED). The event
 * carries no prices; a listener that needs the item reads it through {@link CatalogueFacade}. It is published when the
 * history line is saved, and a listener that runs after commit sees it only if the change was committed.
 */
public record CatalogueItemChanged(TenantId tenantId, UUID itemId, String action, Instant occurredOn) implements DomainEvent {}
