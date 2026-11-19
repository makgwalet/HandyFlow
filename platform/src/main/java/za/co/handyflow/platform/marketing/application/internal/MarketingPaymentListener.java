package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.invoicing.ReceiptRecordedEvent;
import za.co.handyflow.platform.invoicing.RefundIssuedEvent;
import za.co.handyflow.platform.marketing.MarketingAttributionFacade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Feeds invoicing payments and refunds into marketing attribution, so campaign revenue and the PURCHASED journey
 * condition work without anyone entering conversions by hand. Runs after the receipt is saved, in its own transaction;
 * the attribution facade never throws, and a missing customer email is simply "nothing to attribute".
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MarketingPaymentListener {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final CrmFacade crm;
    private final MarketingAttributionFacade attribution;

    @ApplicationModuleListener
    void on(ReceiptRecordedEvent e) {
        String email = emailOf(e.tenantId(), e.customerId());
        if (email == null) return;
        boolean recorded = attribution.recordPayment(e.tenantId(), email, cents(e.amount()), "ZAR",
                "receipt:" + e.receiptNumber(), startOfDay(e.receivedOn()), "Receipt " + e.receiptNumber());
        log.debug("Payment attribution for receipt {}: {}", e.receiptNumber(), recorded);
    }

    @ApplicationModuleListener
    void on(RefundIssuedEvent e) {
        boolean recorded = attribution.recordRefund(e.tenantId(), "receipt:" + e.receiptNumber(),
                "refund:" + e.refundNumber(), cents(e.amount()), startOfDay(e.refundedOn()));
        log.debug("Refund attribution for refund {}: {}", e.refundNumber(), recorded);
    }

    private String emailOf(za.co.handyflow.platform.shared.TenantId tenant, java.util.UUID customerId) {
        if (customerId == null) return null;
        return crm.findCustomerById(tenant, customerId)
                .map(c -> c.email()).filter(m -> m != null && !m.isBlank()).orElse(null);
    }

    static long cents(BigDecimal amount) {
        return amount == null ? 0L : amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static java.time.Instant startOfDay(LocalDate d) {
        return (d == null ? LocalDate.now(SAST) : d).atStartOfDay(SAST).toInstant();
    }
}
