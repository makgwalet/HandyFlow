package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceLineItem;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.dto.CreateRetainerInvoiceRequest;
import za.co.handyflow.platform.invoicing.dto.InvoiceResponse;
import za.co.handyflow.platform.invoicing.dto.LogHoursRequest;
import za.co.handyflow.platform.invoicing.dto.RecordPaymentRequest;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import org.springframework.context.ApplicationEventPublisher;
import za.co.handyflow.platform.invoicing.InvoiceIssuedEvent;
import za.co.handyflow.platform.invoicing.InvoicePaymentRecordedEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository      invoiceRepo;
    private final InvoiceQueryService    queryService;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final InvoicePaymentTermsResolver paymentTermsResolver;
    private final TenantFacade tenantFacade;
    private final CrmFacade crmFacade;
    private final EmailService emailService;
    private final StaffNotifier staffNotifier;
    private final ReceiptPdfService receiptPdfService;
    // FIX: backlog 1.6 — publishes InvoiceIssuedEvent/InvoicePaymentRecordedEvent
    // so accounting's own listener can post to the general ledger. See
    // InvoiceIssuedEvent's own Javadoc for why this is an event, not a
    // direct AccountingFacade call.
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InvoiceResponse recordPayment(TenantId tenantId, UUID id,
                                         RecordPaymentRequest req) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id.toString()));

        Instant paidAt = req.paidDate() != null
                ? req.paidDate().atStartOfDay().toInstant(ZoneOffset.UTC)
                : Instant.now();

        // Auto-issue if still DRAFT, with a real due date now that we have
        // one — recording payment implies it was already sent.
        if (invoice.getIssuedAt() == null) {
            LocalDate dueDate = paymentTermsResolver.resolveDueDate(tenantId, LocalDate.now());
            invoice.markIssued(dueDate);
        }
        invoice.recordPayment(req.amountPaid(), paidAt);
        invoiceRepo.save(invoice);

        log.info("Payment recorded invoice={} amount={} status={}",
                invoice.getInvoiceNumber(), req.amountPaid(), invoice.getStatus());

        // NEW: payment receipt to client. No PDF yet — see EmailTemplates.paymentReceipt
        // Javadoc, a receipt PDF generator doesn't exist (flagged separately).
        // NEW: was a plain HTML email with no attachment. Now generates and
        // attaches a proper receipt PDF, same pattern as invoice/quote PDFs.
        try {
            String clientEmail = resolveClientEmail(invoice, tenantId);
            if (clientEmail != null && !clientEmail.isBlank()) {
                String clientName = resolveClientName(invoice, tenantId);
                String amountPaidStr = "R " + String.format(java.util.Locale.US, "%,.2f", req.amountPaid());
                String totalPaidStr  = "R " + String.format(java.util.Locale.US, "%,.2f", invoice.getAmountPaid());
                String totalStr      = "R " + String.format(java.util.Locale.US, "%,.2f", invoice.getTotal());

                byte[] receiptBytes = receiptPdfService.generateReceiptPdf(
                        invoice.getId(), tenantId, req.amountPaid(), paidAt,
                        req.paymentMethod(), req.reference());

                emailService.sendWithAttachment(
                        clientEmail,
                        "Payment received — Invoice " + invoice.getInvoiceNumber(),
                        EmailTemplates.paymentReceipt(
                                clientName, invoice.getInvoiceNumber(), amountPaidStr, totalPaidStr, totalStr,
                                req.paymentMethod(), req.reference()),
                        "Receipt-" + invoice.getInvoiceNumber() + ".pdf",
                        receiptBytes
                );
            }
        } catch (Exception e) {
            log.warn("Payment receipt email not sent for invoice={}: {}", id, e.getMessage());
        }

        staffNotifier.notify(tenantId, NotificationType.INVOICE_PAYMENT_RECEIVED,
                "Payment received: " + invoice.getInvoiceNumber(),
                "R " + String.format(java.util.Locale.US, "%,.2f", req.amountPaid())
                        + " recorded against invoice " + invoice.getInvoiceNumber() + ".",
                "/invoices", id.toString());

        // FIX: backlog 1.6 — was previously nothing here; a payment never
        // reached the general ledger. bankAccountId is nullable —
        // req.bankAccountId() may be null if the caller doesn't send it yet
        // (RecordPaymentRequest's new field; existing frontend flows won't
        // populate it immediately) — InvoicingAccountingEventHandler
        // handles that case explicitly (logs clearly, does not post, does
        // not guess a default account) rather than this method needing to
        // know anything about that logic itself. Placed after the
        // notification call so a notification failure above (already
        // wrapped in its own try/catch) can never prevent the ledger event
        // from firing.
        eventPublisher.publishEvent(InvoicePaymentRecordedEvent.of(
                tenantId, invoice.getId(), invoice.getInvoiceNumber(),
                req.amountPaid(), req.bankAccountId()));

        return queryService.getInvoice(tenantId, id);
    }

    @Transactional
    public InvoiceResponse issueInvoice(TenantId tenantId, UUID id) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id.toString()));
        // FIXED: now sets a real due date instead of no-arg markIssued().
        LocalDate dueDate = paymentTermsResolver.resolveDueDate(tenantId, LocalDate.now());
        invoice.markIssued(dueDate);
        invoiceRepo.save(invoice);
        log.info("Issued invoice={} dueDate={}", invoice.getInvoiceNumber(), dueDate);

        // FIX: backlog 1.6 — was previously nothing here; issuing an
        // invoice never reached the general ledger. Last statement before
        // the return, deliberately: a failure anywhere earlier in issuance
        // must never result in an event firing for an invoice that isn't
        // actually issued and persisted.
        eventPublisher.publishEvent(InvoiceIssuedEvent.of(
                tenantId, invoice.getId(), invoice.getInvoiceNumber(), invoice.getCustomerId(),
                invoice.getSubtotal(), invoice.getVatTotal(), invoice.getTotal()));

        return queryService.getInvoice(tenantId, id);
    }

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

        var li = InvoiceLineItem.create(
                invoice,
                tenantId,
                null,
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
     * FIX: "no retainer low-balance warning" gap — logHours() only ever
     * alerted on overage (hours exceeded), nothing proactively warned
     * before the client ran out. LOW_BALANCE_THRESHOLD mirrors the audit's
     * own suggested figure (~80% consumed).
     */
    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("0.80");

    @Transactional
    public InvoiceResponse logHours(TenantId tenantId, UUID id, LogHoursRequest req) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id.toString()));

        // Edge-triggered, same pattern as Fuel's FUEL_TANK_LOW alert
        // (captures wasLow before the stock change, fires only on the
        // specific call that crosses from "not low" to "low"): capture
        // consumption BEFORE logging so the warning fires exactly once, on
        // the call that actually crosses 80% — not on every subsequent
        // logHours() call while already above it, which would spam a
        // notification per hour logged.
        BigDecimal hoursBefore = invoice.getHoursConsumed();
        boolean overage = invoice.logHours(req.hours());
        invoiceRepo.save(invoice);

        if (overage) {
            log.warn("Invoice={} entered overage: consumed={} committed={}",
                    invoice.getInvoiceNumber(),
                    invoice.getHoursConsumed(),
                    invoice.getCommittedHours());

            // NEW: was server-log-only before. Staff now actually get told.
            staffNotifier.notify(tenantId, NotificationType.RETAINER_HOURS_OVERAGE,
                    "Retainer overage: " + invoice.getInvoiceNumber(),
                    "Invoice " + invoice.getInvoiceNumber() + " has consumed "
                            + invoice.getHoursConsumed() + "h of " + invoice.getCommittedHours()
                            + "h committed. Consider issuing a reconciliation invoice.",
                    "/invoices", id.toString());
        } else if (invoice.getCommittedHours() != null
                && invoice.getCommittedHours().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pctBefore = hoursBefore.divide(invoice.getCommittedHours(), 4, java.math.RoundingMode.HALF_UP);
            BigDecimal pctAfter = invoice.getHoursConsumed().divide(invoice.getCommittedHours(), 4, java.math.RoundingMode.HALF_UP);

            if (pctBefore.compareTo(LOW_BALANCE_THRESHOLD) < 0 && pctAfter.compareTo(LOW_BALANCE_THRESHOLD) >= 0) {
                log.info("Invoice={} crossed {}% retainer consumption: consumed={} committed={}",
                        invoice.getInvoiceNumber(), LOW_BALANCE_THRESHOLD.multiply(new BigDecimal("100")),
                        invoice.getHoursConsumed(), invoice.getCommittedHours());

                // NOTE: reuses RETAINER_HOURS_OVERAGE rather than a
                // dedicated notification type — NotificationType.java
                // wasn't available to safely add a new enum constant to
                // (same reasoning as everywhere else this session: a wrong
                // guess there breaks compilation). Title/message are
                // written to read distinctly from the overage alert above,
                // but if you want a genuinely separate, filterable
                // notification type, send NotificationType.java and this
                // becomes a one-line change to a dedicated constant.
                staffNotifier.notify(tenantId, NotificationType.RETAINER_HOURS_OVERAGE,
                        "Retainer approaching limit: " + invoice.getInvoiceNumber(),
                        "Invoice " + invoice.getInvoiceNumber() + " has consumed "
                                + invoice.getHoursConsumed() + "h of " + invoice.getCommittedHours()
                                + "h committed (" + pctAfter.multiply(new BigDecimal("100")).setScale(0, java.math.RoundingMode.HALF_UP)
                                + "%). Consider notifying the client before the retainer runs out.",
                        "/invoices", id.toString());
            }
        }
        log.info("Logged {}h on invoice={} totalConsumed={} note={}",
                req.hours(), invoice.getInvoiceNumber(),
                invoice.getHoursConsumed(), req.note());

        return queryService.getInvoice(tenantId, id);
    }

    /**
     * ASSUMPTION: same as QuoteService — CrmFacade customer DTO exposes
     * .email(). Confirm against the real CrmFacade if this doesn't compile.
     */
    private String resolveClientEmail(Invoice invoice, TenantId tenantId) {
        if (invoice.getCustomerId() != null) {
            return crmFacade.findCustomerById(tenantId, invoice.getCustomerId())
                    .map(c -> c.email()).orElse(null);
        }
        return invoice.getWalkinClientEmail();
    }

    private String resolveClientName(Invoice invoice, TenantId tenantId) {
        if (invoice.getCustomerId() != null) {
            return crmFacade.findCustomerById(tenantId, invoice.getCustomerId())
                    .map(c -> c.name()).orElse("Customer");
        }
        return invoice.getWalkinClientName() != null ? invoice.getWalkinClientName() : "Client";
    }
}