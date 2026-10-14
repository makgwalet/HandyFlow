package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.invoicing.CreditNoteIssuedEvent;
import za.co.handyflow.platform.invoicing.domain.model.CreditNote;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.repository.CreditNoteRepository;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.dto.CreateCreditNoteRequest;
import za.co.handyflow.platform.invoicing.dto.CreditNoteResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** FIX: "no credit note PDF" gap. See CreditNote for the design rationale. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditNoteService {

    private final CreditNoteRepository creditNoteRepo;
    private final InvoiceRepository invoiceRepo;
    private final CreditNoteNumberGenerator numberGenerator;
    private final CreditNotePdfService pdfService;
    private final CrmFacade crmFacade;
    private final EmailService emailService;
    // FIX (VAT sweep, module 2): CreateCreditNoteRequest.vatRate() is
    // genuinely nullable ("defaults to 15% if null", per that DTO's own
    // comment) and was falling through to CreditNote.create()'s
    // hardcoded new BigDecimal("15.00") — a genuinely reachable gap, not
    // dead defensive code, same shape as the earlier
    // PosPurchaseOrderItem finding. Resolved here instead.
    private final VatRateProvider vatRateProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final ReceiptService receiptService;

    @Transactional
    public CreditNoteResponse createCreditNote(TenantId tenantId, UUID invoiceId, CreateCreditNoteRequest req) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId.toString()));

        if (invoice.getIssuedAt() == null) {
            throw new IllegalStateException(
                    "Cannot issue a credit note against a draft invoice — issue the invoice first");
        }

        String number = numberGenerator.next(tenantId);
        BigDecimal vatRate = req.vatRate() != null ? req.vatRate() : vatRateProvider.ratePercent();
        CreditNote creditNote = CreditNote.create(
                tenantId, invoiceId, number, req.reason(), req.description(),
                req.amount(), vatRate, invoice.getCurrency());

        // Finance patch F8: credit notes on an invoice never add up to more than it was for; a credit note reduces what
        // the invoice owes, and any part beyond that goes on the customer's account as credit to use or refund.
        BigDecimal alreadyCredited = creditNoteRepo.findByInvoice(tenantId, invoiceId).stream()
                .map(CreditNote::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        String tooMuch = ReceiptRules.creditNoteProblem(invoice.getTotal(), alreadyCredited, creditNote.getTotal());
        if (tooMuch != null) throw new IllegalStateException(tooMuch);
        creditNoteRepo.save(creditNote);

        BigDecimal[] split = ReceiptRules.splitCredit(creditNote.getTotal(), invoice.getBalanceDue());
        invoice.applyCredit(split[0]);
        invoiceRepo.save(invoice);
        if (split[1].signum() > 0) {
            if (invoice.getCustomerId() != null) {
                receiptService.creditFromCreditNote(tenantId, invoice.getCustomerId(), creditNote.getId(), number, split[1], null);
            } else {
                log.warn("Credit note={} leaves R {} owed to a walk-in client on invoice={}; refund it by hand",
                        number, split[1], invoice.getInvoiceNumber());
            }
        }
        log.info("Created credit note={} invoice={} total={}",
                number, invoice.getInvoiceNumber(), creditNote.getTotal());

        // Finance patch F1: post the credit note to the ledger (reverses AR, Revenue and VAT output). The accounting
        // listener runs after this transaction commits, so a credit note that fails to save never reaches the ledger.
        eventPublisher.publishEvent(CreditNoteIssuedEvent.of(
                tenantId, creditNote.getId(), number, invoiceId, invoice.getInvoiceNumber(),
                creditNote.getSubtotal(), creditNote.getVatTotal(), creditNote.getTotal()));

        // Same pattern as QuoteService.convertToInvoice / InvoicingScheduler:
        // generate the PDF, email it, never let an SMTP failure roll back
        // the credit note that's already correctly saved above.
        try {
            String clientEmail = resolveClientEmail(invoice, tenantId);
            if (clientEmail != null && !clientEmail.isBlank()) {
                String clientName = resolveClientName(invoice, tenantId);
                byte[] pdfBytes = pdfService.generateCreditNotePdf(creditNote.getId(), tenantId);
                String amountStr = "R " + String.format(Locale.US, "%,.2f", creditNote.getTotal());

                emailService.sendWithAttachment(
                        clientEmail,
                        "Credit note " + number + " — " + invoice.getInvoiceNumber(),
                        "<p>Dear " + clientName + ",</p>"
                                + "<p>A credit note has been issued against invoice " + invoice.getInvoiceNumber() + ".</p>"
                                + "<p>Amount: " + amountStr + "</p>"
                                + "<p>Reason: " + (req.reason() != null ? req.reason() : "—") + "</p>",
                        number + ".pdf",
                        pdfBytes
                );
            } else {
                log.warn("Credit note={} has no resolvable client email (invoice={}) — not emailed",
                        number, invoiceId);
            }
        } catch (Exception e) {
            log.warn("Credit note email not sent for credit note={}: {}", creditNote.getId(), e.getMessage());
        }

        return toResponse(creditNote, invoice.getInvoiceNumber());
    }

    @Transactional(readOnly = true)
    public Page<CreditNoteResponse> getCreditNotes(TenantId tenantId, Pageable pageable) {
        // NOTE: resolves invoiceNumber per row rather than batch-loading —
        // an accepted N+1 here given credit notes are a low-volume document
        // type, not a hot list like invoices/claims. Worth batching if this
        // list ever gets large.
        return creditNoteRepo.findAllActive(tenantId, pageable)
                .map(cn -> toResponseResolvingInvoiceNumber(tenantId, cn));
    }

    @Transactional(readOnly = true)
    public List<CreditNoteResponse> getCreditNotesForInvoice(TenantId tenantId, UUID invoiceId) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId.toString()));
        return creditNoteRepo.findByInvoice(tenantId, invoiceId).stream()
                .map(cn -> toResponse(cn, invoice.getInvoiceNumber()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CreditNoteResponse getCreditNote(TenantId tenantId, UUID id) {
        CreditNote cn = creditNoteRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("CreditNote", id.toString()));
        return toResponseResolvingInvoiceNumber(tenantId, cn);
    }

    private CreditNoteResponse toResponseResolvingInvoiceNumber(TenantId tenantId, CreditNote cn) {
        String invoiceNumber = invoiceRepo.findActiveByIdWithLineItems(tenantId, cn.getInvoiceId())
                .map(Invoice::getInvoiceNumber).orElse(null);
        return toResponse(cn, invoiceNumber);
    }

    private CreditNoteResponse toResponse(CreditNote cn, String invoiceNumber) {
        return new CreditNoteResponse(cn.getId(), cn.getCreditNoteNumber(), cn.getInvoiceId(), invoiceNumber,
                cn.getReason(), cn.getDescription(), cn.getSubtotal(), cn.getVatTotal(), cn.getTotal(),
                cn.getCurrency(), cn.getIssuedAt(), cn.getCreatedAt());
    }

    /** Same resolution pattern as InvoiceService/QuoteService. */
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