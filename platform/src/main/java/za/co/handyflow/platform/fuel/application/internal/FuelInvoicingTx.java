package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelInvoiceAttempt;
import za.co.handyflow.platform.fuel.domain.repository.FuelInvoiceAttemptRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * The three short database steps around creating an invoice, each in its own transaction so that a failure in
 * Invoicing can never roll back the record that the attempt failed (the delivery must stay visible in the queue).
 */
@Component
@RequiredArgsConstructor
public class FuelInvoicingTx {

    /** A claim older than this is treated as a crashed attempt and may be taken again. */
    static final Duration STALE_CLAIM = Duration.ofMinutes(15);

    private final FuelDeliveryRepository deliveries;
    private final FuelInvoiceAttemptRepository attempts;

    /** Records that an attempt has begun. Its own transaction, so the record survives whatever happens next. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID startAttempt(TenantId tenantId, UUID customerId, Collection<UUID> ids, UUID by) {
        return attempts.save(FuelInvoiceAttempt.start(tenantId, customerId, ids, by)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attemptCreated(TenantId tenantId, UUID attemptId, UUID invoiceId, String invoiceNumber) {
        attempts.findOwned(tenantId, attemptId).ifPresent(a -> { a.created(invoiceId, invoiceNumber); attempts.save(a); });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attemptFailed(TenantId tenantId, UUID attemptId, String message) {
        attempts.findOwned(tenantId, attemptId).ifPresent(a -> { a.failed(message); attempts.save(a); });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attemptLinkFailed(TenantId tenantId, UUID attemptId, UUID invoiceId, String invoiceNumber, String message) {
        attempts.findOwned(tenantId, attemptId).ifPresent(a -> { a.linkFailed(invoiceId, invoiceNumber, message); attempts.save(a); });
    }

    /**
     * An earlier attempt created an invoice for one of these deliveries but could not link it. Invoicing them again would bill the
     * customer twice, so refuse until a person has finished that attempt on the Needs attention list.
     */
    @Transactional(readOnly = true)
    public void requireNoUnlinkedInvoice(TenantId tenantId, Collection<UUID> ids) {
        var open = attempts.findUnlinkedCovering(tenantId, ids);
        if (!open.isEmpty()) {
            String no = open.get(0).getInvoiceNumber();
            throw new IllegalStateException("An earlier attempt already created invoice " + (no == null ? "(number unknown)" : no)
                    + " for some of these deliveries but could not link it. Finish that on the Needs attention list before invoicing them again.");
        }
    }

    /** All-or-nothing: either every delivery is claimed or none is. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(TenantId tenantId, Collection<UUID> ids) {
        Instant now = Instant.now();
        int claimed = deliveries.claimForInvoicing(tenantId, ids, now, now.minus(STALE_CLAIM));
        if (claimed != ids.size()) {
            throw new IllegalStateException("Some of these deliveries are already invoiced or being invoiced. Refresh the list.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void link(TenantId tenantId, Collection<UUID> ids, UUID invoiceId, String invoiceNumber) {
        for (FuelDelivery d : deliveries.findAllByIds(tenantId, ids)) {
            d.markInvoiced(invoiceId, invoiceNumber);
            deliveries.save(d);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(TenantId tenantId, Collection<UUID> ids, String message) {
        for (FuelDelivery d : deliveries.findAllByIds(tenantId, ids)) {
            d.markInvoiceFailed(message);
            deliveries.save(d);
        }
    }
}
