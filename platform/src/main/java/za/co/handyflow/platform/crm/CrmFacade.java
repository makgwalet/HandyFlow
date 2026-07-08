package za.co.handyflow.platform.crm;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
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
     * NEW: lists active customers with a usable email address for this
     * tenant — for modules that need to enumerate the whole customer base,
     * not just look up one at a time. findCustomerById/customerExists above
     * only support single lookups; this was the missing piece behind
     * Marketing's contact sync reading directly from the `customers` table
     * via raw SQL instead of through this facade — there was previously no
     * facade method that let it do otherwise.
     */
    List<CustomerSummary> findActiveCustomersWithEmail(TenantId tenantId);

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

    /**
     * Notify CRM that a customer's marketing email consent changed (opted in
     * or out, via the Marketing module). Recorded on the customer's activity
     * timeline — same cross-module Customer 360 mechanism
     * notifyBookingLinked/notifyInvoiceLinked already provide, just for
     * consent changes instead of bookings/invoices. This is a lightweight
     * timeline entry for visibility — see the two methods below for the
     * actual POPIA-compliance consent record.
     *
     * @param triggeredBy the userId who acted, if any — null for a
     *                     recipient-initiated unsubscribe (there's no
     *                     HandyFlow user to attribute it to), matching the
     *                     same "can be null for system/import" convention
     *                     already used by triggeredBy on the two methods
     *                     above.
     */
    void notifyMarketingConsentChanged(TenantId tenantId, UUID customerId,
                                       boolean optedIn, UUID triggeredBy);

    /**
     * NEW: records a formal POPIA Section 11 consent grant specifically for
     * marketing purposes. Always creates a dedicated consent record whose
     * purposes are exactly {"MARKETING"} — deliberately never appended to
     * or bundled with any broader existing consent record. CustomerConsent
     * has no way to add/remove a single purpose from a multi-purpose
     * record, and withdraw() revokes the whole record — bundling marketing
     * into a broader consent would make it impossible to later withdraw
     * marketing consent without also revoking whatever else that record
     * covers (e.g. consent to process the customer's booking/service data).
     *
     * @param source   Marketing's own free-text opt-in source (e.g.
     *                 "IMPORT", "FORM", "MANUAL") — mapped to
     *                 CustomerConsent's constrained ConsentSource enum on
     *                 the CRM side; the original string is preserved in
     *                 the consent's evidence field regardless of how it
     *                 was mapped.
     */
    void recordMarketingConsentGranted(TenantId tenantId, UUID customerId, String source);

    /**
     * NEW: withdraws the customer's active marketing-only consent record,
     * if one exists. Only ever touches a record whose purposes are exactly
     * {"MARKETING"} — never a broader record, for the same reason
     * described on recordMarketingConsentGranted above. A no-op if no such
     * record exists (e.g. the customer was never granted marketing consent
     * through this mechanism in the first place).
     */
    void withdrawMarketingConsent(TenantId tenantId, UUID customerId, String reason);
}