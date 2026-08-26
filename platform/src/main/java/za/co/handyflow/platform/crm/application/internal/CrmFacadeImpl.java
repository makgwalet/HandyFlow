package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.crm.domain.model.CustomerConsent;
import za.co.handyflow.platform.crm.domain.repository.CustomerConsentRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
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
 * - findActiveCustomersWithEmail() / notifyMarketingConsentChanged(): the
 *   same idea, added for the Marketing module — it previously had no way
 *   to enumerate the customer base or record a consent change through this
 *   facade at all, so it was reading `customers` directly via raw SQL
 *   instead. See each method's own Javadoc below for what it needs from
 *   CustomerRepository/Customer that may not exist yet — those two files
 *   weren't available when this was written, so the exact query/entity
 *   method names below are a best-effort match to this class's existing
 *   naming convention (findActiveById/existsActiveById,
 *   recordBookingLinked/recordInvoiceLinked), not a guaranteed-correct
 *   implementation. Worth a direct check against the real files.
 * ═══════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
class CrmFacadeImpl implements CrmFacade {

    private final CustomerRepository customerRepository;
    private final CustomerConsentRepository customerConsentRepository;
    private final za.co.handyflow.platform.crm.domain.repository.CustomerCommunicationRepository communicationRepository;

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
     * NEW: backs Marketing's CRM contact sync. NEEDS
     * CustomerRepository.findAllActiveWithEmail(TenantId) to exist —
     * it doesn't yet as far as this change could confirm. Should follow
     * the exact same "active" definition findActiveById/existsActiveById
     * already use above (whatever that filters on — deleted_at, status,
     * both), with an added `email IS NOT NULL` (or equivalent) filter.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CustomerSummary> findActiveCustomersWithEmail(TenantId tenantId) {
        return customerRepository.findAllActiveWithEmail(tenantId).stream()
                .map(c -> new CustomerSummary(
                        c.getId(),
                        c.getName(),
                        c.getEmail(),
                        c.getPhone(),
                        c.getTaxNumber(),
                        c.getCustomerType(),
                        c.getStatus()
                ))
                .toList();
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

    /**
     * FIX: backlog 4.6 (Piece A). See CrmFacade's own Javadoc for the
     * full rationale. Silently skips (logs a warning, doesn't throw) if
     * the customer isn't found — a walk-in/no-customer-record quote or
     * invoice has nothing to log against, and that's a normal case, not
     * an error condition.
     */
    @Override
    @Transactional
    public void logCommunication(TenantId tenantId, UUID customerId, String type, String direction,
                                 String summary, java.time.Instant occurredAt, UUID triggeredBy) {
        if (!customerRepository.existsActiveById(tenantId, customerId)) {
            log.warn("logCommunication: customer={} not found/active for tenant={} — skipping", customerId, tenantId);
            return;
        }
        var communication = za.co.handyflow.platform.crm.domain.model.CustomerCommunication.create(
                tenantId, customerId,
                za.co.handyflow.platform.crm.domain.model.CustomerCommunication.Type.valueOf(type),
                za.co.handyflow.platform.crm.domain.model.CustomerCommunication.Direction.valueOf(direction),
                summary, occurredAt, triggeredBy);
        communicationRepository.save(communication);
    }

    /**
     * NEW: backs the Marketing module's unsubscribe flow — previously an
     * opt-out never reached CRM's activity timeline at all, since no
     * facade method existed for it. Confirmed working — Customer got a
     * matching recordMarketingConsentChanged(boolean, UUID) method once
     * Customer.java's real source became available to check against.
     */
    @Override
    @Transactional
    public void notifyMarketingConsentChanged(TenantId tenantId, UUID customerId,
                                              boolean optedIn, UUID triggeredBy) {
        customerRepository.findActiveById(tenantId, customerId)
                .ifPresent(customer -> {
                    customer.recordMarketingConsentChanged(optedIn, triggeredBy);
                    customerRepository.save(customer);
                });
        // Same silent-no-op-if-not-found behaviour as the two methods
        // above — a missing/already-deleted CRM record must never fail the
        // opt-in/opt-out action itself, which is the primary transaction.
    }

    /**
     * NEW: the formal POPIA consent record, distinct from the activity
     * timeline entry above — this is what an audit or POPIA export
     * actually needs to prove lawful processing, not a timeline note.
     * Always a fresh, marketing-only record (purposes = {"MARKETING"}) —
     * see CrmFacade's Javadoc on this method for why bundling with any
     * existing broader consent would be unsafe.
     */
    @Override
    @Transactional
    public void recordMarketingConsentGranted(TenantId tenantId, UUID customerId, String source) {
        CustomerConsent consent = CustomerConsent.create(
                tenantId, customerId,
                CustomerConsent.LawfulBasis.CONSENT,
                new String[]{"MARKETING"},
                mapConsentSource(source),
                "Marketing opt-in — original source: " + (source != null ? source : "unspecified"));
        customerConsentRepository.save(consent);
    }

    /**
     * NEW: withdraws whichever active consent record(s) for this customer
     * are marketing-only — deliberately filtered to purposes exactly
     * equal to {"MARKETING"}, never a broader record. A customer could in
     * principle have more than one such record (opted in, withdrew, opted
     * in again) — withdraw() is applied to every currently-active one
     * found, not just the first, so no stray active marketing consent is
     * left behind.
     */
    @Override
    @Transactional
    public void withdrawMarketingConsent(TenantId tenantId, UUID customerId, String reason) {
        customerConsentRepository.findAllByCustomer(tenantId, customerId).stream()
                .filter(CustomerConsent::isActive)
                .filter(CrmFacadeImpl::isMarketingOnly)
                .forEach(consent -> {
                    consent.withdraw(reason);
                    customerConsentRepository.save(consent);
                });
        // No matching record: silent no-op — the customer may never have
        // had marketing consent recorded through this mechanism (e.g. they
        // were only ever a standalone Marketing subscriber, never synced
        // from CRM), which is a legitimate, unremarkable case, not an error.
    }

    private static boolean isMarketingOnly(CustomerConsent consent) {
        String[] purposes = consent.getPurposes();
        return purposes != null && purposes.length == 1 && "MARKETING".equals(purposes[0]);
    }

    /**
     * Marketing's opt-in source is a free-text string ("IMPORT", "FORM",
     * "MANUAL", etc. — see MktContactPreference); CustomerConsent.
     * ConsentSource is a constrained enum with no generic/system value.
     * This is a deliberate best-fit mapping, not a guaranteed-precise one
     * — the original string is always preserved in the consent's evidence
     * field regardless (see recordMarketingConsentGranted above), so
     * nothing is actually lost even where the enum mapping is approximate.
     */
    private static CustomerConsent.ConsentSource mapConsentSource(String source) {
        if (source == null) return CustomerConsent.ConsentSource.WEB_FORM;
        return switch (source.toUpperCase()) {
            case "IMPORT" -> CustomerConsent.ConsentSource.IMPORT;
            case "FORM", "WEB_FORM" -> CustomerConsent.ConsentSource.WEB_FORM;
            case "EMAIL" -> CustomerConsent.ConsentSource.EMAIL;
            case "PHONE" -> CustomerConsent.ConsentSource.PHONE;
            case "IN_PERSON" -> CustomerConsent.ConsentSource.IN_PERSON;
            // MANUAL, API, CRM_SYNC, and anything unrecognized default to
            // WEB_FORM as the closest generic "someone directly entered
            // this" fallback — the real value is still in evidence.
            default -> CustomerConsent.ConsentSource.WEB_FORM;
        };
    }
}