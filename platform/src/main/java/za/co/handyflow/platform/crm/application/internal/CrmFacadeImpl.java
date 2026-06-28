package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * CrmFacadeImpl — the anti-corruption boundary between CRM and other modules.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WHY does this facade exist?
 *
 * In a modular monolith (or microservices), modules should NOT import
 * each other's internal classes.  The Bookings module should never
 * import za.co.handyflow.platform.crm.domain.model.Customer directly.
 *
 * Instead, the CRM module publishes a facade interface (CrmFacade) in
 * its public API package.  Other modules depend only on that interface.
 * This means:
 *   1. The CRM module can refactor its internals freely
 *   2. Other modules are never broken by CRM internal changes
 *   3. You can swap CRM for a different implementation without
 *      touching BookingService
 *
 * WHAT'S NEW:
 * - notifyBookingLinked() / notifyInvoiceLinked(): cross-module activity recording
 *   When a booking is created for a customer, Bookings module calls this
 *   facade, and CRM records it in the customer's activity timeline.
 *   This is how the "Customer 360 view" gets booking/invoice history
 *   without CRM directly querying the Bookings module.
 * ═══════════════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
class CrmFacadeImpl implements CrmFacade {

    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSummary> findCustomerById(TenantId tenantId, UUID customerId) {
        return customerRepository.findActiveById(tenantId, customerId)
                .map(c -> new CustomerSummary(
                        c.getId(),
                        c.getName(),
                        c.getEmail(),
                        c.getPhone(),
                        c.getTaxNumber(),
                        c.getCustomerType(),
                        c.getStatus()
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean customerExists(TenantId tenantId, UUID customerId) {
        // Delegate to a count-based query — avoids loading the full entity just to check existence.
        return customerRepository.existsActiveById(tenantId, customerId);
    }

    /**
     * NEW: Record on the customer's activity timeline that a booking was linked.
     *
     * Called by BookingService when a booking is created for this customer.
     * This is the backbone of the "Customer 360 view" — all booking events
     * appear on the customer's timeline without CRM importing any booking code.
     *
     * WHY Transactional here (not readOnly)?
     * We're writing a CustomerActivity record.
     *
     * @param triggeredBy the userId who created the booking (can be null for system/import)
     */
    @Override
    @Transactional
    public void notifyBookingLinked(TenantId tenantId, UUID customerId,
                                    UUID bookingId, UUID triggeredBy) {
        customerRepository.findActiveById(tenantId, customerId)
                .ifPresent(customer -> {
                    customer.recordBookingLinked(bookingId, triggeredBy);
                    customerRepository.save(customer);
                });
        // If customer not found: silent no-op.  Don't fail the booking creation
        // because of a missing CRM record — the booking is the primary transaction.
    }

    @Override
    @Transactional
    public void notifyInvoiceLinked(TenantId tenantId, UUID customerId,
                                    UUID invoiceId, UUID triggeredBy) {
        customerRepository.findActiveById(tenantId, customerId)
                .ifPresent(customer -> {
                    customer.recordInvoiceLinked(invoiceId, triggeredBy);
                    customerRepository.save(customer);
                });
    }
}