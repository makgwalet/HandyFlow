package za.co.handyflow.platform.crm;

import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.model.CustomerType;

import java.util.Map;
import java.util.UUID;

/**
 * CustomerSummary — the cross-module summary DTO for a customer.
 *
 * WHY add customerType and status here?
 * The Bookings module needs to know:
 *   1. Is this a LEAD or a CUSTOMER? (can leads book, or only customers?)
 *   2. Is this customer BLOCKED?     (must refuse new bookings)
 *
 * Without these fields, BookingService would need to call the CRM API
 * twice (once for existence, once for status) — or worse, it would
 * blindly create a booking for a BLOCKED customer.
 *
 * By adding them to the summary, one call gives the caller everything
 * they need to make the booking decision.
 *
 * WHY include taxNumber in the summary?
 * The Invoicing module needs the VAT number to generate a legally
 * compliant SA invoice.  Without it, the invoice is non-compliant.
 *
 * WHY include address?
 * Same reasoning as taxNumber, confirmed by a real invoice: SARS full
 * tax invoice requirements (above the R5,000 threshold) need the
 * recipient's address too, not just their VAT number. Customer.address
 * was already captured (Map<String,String>, JSONB — street/suburb/city/
 * postalCode keys, same shape InvoicePdfService's addressLine() helper
 * already expects for the tenant's own FROM-block address) but never
 * made it into this DTO, so it silently never reached the BILL TO block
 * despite the data existing. Nullable — a customer may not have an
 * address on file yet, and the PDF renders fine either way.
 */
public record CustomerSummary(
        UUID id,
        String name,
        String email,
        String phone,
        String taxNumber,
        Map<String, String> address,
        CustomerType customerType,
        CustomerStatus status
) {
    /** Convenience: is this customer allowed to make new bookings? */
    public boolean canTransact() {
        return status == CustomerStatus.ACTIVE || status == CustomerStatus.INACTIVE;
    }

    /** Is this customer blocked from transacting? */
    public boolean isBlocked() {
        return status == CustomerStatus.BLOCKED;
    }
}
