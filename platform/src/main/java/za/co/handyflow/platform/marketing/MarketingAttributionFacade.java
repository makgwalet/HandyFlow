package za.co.handyflow.platform.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.marketing.application.internal.MarketingAttributionService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;

/** Public entry point for other modules (invoicing/payments) to report sales and refunds for attribution. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingAttributionFacade {

    private final MarketingAttributionService service;

    /**
     * Records a paid sale. Idempotent on externalRef, so it is safe to call twice.
     * Never throws: a marketing problem must not break a payment. Returns true if a new row was stored.
     */
    public boolean recordPayment(TenantId tenantId, String customerEmail, long amountCents, String currency,
                                 String externalRef, Instant paidAt, String description) {
        try {
            return service.recordPayment(tenantId, null, customerEmail, amountCents, currency, externalRef, paidAt, description, "INTEGRATION").recorded();
        } catch (Exception e) {
            log.warn("Marketing attribution ignored payment ref={} tenant={}: {}", externalRef, tenantId, e.getMessage());
            return false;
        }
    }

    /** Records a refund against a previously recorded payment (matched by its externalRef). Never throws. */
    public boolean recordRefund(TenantId tenantId, String originalRef, String refundRef, long amountCents, Instant refundedAt) {
        try {
            return service.recordRefund(tenantId, null, originalRef, refundRef, amountCents, refundedAt, "INTEGRATION").recorded();
        } catch (Exception e) {
            log.warn("Marketing attribution ignored refund of ref={} tenant={}: {}", originalRef, tenantId, e.getMessage());
            return false;
        }
    }
}