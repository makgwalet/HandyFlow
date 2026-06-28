package za.co.handyflow.platform.crm;

import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * CrmFacade — public API of the CRM module.
 *
 * This interface lives in the MODULE ROOT package (not in application.internal).
 * WHY? Because this is what other modules import.  The internal package
 * contains implementation details that only CRM itself should see.
 *
 * RULE: Every method on this interface must return either:
 * - A primitive/String/UUID (no domain objects crossing module boundaries)
 * - A shared DTO (CustomerSummary is in the shared CRM public package)
 * - void
 *
 * WHY? If BookingService received a Customer entity, it would depend on
 * CRM's domain model.  Changes to Customer would then require changes in
 * Bookings — that's tight coupling, the thing we're trying to avoid.
 */
public interface CrmFacade {

    /** Look up a customer visible to queries. Returns empty if not found or deleted. */
    Optional<CustomerSummary> findCustomerById(TenantId tenantId, UUID customerId);

    /** Quick existence check without loading the full entity. */
    boolean customerExists(TenantId tenantId, UUID customerId);

    /**
     * Notify CRM that a booking was created for this customer.
     * CRM records this on the customer's activity timeline.
     * Called by BookingService after successfully persisting a booking.
     */
    void notifyBookingLinked(TenantId tenantId, UUID customerId, UUID bookingId, UUID triggeredBy);

    /**
     * Notify CRM that an invoice was raised for this customer.
     * CRM records this on the customer's activity timeline.
     */
    void notifyInvoiceLinked(TenantId tenantId, UUID customerId, UUID invoiceId, UUID triggeredBy);
}
