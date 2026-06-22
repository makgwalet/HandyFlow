// invoicing/application/internal/InvoiceService.java
package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceLineItem;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.dto.CreateRetainerInvoiceRequest;
import za.co.handyflow.platform.invoicing.dto.InvoiceResponse;
import za.co.handyflow.platform.invoicing.dto.LogHoursRequest;
import za.co.handyflow.platform.invoicing.dto.RecordPaymentRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository      invoiceRepo;
    private final InvoiceQueryService    queryService;
    private final InvoiceNumberGenerator invoiceNumberGenerator;

    // ── Existing methods ──────────────────────────────────────────────────────

    @Transactional
    public InvoiceResponse recordPayment(TenantId tenantId, UUID id,
                                         RecordPaymentRequest req) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id.toString()));

        // WHY convert LocalDate to Instant?
        // paidAt is stored as Instant for precision.
        // paidDate from client is a calendar date — convert to start-of-day UTC.
        Instant paidAt = req.paidDate() != null
                ? req.paidDate().atStartOfDay().toInstant(ZoneOffset.UTC)
                : Instant.now();

        // Auto-issue if still DRAFT — recording payment implies it was sent
        invoice.markIssued();
        invoice.recordPayment(req.amountPaid(), paidAt);
        invoiceRepo.save(invoice);

        log.info("Payment recorded invoice={} amount={} status={}",
                invoice.getInvoiceNumber(), req.amountPaid(), invoice.getStatus());
        return queryService.getInvoice(tenantId, id);
    }

    @Transactional
    public InvoiceResponse issueInvoice(TenantId tenantId, UUID id) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id.toString()));
        invoice.markIssued();
        invoiceRepo.save(invoice);
        log.info("Issued invoice={}", invoice.getInvoiceNumber());
        return queryService.getInvoice(tenantId, id);
    }

    // ── Retainer / upfront-hours ──────────────────────────────────────────────

    /**
     * Creates a retainer (upfront-hours) invoice and automatically adds one
     * line item for the committed hours block.
     *
     * WHY auto-add the line item here rather than requiring a separate call?
     * A retainer is always anchored to "N hours at R X/hr".  Forcing the caller
     * to POST a line item after creation would leave a window where the invoice
     * exists with no items, which breaks PDF generation and total calculations.
     */
    @Transactional
    public InvoiceResponse createRetainer(TenantId tenantId,
                                          CreateRetainerInvoiceRequest req) {
        boolean hasCustomer = req.customerId() != null;
        boolean hasWalkin   = req.walkinClientName() != null
                && !req.walkinClientName().isBlank();

        if (!hasCustomer && !hasWalkin) {
            throw new IllegalArgumentException(
                    "Either a customer or a walk-in client name must be provided");
        }

        String invoiceNumber = invoiceNumberGenerator.next(tenantId);

        var invoice = Invoice.createRetainer(
                tenantId,
                req.customerId(),
                invoiceNumber,
                req.title(),
                req.committedHours(),
                req.ratePerHour(),
                req.walkinClientName(),
                req.walkinClientEmail(),
                req.walkinClientPhone()
        );

        // Auto-add the committed-hours line item so totals are immediately correct
        BigDecimal lineTotal = req.committedHours()
                .multiply(req.ratePerHour())
                .setScale(2, RoundingMode.HALF_UP);

        var li = InvoiceLineItem.create(
                invoice,
                tenantId,
                null,           // no catalogue item — retainers are free-form
                req.committedHours().stripTrailingZeros().toPlainString()
                        + " hrs upfront — " + req.title(),
                "hours",
                req.committedHours(),
                req.ratePerHour(),
                req.vatRate(),
                0
        );
        invoice.addLineItem(li);
        invoice.recalculateTotals();

        invoiceRepo.save(invoice);
        log.info("Created retainer invoice={} committed={}hrs rate={}/hr",
                invoiceNumber, req.committedHours(), req.ratePerHour());
        return queryService.getInvoice(tenantId, invoice.getId());
    }

    /**
     * Log actual hours consumed against a retainer invoice.
     * Returns the updated invoice; if consumption tips into overage a warning
     * is logged — the caller can check {@code InvoiceResponse.isOverage()}.
     */
    @Transactional
    public InvoiceResponse logHours(TenantId tenantId, UUID id, LogHoursRequest req) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id.toString()));

        boolean overage = invoice.logHours(req.hours());
        invoiceRepo.save(invoice);

        if (overage) {
            log.warn("Invoice={} entered overage: consumed={} committed={}",
                    invoice.getInvoiceNumber(),
                    invoice.getHoursConsumed(),
                    invoice.getCommittedHours());
        }
        log.info("Logged {}h on invoice={} totalConsumed={} note={}",
                req.hours(), invoice.getInvoiceNumber(),
                invoice.getHoursConsumed(), req.note());

        return queryService.getInvoice(tenantId, id);
    }
}
