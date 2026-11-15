package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** The short database steps around invoicing a stay, each in its own transaction so a failure in Invoicing cannot roll back the record of it. */
@Component
@RequiredArgsConstructor
public class FuelStayInvoicingTx {

    static final Duration STALE_CLAIM = Duration.ofMinutes(15);

    private final FuelTruckStopStayRepository stays;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(TenantId tenantId, UUID id) {
        Instant now = Instant.now();
        if (stays.claimForInvoicing(tenantId, id, now, now.minus(STALE_CLAIM)) != 1) {
            throw new IllegalStateException("This stay is already invoiced, being invoiced, or has an invoice that still needs finishing. Refresh the list.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void link(TenantId tenantId, UUID id, UUID invoiceId, String invoiceNumber) {
        stays.markInvoiced(tenantId, id, invoiceId, invoiceNumber);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(TenantId tenantId, UUID id, String message) {
        stays.markFailed(tenantId, id, trim(message));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void linkFailed(TenantId tenantId, UUID id, UUID invoiceId, String invoiceNumber, String message) {
        stays.markLinkFailed(tenantId, id, invoiceId, invoiceNumber, trim(message));
    }

    private static String trim(String m) { return m == null ? "Unknown error" : (m.length() > 500 ? m.substring(0, 500) : m); }
}
