package za.co.handyflow.platform.notifications.application;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/**
 * Resolves who should receive a BILLING communication specifically —
 * subscription invoices, payment receipts, past-due/suspension notices.
 * Deliberately separate from TenantAdminRecipients, not an extra method
 * on it: that interface's implementation is used generically across
 * modules for non-billing notifications (confirmed: FleetService calls
 * it for vehicle alerts), so billing-specific routing needed its own
 * contract rather than overloading an existing one.
 * <p>
 * Lives in this shared package — not in identity.internal, where the
 * actual implementation does — specifically so modules like billing can
 * depend on this interface without reaching across a module boundary.
 * Spring wires the concrete implementation automatically; the consuming
 * module never needs to know or import which package implements it.
 */
public interface BillingRecipients {
    List<Recipient> resolveBillingRecipients(TenantId tenantId);
}
