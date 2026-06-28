package za.co.handyflow.platform.crm;

import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.model.CustomerType;

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
 */
public record CustomerSummary(
        UUID id,
        String name,
        String email,
        String phone,
        String taxNumber,
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
