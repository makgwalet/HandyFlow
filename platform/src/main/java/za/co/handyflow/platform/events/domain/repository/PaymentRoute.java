package za.co.handyflow.platform.events.domain.repository;

import java.util.UUID;

/**
 * Just enough to find a payment's tenant and event WITHOUT loading the payment
 * entity. A scalar projection is never served from the persistence context, and
 * that matters: the caller must lock the event row BEFORE it loads the payment,
 * so the payment it then reads reflects any state change committed while it
 * waited for the lock.
 */
public interface PaymentRoute {
    UUID getTenantId();
    UUID getEventId();
}